import { createHash } from 'node:crypto';

export class HttpError extends Error {
  constructor(status, code, message, headers = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.headers = headers;
  }
}

/** Fixed-window counter per key. In-memory: suitable for a single instance (see README for scaling). */
export class RateLimiter {
  constructor(limit, windowMs, now = Date.now) {
    this.limit = limit;
    this.windowMs = windowMs;
    this.now = now;
    this.windows = new Map();
  }

  /** Returns 0 when allowed, otherwise seconds until the window resets. */
  take(key) {
    const t = this.now();
    let w = this.windows.get(key);
    if (!w || t >= w.resetAt) {
      w = { count: 0, resetAt: t + this.windowMs };
      this.windows.set(key, w);
    }
    if (this.windows.size > 50_000) this.#sweep(t);
    if (w.count >= this.limit) return Math.max(1, Math.ceil((w.resetAt - t) / 1000));
    w.count += 1;
    return 0;
  }

  #sweep(t) {
    for (const [k, w] of this.windows) if (t >= w.resetAt) this.windows.delete(k);
  }
}

/** TTL cache with a size cap (oldest entry evicted first). Keys are hashes, never raw URLs. */
export class TtlCache {
  constructor(maxEntries = 5000, now = Date.now) {
    this.max = maxEntries;
    this.now = now;
    this.map = new Map();
  }

  get(key) {
    const e = this.map.get(key);
    if (!e) return undefined;
    if (this.now() >= e.expiresAt) {
      this.map.delete(key);
      return undefined;
    }
    return e.value;
  }

  set(key, value, ttlMs) {
    if (ttlMs <= 0) return;
    this.map.delete(key);
    this.map.set(key, { value, expiresAt: this.now() + ttlMs });
    while (this.map.size > this.max) this.map.delete(this.map.keys().next().value);
  }
}

export const sha256 = (s) => createHash('sha256').update(s).digest('hex');

/**
 * fetch with a hard timeout and limited retries for network errors and 5xx responses.
 * 4xx responses (including 429) are returned to the caller without retrying.
 */
export async function fetchWithRetry(fetchImpl, url, init, { timeoutMs, retries = 1, backoffMs = 300 }) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetchImpl(url, { ...init, signal: AbortSignal.timeout(timeoutMs) });
      if (res.status >= 500 && attempt < retries) {
        lastError = new Error(`upstream ${res.status}`);
      } else {
        return res;
      }
    } catch (err) {
      lastError = err;
    }
    await new Promise((r) => setTimeout(r, backoffMs * (attempt + 1)));
  }
  throw lastError;
}

/** One structured line per request. Never includes bodies, URLs being checked, IPs or headers. */
export function logRequest({ method, path, status, ms, cached }) {
  const line = { ts: new Date().toISOString(), method, path, status, ms };
  if (cached !== undefined) line.cached = cached;
  process.stdout.write(JSON.stringify(line) + '\n');
}
