import { HttpError } from './support.js';

const MAX_URL_LENGTH = 2048;
const MAX_TEXT_LENGTH = 4000;
const KINDS = new Set(['message', 'url', 'screenshot']);
const LANGUAGES = new Set(['en', 'hi', 'mr']);
const FINDING_CODE = /^[A-Z][A-Z_]{1,40}$/;

const bad = (message) => new HttpError(400, 'invalid_request', message);

/**
 * Validates and normalises a URL for reputation lookups:
 * http/https only, host required, credentials and fragment removed (they are private and
 * irrelevant to reputation), host lower-cased by the WHATWG parser.
 */
export function normaliseUrl(raw) {
  if (typeof raw !== 'string') throw bad('"url" must be a string.');
  const trimmed = raw.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_URL_LENGTH) throw bad(`"url" must be 1-${MAX_URL_LENGTH} characters.`);
  const candidate = /^[a-z][a-z0-9+.-]*:/i.test(trimmed) ? trimmed : `https://${trimmed}`;
  let url;
  try {
    url = new URL(candidate);
  } catch {
    throw bad('"url" is not a valid URL.');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') throw bad('Only http and https URLs can be checked.');
  if (!url.hostname) throw bad('"url" has no host.');
  url.username = '';
  url.password = '';
  url.hash = '';
  return url.toString();
}

export function validateUrlRequest(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw bad('Body must be a JSON object.');
  return { url: normaliseUrl(body.url) };
}

export function validateAiRequest(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw bad('Body must be a JSON object.');
  const { text, kind = 'message', language = 'en', localFindings = [] } = body;
  if (typeof text !== 'string' || text.trim().length === 0) throw bad('"text" is required.');
  if (text.length > MAX_TEXT_LENGTH) throw bad(`"text" must be at most ${MAX_TEXT_LENGTH} characters.`);
  if (!KINDS.has(kind)) throw bad('"kind" must be message, url or screenshot.');
  if (!LANGUAGES.has(language)) throw bad('"language" must be en, hi or mr.');
  if (!Array.isArray(localFindings) || localFindings.length > 20 || !localFindings.every((c) => typeof c === 'string' && FINDING_CODE.test(c))) {
    throw bad('"localFindings" must be up to 20 finding codes.');
  }
  return { text: text.trim(), kind, language, localFindings };
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const validInstallId = (v) => (typeof v === 'string' && UUID.test(v) ? v.toLowerCase() : null);
