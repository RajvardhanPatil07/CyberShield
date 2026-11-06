import { createServer } from 'node:http';
import { geminiExplainer } from './ai.js';
import { expandShortUrl } from './expand.js';
import { aggregate, safeBrowsing, urlhaus, virusTotal } from './providers.js';
import { buildContributionRecord, fileContributionSink, nullContributionSink } from './contribute.js';
import { HttpError, logRequest, RateLimiter, sha256, TtlCache } from './support.js';
import { validateAiRequest, validateContributionRequest, validateUrlRequest, validInstallId } from './validate.js';

const VERSION = '0.3.0';
const DISCLAIMER = 'AI-generated explanation. It can be wrong; confirm with official channels before acting.';

/**
 * Builds the request handler. Everything external (fetch, clock, providers) is injectable so the
 * test suite runs without network access or real API keys.
 */
export function createApp(config, { fetchImpl = fetch, now = Date.now, providers, explainer, contributionSink, log = logRequest } = {}) {
  const deps = { fetchImpl, timeoutMs: config.providerTimeoutMs };
  const intel = providers ?? [
    safeBrowsing({ apiKey: config.keys.safeBrowsing, ...deps }),
    virusTotal({ apiKey: config.keys.virusTotal, perMinute: config.virusTotalPerMinute, now, ...deps }),
    urlhaus({ authKey: config.keys.urlhaus, ...deps }),
  ];
  const ai = explainer ?? geminiExplainer({ apiKey: config.keys.gemini, model: config.geminiModel, fetchImpl, timeoutMs: config.aiTimeoutMs });
  const contributions = contributionSink
    ?? (config.contributionsDir ? fileContributionSink(config.contributionsDir, { now }) : nullContributionSink);
  const urlLimiter = new RateLimiter(config.urlRatePerMinute, 60_000, now);
  const aiLimiter = new RateLimiter(config.aiRatePerMinute, 60_000, now);
  const contributeLimiter = new RateLimiter(config.contributeRatePerMinute, 60_000, now);
  const cache = new TtlCache(5000, now);

  function clientKeys(req) {
    const forwarded = config.trustProxy ? String(req.headers['x-forwarded-for'] ?? '').split(',')[0].trim() : '';
    const ip = forwarded || req.socket.remoteAddress || 'unknown';
    const install = validInstallId(req.headers['x-cybershield-install']);
    return install ? [`ip:${ip}`, `install:${install}`] : [`ip:${ip}`];
  }

  function enforceLimit(limiter, req, route) {
    for (const key of clientKeys(req)) {
      const retry = limiter.take(`${route}|${key}`);
      if (retry > 0) throw new HttpError(429, 'rate_limited', 'Too many requests. Try again later.', { 'Retry-After': String(retry) });
    }
  }

  async function readJson(req) {
    const type = String(req.headers['content-type'] ?? '');
    if (!type.toLowerCase().startsWith('application/json')) throw new HttpError(415, 'unsupported_media_type', 'Use Content-Type: application/json.');
    const declared = Number(req.headers['content-length'] ?? 0);
    if (declared > config.maxBodyBytes) throw new HttpError(413, 'payload_too_large', `Body exceeds ${config.maxBodyBytes} bytes.`);
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += chunk.length;
      if (size > config.maxBodyBytes) throw new HttpError(413, 'payload_too_large', `Body exceeds ${config.maxBodyBytes} bytes.`);
      chunks.push(chunk);
    }
    try {
      return JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch {
      throw new HttpError(400, 'invalid_json', 'Body is not valid JSON.');
    }
  }

  async function checkUrl(req) {
    enforceLimit(urlLimiter, req, 'url');
    const { url } = validateUrlRequest(await readJson(req));
    const key = sha256(url);
    const hit = cache.get(key);
    if (hit) return { body: { ...hit, cached: true }, cached: true };

    const expandedUrl = await expandShortUrl(url, deps);
    const urls = expandedUrl ? [url, expandedUrl] : [url];
    const settled = await Promise.allSettled(intel.map((p) => p.check(urls)));
    const results = settled.map((s, i) => {
      const r = s.status === 'fulfilled' ? s.value : { status: 'error', threats: [], detail: 'Provider failed' };
      return { name: intel[i].name, status: r.status, threats: r.threats ?? [], ...(r.detail ? { detail: r.detail } : {}), cacheSeconds: r.cacheSeconds };
    });
    const verdict = aggregate(results);
    const body = {
      verdict,
      checkedUrl: url,
      expandedUrl,
      providers: results.map(({ cacheSeconds, ...rest }) => rest),
      checkedAt: new Date(now()).toISOString(),
      cached: false,
    };
    // Only cache answers that came from at least one provider. Honour Safe Browsing's cacheDuration.
    if (verdict !== 'unavailable') {
      const sbSeconds = results.find((r) => r.cacheSeconds)?.cacheSeconds;
      const ttl = Math.min(config.cacheTtlSeconds, verdict === 'malicious' && sbSeconds ? sbSeconds : config.cacheTtlSeconds);
      cache.set(key, body, ttl * 1000);
    }
    return { body, cached: false };
  }

  async function analyse(req) {
    enforceLimit(aiLimiter, req, 'ai');
    const request = validateAiRequest(await readJson(req));
    const { analysis, model } = await ai.explain(request);
    return { body: { analysis, model, disclaimer: DISCLAIMER } };
  }

  async function contribute(req) {
    enforceLimit(contributeLimiter, req, 'contribute');
    const request = validateContributionRequest(await readJson(req));
    if (!contributions.configured) {
      throw new HttpError(503, 'not_configured', 'Contributions are not enabled on this server.');
    }
    const record = buildContributionRecord(request, now());
    await contributions.store(record);
    return { body: { status: 'stored', id: record.id } };
  }

  function health() {
    return {
      body: {
        status: 'ok',
        version: VERSION,
        providers: Object.fromEntries(intel.map((p) => [p.name, p.configured ? 'configured' : 'not_configured'])),
        ai: ai.configured ? 'configured' : 'not_configured',
        contributions: contributions.configured ? 'configured' : 'not_configured',
      },
    };
  }

  const ENDPOINTS = {
    'GET /health': 'Liveness and which providers are configured',
    'POST /v1/url/check': 'Link reputation (Safe Browsing, VirusTotal, URLhaus)',
    'POST /v1/ai/analyze': 'AI explanation of a redacted message',
    'POST /v1/contribute': 'Opt-in, redacted training-data donation',
  };
  const DOCS = 'https://github.com/RajvardhanPatil07/CyberShield/tree/main/backend';

  /**
   * What you see when you open the server address. Browsers (Accept: text/html) get a readable
   * status page; API clients get the same information as JSON.
   */
  function index(req) {
    const body = { service: 'CyberShield backend', version: VERSION, status: 'ok', endpoints: ENDPOINTS, docs: DOCS };
    const wantsHtml = String(req.headers.accept ?? '').toLowerCase().includes('text/html');
    return wantsHtml ? { body, html: statusPage() } : { body };
  }

  function statusPage() {
    const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
    const label = { google_safe_browsing: 'Google Safe Browsing', virustotal: 'VirusTotal', urlhaus: 'URLhaus' };
    const features = [
      ['AI explanations (Gemini)', ai.configured],
      ...intel.map((p) => [`Link check: ${label[p.name] ?? p.name}`, p.configured]),
      ['Training-data donations', contributions.configured],
    ];
    const rows = features.map(([name, on]) =>
      `<li><span>${esc(name)}</span><b class="${on ? 'on' : 'off'}">${on ? 'On' : 'Not set up'}</b></li>`).join('');
    const eps = Object.entries(ENDPOINTS).map(([k, v]) => `<li><code>${esc(k)}</code><span>${esc(v)}</span></li>`).join('');
    return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>CyberShield backend</title><style>
body{margin:0;font:16px/1.5 system-ui,-apple-system,Segoe UI,sans-serif;background:#eef3ff;color:#0f1d3d}
main{max-width:640px;margin:48px auto;padding:0 20px}
.card{background:#fff;border-radius:20px;padding:24px 28px;margin-bottom:16px;box-shadow:0 6px 24px rgba(30,60,140,.08)}
h1{margin:0 0 4px;font-size:28px}.sub{margin:0;color:#4b5a7a}
.ok{display:inline-block;margin-top:14px;background:#e3f6ec;color:#0b7a43;font-weight:700;border-radius:99px;padding:4px 14px}
h2{font-size:17px;margin:0 0 12px}ul{list-style:none;margin:0;padding:0}
li{display:flex;justify-content:space-between;gap:16px;padding:9px 0;border-top:1px solid #edf0f7}li:first-child{border-top:0}
b.on{color:#0b7a43}b.off{color:#8a94ab;font-weight:600}
code{font:600 14px ui-monospace,Menlo,monospace;color:#2347e6;white-space:nowrap}li span{color:#4b5a7a;text-align:right}
a{color:#2347e6}footer{color:#4b5a7a;font-size:14px;text-align:center}
</style></head><body><main>
<div class="card"><h1>CyberShield backend</h1><p class="sub">Version ${esc(VERSION)}. This server is used by the CyberShield app for optional cloud checks.</p><span class="ok">● Running</span></div>
<div class="card"><h2>Features</h2><ul>${rows}</ul></div>
<div class="card"><h2>API endpoints</h2><ul>${eps}</ul></div>
<footer>Documentation: <a href="${esc(DOCS)}">github.com/RajvardhanPatil07/CyberShield</a></footer>
</main></body></html>`;
  }

  const routes = {
    'GET /': index,
    'GET /health': health,
    'POST /v1/url/check': checkUrl,
    'POST /v1/ai/analyze': analyse,
    'POST /v1/contribute': contribute,
  };

  /** CORS headers for browser callers on an allowed origin (ALLOWED_ORIGINS); none otherwise. */
  function corsHeaders(req) {
    if (config.allowedOrigins.size === 0) return {};
    const origin = String(req.headers.origin ?? '');
    if (!config.allowedOrigins.has(origin.toLowerCase())) return { Vary: 'Origin' };
    return { 'Access-Control-Allow-Origin': origin, 'Access-Control-Expose-Headers': 'Retry-After', 'Access-Control-Max-Age': '600', Vary: 'Origin' };
  }

  function preflight(req, res, path) {
    const cors = corsHeaders(req);
    const knownPath = Object.keys(routes).some((r) => r.endsWith(` ${path}`));
    const allowed = knownPath && cors['Access-Control-Allow-Origin'];
    res.writeHead(allowed ? 204 : 403, {
      ...cors,
      ...(allowed ? { 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'Content-Type, Accept, X-CyberShield-Install' } : {}),
      'Cache-Control': 'no-store',
    });
    res.end();
    log({ method: 'OPTIONS', path: knownPath ? path : 'unmatched', status: allowed ? 204 : 403, ms: 0 });
  }

  return async function handler(req, res) {
    const started = now();
    const path = (req.url ?? '/').split('?')[0];
    if (req.method === 'OPTIONS') return preflight(req, res, path);
    const route = routes[`${req.method} ${path}`];
    let status = 200;
    let cached;
    let payload;
    let headers = {};
    try {
      if (!route) {
        const known = Object.keys(routes).some((r) => r.endsWith(` ${path}`));
        throw known ? new HttpError(405, 'method_not_allowed', 'Method not allowed.') : new HttpError(404, 'not_found', 'Not found.');
      }
      const out = await route(req);
      if (out.html) {
        // Static page, no scripts: a strict CSP blocks everything except its own inline styles.
        res.writeHead(200, {
          'Content-Type': 'text/html; charset=utf-8',
          'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
          'Cache-Control': 'no-store',
          'X-Content-Type-Options': 'nosniff',
          'Referrer-Policy': 'no-referrer',
          Vary: 'Accept',
        });
        res.end(out.html);
        log({ method: req.method, path, status: 200, ms: now() - started });
        return;
      }
      payload = out.body;
      cached = out.cached;
    } catch (err) {
      if (err instanceof HttpError) {
        status = err.status;
        headers = err.headers;
        payload = { error: { code: err.code, message: err.message } };
      } else {
        status = 500;
        payload = { error: { code: 'internal_error', message: 'Unexpected server error.' } };
        process.stderr.write(`internal error: ${err?.name ?? 'Error'}\n`); // no message: it could echo user data
      }
    }
    res.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      ...corsHeaders(req),
      ...headers,
    });
    res.end(JSON.stringify(payload));
    log({ method: req.method, path: route ? path : 'unmatched', status, ms: now() - started, cached });
  };
}

export function createAppServer(config, deps) {
  const server = createServer(createApp(config, deps));
  server.requestTimeout = 60_000;
  server.headersTimeout = 15_000;
  return server;
}
