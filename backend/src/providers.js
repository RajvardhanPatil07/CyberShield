import { fetchWithRetry, RateLimiter } from './support.js';

/**
 * Threat-intelligence providers. Each implements:
 *   name: string
 *   configured: boolean
 *   check(urls: string[]): Promise<{ status, threats: string[], detail?: string, cacheSeconds?: number }>
 * status ∈ malicious | suspicious | clean | unknown | error | not_configured | rate_limited
 *
 * Only "malicious" from a provider is reported to the app as a confirmed threat.
 */

const result = (status, threats = [], detail, extra = {}) => ({ status, threats, ...(detail ? { detail } : {}), ...extra });

/** Google Safe Browsing v5 urls:search. Non-commercial use only (commercial use requires Web Risk). */
export function safeBrowsing({ apiKey, fetchImpl, timeoutMs }) {
  return {
    name: 'google_safe_browsing',
    configured: Boolean(apiKey),
    async check(urls) {
      if (!apiKey) return result('not_configured');
      const qs = new URLSearchParams();
      urls.slice(0, 50).forEach((u) => qs.append('urls', u));
      qs.set('key', apiKey);
      let res;
      try {
        res = await fetchWithRetry(fetchImpl, `https://safebrowsing.googleapis.com/v5/urls:search?${qs}`, { method: 'GET' }, { timeoutMs });
      } catch {
        return result('error', [], 'Provider unreachable');
      }
      if (res.status === 429) return result('rate_limited');
      if (!res.ok) return result('error', [], `HTTP ${res.status}`);
      const body = await res.json().catch(() => null);
      if (!body) return result('error', [], 'Unreadable response');
      const threats = [...new Set((body.threats ?? []).flatMap((t) => t.threatTypes ?? []))];
      const cacheSeconds = Number.parseFloat(String(body.cacheDuration ?? '').replace(/s$/, '')) || undefined;
      return threats.length > 0 ? result('malicious', threats, undefined, { cacheSeconds }) : result('clean', [], undefined, { cacheSeconds });
    },
  };
}

/**
 * VirusTotal v3 URL report lookup. Looks up an existing report only and never submits the URL
 * for scanning, because submitted URLs become visible to VirusTotal's community.
 * Public API: 4 requests/minute, 500/day, non-commercial use only.
 */
export function virusTotal({ apiKey, fetchImpl, timeoutMs, perMinute = 4, now }) {
  const limiter = new RateLimiter(perMinute, 60_000, now);
  return {
    name: 'virustotal',
    configured: Boolean(apiKey),
    async check(urls) {
      if (!apiKey) return result('not_configured');
      if (limiter.take('global') > 0) return result('rate_limited', [], 'Free-tier quota reached; try again shortly');
      const id = Buffer.from(urls[urls.length - 1]).toString('base64url');
      let res;
      try {
        res = await fetchWithRetry(fetchImpl, `https://www.virustotal.com/api/v3/urls/${id}`, { headers: { 'x-apikey': apiKey } }, { timeoutMs });
      } catch {
        return result('error', [], 'Provider unreachable');
      }
      if (res.status === 404) return result('unknown', [], 'No VirusTotal report for this URL');
      if (res.status === 429) return result('rate_limited');
      if (res.status === 401 || res.status === 403) return result('error', [], 'API key rejected');
      if (!res.ok) return result('error', [], `HTTP ${res.status}`);
      const body = await res.json().catch(() => null);
      const stats = body?.data?.attributes?.last_analysis_stats;
      if (!stats) return result('error', [], 'Unreadable response');
      const malicious = Number(stats.malicious) || 0;
      const suspicious = Number(stats.suspicious) || 0;
      const detail = `${malicious} malicious, ${suspicious} suspicious engine verdicts`;
      // A single engine verdict is a common false positive, so it is reported as suspicious only.
      if (malicious >= 2) return result('malicious', ['MALICIOUS_ENGINES'], detail);
      if (malicious === 1 || suspicious > 0) return result('suspicious', [], detail);
      return result('clean', [], detail);
    },
  };
}

/** abuse.ch URLhaus lookup (malware distribution URLs). Free Auth-Key from auth.abuse.ch. */
export function urlhaus({ authKey, fetchImpl, timeoutMs }) {
  return {
    name: 'urlhaus',
    configured: Boolean(authKey),
    async check(urls) {
      if (!authKey) return result('not_configured');
      let res;
      try {
        res = await fetchWithRetry(fetchImpl, 'https://urlhaus-api.abuse.ch/v1/url/', {
          method: 'POST',
          headers: { 'Auth-Key': authKey, 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ url: urls[urls.length - 1] }).toString(),
        }, { timeoutMs });
      } catch {
        return result('error', [], 'Provider unreachable');
      }
      if (res.status === 429) return result('rate_limited');
      if (res.status === 401 || res.status === 403) return result('error', [], 'Auth-Key rejected');
      if (!res.ok) return result('error', [], `HTTP ${res.status}`);
      const body = await res.json().catch(() => null);
      switch (body?.query_status) {
        case 'ok':
          return result('malicious', [String(body.threat || 'malware_download').toUpperCase()], `URL status: ${body.url_status ?? 'unknown'}`);
        case 'no_results':
          return result('clean');
        default:
          return result('error', [], `query_status: ${body?.query_status ?? 'missing'}`);
      }
    },
  };
}

/** Combine provider answers. Errors never count as "safe". */
export function aggregate(providerResults) {
  const statuses = providerResults.map((p) => p.status);
  if (statuses.includes('malicious')) return 'malicious';
  if (statuses.includes('suspicious')) return 'suspicious';
  // "unknown" means the provider answered but has no record, which is still "not listed".
  if (statuses.includes('clean') || statuses.includes('unknown')) return 'no_known_threat';
  return 'unavailable';
}
