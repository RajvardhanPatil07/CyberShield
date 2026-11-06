/**
 * All configuration comes from environment variables. Secrets are never logged or returned.
 * See .env.example and README.md for how to obtain each key.
 */
function int(value, fallback, min, max) {
  const n = Number.parseInt(value ?? '', 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

function secret(value) {
  const v = (value ?? '').trim();
  return v.length > 0 ? v : null;
}

export function loadConfig(env = process.env) {
  return {
    host: env.HOST || '0.0.0.0',
    port: int(env.PORT, 8787, 0, 65535),
    trustProxy: env.TRUST_PROXY === 'true',
    // Web origins (e.g. https://cybershield.vercel.app) whose pages may call the API from a browser. Exact matches only.
    allowedOrigins: new Set(
      String(env.ALLOWED_ORIGINS ?? '').split(',').map((o) => o.trim().toLowerCase().replace(/\/+$/, '')).filter(Boolean),
    ),
    maxBodyBytes: int(env.MAX_BODY_BYTES, 16 * 1024, 1024, 64 * 1024),
    urlRatePerMinute: int(env.RATE_LIMIT_PER_MINUTE, 20, 1, 1000),
    aiRatePerMinute: int(env.AI_RATE_LIMIT_PER_MINUTE, 5, 1, 1000),
    contributeRatePerMinute: int(env.CONTRIBUTE_RATE_LIMIT_PER_MINUTE, 10, 1, 1000),
    // Directory for opt-in, redacted scam-message contributions. Unset disables the endpoint.
    contributionsDir: ((env.CONTRIBUTIONS_DIR ?? '').trim()) || null,
    // Safe Browsing forbids caching beyond 24 hours; the code also honours its cacheDuration.
    cacheTtlSeconds: int(env.CACHE_TTL_SECONDS, 3600, 0, 86400),
    providerTimeoutMs: int(env.PROVIDER_TIMEOUT_MS, 5000, 500, 20000),
    aiTimeoutMs: int(env.AI_TIMEOUT_MS, 25000, 2000, 60000),
    // VirusTotal public API: 4 requests/minute and 500/day per key.
    virusTotalPerMinute: int(env.VIRUSTOTAL_PER_MINUTE, 4, 1, 1000),
    keys: {
      safeBrowsing: secret(env.GOOGLE_SAFE_BROWSING_API_KEY),
      virusTotal: secret(env.VIRUSTOTAL_API_KEY),
      urlhaus: secret(env.URLHAUS_AUTH_KEY),
      gemini: secret(env.GEMINI_API_KEY),
    },
    geminiModel: (env.GEMINI_MODEL || 'gemini-3.5-flash').trim(),
  };
}
