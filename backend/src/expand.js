import { fetchWithRetry } from './support.js';

/**
 * Hosts whose redirects we follow. Only these hosts are ever contacted, so the expander cannot be
 * used to make the server request arbitrary or internal addresses (SSRF). The destination itself
 * is never fetched; only the shortener's Location header is read.
 */
export const SHORTENERS = new Set([
  'bit.ly', 'tinyurl.com', 'goo.gl', 'ow.ly', 'is.gd', 'cutt.ly', 'rb.gy', 'shorturl.at', 'tiny.cc', 'rebrand.ly',
  't.ly', 'v.gd', 's.id', 'surl.li', 'short.gy', 'buff.ly', 'bl.ink',
]);

const MAX_HOPS = 3;

export async function expandShortUrl(url, { fetchImpl, timeoutMs }) {
  let current = url;
  for (let hop = 0; hop < MAX_HOPS; hop++) {
    const parsed = new URL(current);
    const host = parsed.hostname.replace(/^www\./, '');
    if (!SHORTENERS.has(host) || parsed.protocol !== 'https:' && parsed.protocol !== 'http:') break;
    let res;
    try {
      res = await fetchWithRetry(fetchImpl, current, { method: 'HEAD', redirect: 'manual' }, { timeoutMs, retries: 0 });
    } catch {
      return current === url ? null : current;
    }
    const location = res.headers.get('location');
    if (res.status < 300 || res.status >= 400 || !location) break;
    let next;
    try {
      next = new URL(location, current);
    } catch {
      break;
    }
    if (next.protocol !== 'https:' && next.protocol !== 'http:') break;
    next.username = '';
    next.password = '';
    next.hash = '';
    current = next.toString();
  }
  return current === url ? null : current;
}
