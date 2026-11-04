import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { createAppServer } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { validateAnalysis } from '../src/ai.js';
import { normaliseUrl } from '../src/validate.js';

const json = (status, body, headers = {}) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });

/** Fake upstream: routes by host so each provider can be scripted per test. */
function fakeFetch(handlers) {
  const calls = [];
  const impl = async (url, init = {}) => {
    const u = new URL(url);
    calls.push({ host: u.hostname, url: u, init });
    const h = handlers[u.hostname];
    if (!h) throw new TypeError('fetch failed');
    return h(u, init);
  };
  impl.calls = calls;
  return impl;
}

const ALL_KEYS = {
  GOOGLE_SAFE_BROWSING_API_KEY: 'sb-test', VIRUSTOTAL_API_KEY: 'vt-test', URLHAUS_AUTH_KEY: 'uh-test', GEMINI_API_KEY: 'gm-test',
};

async function start(env, fetchImpl) {
  const config = loadConfig({ PORT: '0', HOST: '127.0.0.1', ...env });
  const server = createAppServer(config, { fetchImpl, log: () => {} });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (path, body, headers = {}) => fetch(base + path, {
    method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: typeof body === 'string' ? body : JSON.stringify(body),
  });
  return { server, base, post };
}

const cleanUpstream = () => ({
  'safebrowsing.googleapis.com': () => json(200, { cacheDuration: '300s' }),
  'www.virustotal.com': () => json(404, { error: { code: 'NotFoundError' } }),
  'urlhaus-api.abuse.ch': () => json(200, { query_status: 'no_results' }),
});

describe('health', () => {
  let ctx;
  before(async () => { ctx = await start({}, fakeFetch({})); });
  after(() => ctx.server.close());

  it('reports status and which providers are configured, without secrets', async () => {
    const res = await fetch(ctx.base + '/health');
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, 'ok');
    assert.deepEqual(body.providers, { google_safe_browsing: 'not_configured', virustotal: 'not_configured', urlhaus: 'not_configured' });
    assert.equal(body.ai, 'not_configured');
    assert.equal(res.headers.get('cache-control'), 'no-store');
  });

  it('returns 404 and 405 for unknown routes and methods', async () => {
    assert.equal((await fetch(ctx.base + '/nope')).status, 404);
    assert.equal((await fetch(ctx.base + '/health', { method: 'POST' })).status, 405);
  });
});

describe('POST /v1/url/check', () => {
  it('valid request: confirmed malicious by Safe Browsing', async () => {
    const upstream = fakeFetch({
      ...cleanUpstream(),
      'safebrowsing.googleapis.com': (u) => {
        assert.equal(u.searchParams.get('key'), 'sb-test');
        assert.deepEqual(u.searchParams.getAll('urls'), ['http://phish.example/login']);
        return json(200, { threats: [{ url: 'http://phish.example/login', threatTypes: ['SOCIAL_ENGINEERING'] }], cacheDuration: '300s' });
      },
    });
    const ctx = await start(ALL_KEYS, upstream);
    try {
      const res = await ctx.post('/v1/url/check', { url: 'http://user:pass@PHISH.example/login#frag' });
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.equal(body.verdict, 'malicious');
      assert.equal(body.checkedUrl, 'http://phish.example/login', 'credentials and fragment are stripped before lookup');
      const sb = body.providers.find((p) => p.name === 'google_safe_browsing');
      assert.deepEqual(sb, { name: 'google_safe_browsing', status: 'malicious', threats: ['SOCIAL_ENGINEERING'] });
      assert.equal(body.providers.find((p) => p.name === 'virustotal').status, 'unknown');
      // VirusTotal is only queried (GET report), never submitted to.
      assert.ok(upstream.calls.filter((c) => c.host === 'www.virustotal.com').every((c) => !c.init.method || c.init.method === 'GET'));
    } finally {
      ctx.server.close();
    }
  });

  it('VirusTotal needs two engine detections before reporting malicious', async () => {
    const vt = (malicious) => fakeFetch({
      ...cleanUpstream(),
      'www.virustotal.com': () => json(200, { data: { attributes: { last_analysis_stats: { malicious, suspicious: 0, harmless: 60 } } } }),
    });
    for (const [n, expected] of [[1, 'suspicious'], [3, 'malicious'], [0, 'no_known_threat']]) {
      const ctx = await start(ALL_KEYS, vt(n));
      try {
        const body = await (await ctx.post('/v1/url/check', { url: 'https://example.com' })).json();
        assert.equal(body.verdict, expected, `malicious=${n}`);
      } finally {
        ctx.server.close();
      }
    }
  });

  it('invalid requests are rejected with structured errors', async () => {
    const ctx = await start(ALL_KEYS, fakeFetch(cleanUpstream()));
    try {
      for (const [body, code] of [
        [{}, 'invalid_request'],
        [{ url: 42 }, 'invalid_request'],
        [{ url: 'javascript:alert(1)' }, 'invalid_request'],
        [{ url: 'x'.repeat(3000) }, 'invalid_request'],
        [[1, 2], 'invalid_request'],
        ['{not json', 'invalid_json'],
      ]) {
        const res = await ctx.post('/v1/url/check', body);
        assert.equal(res.status, 400, JSON.stringify(body).slice(0, 40));
        assert.equal((await res.json()).error.code, code);
      }
      const wrongType = await fetch(ctx.base + '/v1/url/check', { method: 'POST', headers: { 'content-type': 'text/plain' }, body: 'url' });
      assert.equal(wrongType.status, 415);
      const huge = await ctx.post('/v1/url/check', { url: 'https://a.example', pad: 'x'.repeat(20_000) });
      assert.equal(huge.status, 413);
    } finally {
      ctx.server.close();
    }
  });

  it('missing credentials: every provider reports not_configured and nothing is called', async () => {
    const upstream = fakeFetch(cleanUpstream());
    const ctx = await start({}, upstream);
    try {
      const body = await (await ctx.post('/v1/url/check', { url: 'https://example.com' })).json();
      assert.equal(body.verdict, 'unavailable');
      assert.ok(body.providers.every((p) => p.status === 'not_configured'));
      assert.equal(upstream.calls.length, 0);
    } finally {
      ctx.server.close();
    }
  });

  it('provider unavailable: errors are isolated and never reported as safe', async () => {
    const upstream = fakeFetch({ 'urlhaus-api.abuse.ch': () => json(200, { query_status: 'no_results' }) }); // SB and VT unreachable
    const ctx = await start(ALL_KEYS, upstream);
    try {
      const body = await (await ctx.post('/v1/url/check', { url: 'https://example.com' })).json();
      assert.equal(body.providers.find((p) => p.name === 'google_safe_browsing').status, 'error');
      assert.equal(body.providers.find((p) => p.name === 'virustotal').status, 'error');
      assert.equal(body.providers.find((p) => p.name === 'urlhaus').status, 'clean');
      assert.equal(body.verdict, 'no_known_threat');

      const allDown = await start(ALL_KEYS, fakeFetch({}));
      try {
        const down = await (await allDown.post('/v1/url/check', { url: 'https://example.com' })).json();
        assert.equal(down.verdict, 'unavailable');
      } finally {
        allDown.server.close();
      }
    } finally {
      ctx.server.close();
    }
  });

  it('retries a provider once after a 5xx', async () => {
    let attempts = 0;
    const upstream = fakeFetch({
      ...cleanUpstream(),
      'safebrowsing.googleapis.com': () => (++attempts === 1 ? json(503, {}) : json(200, {})),
    });
    const ctx = await start(ALL_KEYS, upstream);
    try {
      const body = await (await ctx.post('/v1/url/check', { url: 'https://example.com' })).json();
      assert.equal(attempts, 2);
      assert.equal(body.providers.find((p) => p.name === 'google_safe_browsing').status, 'clean');
    } finally {
      ctx.server.close();
    }
  });

  it('caches results by URL hash', async () => {
    const upstream = fakeFetch(cleanUpstream());
    const ctx = await start(ALL_KEYS, upstream);
    try {
      const first = await (await ctx.post('/v1/url/check', { url: 'https://example.com/a' })).json();
      const second = await (await ctx.post('/v1/url/check', { url: 'https://example.com/a' })).json();
      assert.equal(first.cached, false);
      assert.equal(second.cached, true);
      assert.equal(upstream.calls.filter((c) => c.host === 'safebrowsing.googleapis.com').length, 1);
    } finally {
      ctx.server.close();
    }
  });

  it('rate limit exceeded returns 429 with Retry-After', async () => {
    const ctx = await start({ ...ALL_KEYS, RATE_LIMIT_PER_MINUTE: '2' }, fakeFetch(cleanUpstream()));
    try {
      const install = { 'x-cybershield-install': '3f2b8a4e-1c2d-4e5f-8a9b-0c1d2e3f4a5b' };
      assert.equal((await ctx.post('/v1/url/check', { url: 'https://a.example' }, install)).status, 200);
      assert.equal((await ctx.post('/v1/url/check', { url: 'https://b.example' }, install)).status, 200);
      const limited = await ctx.post('/v1/url/check', { url: 'https://c.example' }, install);
      assert.equal(limited.status, 429);
      assert.equal((await limited.json()).error.code, 'rate_limited');
      assert.ok(Number(limited.headers.get('retry-after')) >= 1);
    } finally {
      ctx.server.close();
    }
  });

  it('VirusTotal free-tier quota is enforced locally before calling the API', async () => {
    const upstream = fakeFetch(cleanUpstream());
    const ctx = await start({ ...ALL_KEYS, VIRUSTOTAL_PER_MINUTE: '1', RATE_LIMIT_PER_MINUTE: '100' }, upstream);
    try {
      await ctx.post('/v1/url/check', { url: 'https://one.example' });
      const body = await (await ctx.post('/v1/url/check', { url: 'https://two.example' })).json();
      assert.equal(body.providers.find((p) => p.name === 'virustotal').status, 'rate_limited');
      assert.equal(upstream.calls.filter((c) => c.host === 'www.virustotal.com').length, 1);
    } finally {
      ctx.server.close();
    }
  });

  it('expands known shorteners via Location header only and checks the destination', async () => {
    const upstream = fakeFetch({
      ...cleanUpstream(),
      'bit.ly': (_u, init) => {
        assert.equal(init.method, 'HEAD');
        assert.equal(init.redirect, 'manual');
        return new Response(null, { status: 301, headers: { location: 'https://evil.example/kyc?x=1' } });
      },
      'safebrowsing.googleapis.com': (u) => {
        assert.deepEqual(u.searchParams.getAll('urls'), ['https://bit.ly/abc', 'https://evil.example/kyc?x=1']);
        return json(200, { threats: [{ url: 'https://evil.example/kyc?x=1', threatTypes: ['SOCIAL_ENGINEERING'] }] });
      },
    });
    const ctx = await start(ALL_KEYS, upstream);
    try {
      const body = await (await ctx.post('/v1/url/check', { url: 'https://bit.ly/abc' })).json();
      assert.equal(body.expandedUrl, 'https://evil.example/kyc?x=1');
      assert.equal(body.verdict, 'malicious');
      assert.ok(!upstream.calls.some((c) => c.host === 'evil.example'), 'the destination itself is never fetched');
    } finally {
      ctx.server.close();
    }
  });
});

describe('POST /v1/ai/analyze', () => {
  const goodAnalysis = {
    riskLevel: 'high', scamType: 'Fake KYC update', summary: 'This message pressures you to update KYC through a fake link.',
    techniques: ['Urgency'], redFlags: ['Unofficial link'], safeSteps: ['Do not click', 'Call 1930 if money was lost'], confidence: 'high',
  };
  const gemini = (handler) => fakeFetch({ 'generativelanguage.googleapis.com': handler });

  it('valid request returns validated structured analysis', async () => {
    let sent;
    const ctx = await start(ALL_KEYS, gemini(async (u, init) => {
      sent = { path: u.pathname, headers: init.headers, body: JSON.parse(init.body) };
      return json(200, { candidates: [{ content: { parts: [{ text: JSON.stringify(goodAnalysis) }] } }], modelVersion: 'gemini-test' });
    }));
    try {
      const res = await ctx.post('/v1/ai/analyze', { text: 'Your KYC expired, update at [URL]', kind: 'message', language: 'hi', localFindings: ['KYC_UPDATE'] });
      assert.equal(res.status, 200);
      const body = await res.json();
      assert.deepEqual(body.analysis, goodAnalysis);
      assert.equal(body.model, 'gemini-test');
      assert.match(sent.path, /\/v1beta\/models\/gemini-3\.5-flash:generateContent$/);
      assert.equal(sent.headers['x-goog-api-key'], 'gm-test');
      assert.equal(sent.body.generationConfig.responseMimeType, 'application/json');
      assert.ok(sent.body.generationConfig.responseJsonSchema.required.includes('safeSteps'));
      assert.match(sent.body.systemInstruction.parts[0].text, /Hindi/);
      assert.match(sent.body.contents[0].parts[0].text, /<<<CONTENT\nYour KYC expired/);
    } finally {
      ctx.server.close();
    }
  });

  it('missing credentials returns 503 not_configured', async () => {
    const ctx = await start({}, gemini(() => assert.fail('must not call provider')));
    try {
      const res = await ctx.post('/v1/ai/analyze', { text: 'hello' });
      assert.equal(res.status, 503);
      assert.equal((await res.json()).error.code, 'not_configured');
    } finally {
      ctx.server.close();
    }
  });

  it('invalid request is rejected before calling the provider', async () => {
    // Rate limiting runs before validation (so invalid floods are throttled too); raise it for this test.
    const ctx = await start({ ...ALL_KEYS, AI_RATE_LIMIT_PER_MINUTE: '100' }, gemini(() => assert.fail('must not call provider')));
    try {
      for (const body of [{}, { text: '' }, { text: 'x'.repeat(4001) }, { text: 'a', kind: 'video' }, { text: 'a', language: 'fr' },
        { text: 'a', localFindings: ['drop table'] }]) {
        assert.equal((await ctx.post('/v1/ai/analyze', body)).status, 400, JSON.stringify(body).slice(0, 50));
      }
    } finally {
      ctx.server.close();
    }
  });

  it('provider unavailable, rate-limited or malformed output map to clear errors', async () => {
    const cases = [
      [() => { throw new TypeError('fetch failed'); }, 502, 'provider_unavailable'],
      [() => json(429, {}), 503, 'provider_rate_limited'],
      [() => json(403, {}), 502, 'provider_auth'],
      [() => json(200, { candidates: [{ content: { parts: [{ text: '{"riskLevel":"extreme"}' }] } }] }), 502, 'provider_bad_output'],
      [() => json(200, { candidates: [{ content: { parts: [{ text: 'not json' }] } }] }), 502, 'provider_bad_output'],
    ];
    for (const [handler, status, code] of cases) {
      const ctx = await start(ALL_KEYS, gemini(handler));
      try {
        const res = await ctx.post('/v1/ai/analyze', { text: 'test message' });
        assert.equal(res.status, status, code);
        assert.equal((await res.json()).error.code, code);
      } finally {
        ctx.server.close();
      }
    }
  });

  it('AI rate limit exceeded returns 429', async () => {
    const ctx = await start({ ...ALL_KEYS, AI_RATE_LIMIT_PER_MINUTE: '1' }, gemini(() =>
      json(200, { candidates: [{ content: { parts: [{ text: JSON.stringify(goodAnalysis) }] } }] })));
    try {
      assert.equal((await ctx.post('/v1/ai/analyze', { text: 'one' })).status, 200);
      assert.equal((await ctx.post('/v1/ai/analyze', { text: 'two' })).status, 429);
    } finally {
      ctx.server.close();
    }
  });
});

describe('unit helpers', () => {
  it('normaliseUrl adds https, strips credentials and fragments', () => {
    assert.equal(normaliseUrl('Example.COM/path#x'), 'https://example.com/path');
    assert.throws(() => normaliseUrl('ftp://example.com'));
    assert.throws(() => normaliseUrl('data:text/html,hi'));
  });

  it('validateAnalysis clamps lists and rejects off-schema output', () => {
    assert.equal(validateAnalysis(null), null);
    assert.equal(validateAnalysis({ riskLevel: 'high', confidence: 'high', summary: '' }), null);
    const out = validateAnalysis({ riskLevel: 'low', confidence: 'low', summary: 'ok', scamType: '', techniques: Array(20).fill('t'), redFlags: [1, 'x'], safeSteps: 'no' });
    assert.equal(out.techniques.length, 6);
    assert.deepEqual(out.redFlags, ['x']);
    assert.deepEqual(out.safeSteps, []);
  });
});
