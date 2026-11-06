// Browser client for the CyberShield backend. Mirrors android/.../cloud/CloudClient.kt.
import { config } from './config.js';

/** HTTPS only, except http://localhost for local testing (the app's release builds have the same rule). */
export function cloudConfigured() {
  try {
    const u = new URL(config.backendUrl);
    return u.protocol === 'https:' || (u.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(u.hostname));
  } catch {
    return false;
  }
}

/** Random per-browser ID used only for the backend's rate limits, like the app's install ID. */
function installId() {
  const key = 'cybershield.installId';
  try {
    let id = localStorage.getItem(key);
    if (!id) {
      id = crypto.randomUUID();
      localStorage.setItem(key, id);
    }
    return id;
  } catch {
    return crypto.randomUUID();
  }
}

/** Result: { ok: true, data } or { ok: false, reason: 'not_configured'|'offline'|'rate_limited'|'failed', message } */
async function call(path, body, timeoutMs) {
  if (!cloudConfigured()) return { ok: false, reason: 'not_configured' };
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(config.backendUrl.replace(/\/+$/, '') + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CyberShield-Install': installId() },
      body: JSON.stringify(body),
      signal: controller.signal,
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
    });
    const json = await res.json().catch(() => null);
    if (res.ok && json) return { ok: true, data: json };
    if (res.status === 429) return { ok: false, reason: 'rate_limited' };
    if (res.status === 503 && json?.error?.code === 'not_configured') return { ok: false, reason: 'not_configured' };
    return { ok: false, reason: 'failed', message: json?.error?.message ?? `HTTP ${res.status}` };
  } catch {
    return { ok: false, reason: 'offline' };
  } finally {
    clearTimeout(timer);
  }
}

export const checkUrl = (url) => call('/v1/url/check', { url: url.slice(0, 2048) }, 15_000);

export const explain = (redactedText, kind, language, localFindings) =>
  call('/v1/ai/analyze', { text: redactedText.slice(0, 4000), kind, language, localFindings: localFindings.slice(0, 20) }, 35_000);
