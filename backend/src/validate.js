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

// Contributions (opt-in, redacted scam-message donations for the ML training corpus).
const CONTRIB_LABELS = new Set(['scam', 'legit', 'unsure']);
const CONTRIB_LANGUAGES = new Set(['en', 'hi', 'mr', 'hinglish', 'mixed', 'unknown']);
const EMAIL_RE = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
const PHONE_RE = /(?<!\d)(?:\+?91[ -]?)?[6-9]\d{4}[ -]?\d{5}(?!\d)/;
// The on-device Redactor replaces any run of 4+ digits with [NUMBER]; a run of 5+ digits reaching the
// server means the text was not redacted. Rupee amounts survive redaction only as <=3-digit groups.
const LONG_DIGITS_RE = /\d{5,}/;

/** Defence in depth: the app must redact first; reject anything that still carries obvious personal data. */
export function looksUnredacted(text) {
  return EMAIL_RE.test(text) || PHONE_RE.test(text) || LONG_DIGITS_RE.test(text);
}

export function validateContributionRequest(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw bad('Body must be a JSON object.');
  const { text, label, language = 'unknown', kind = 'message', findings = [] } = body;
  if (typeof text !== 'string' || text.trim().length === 0) throw bad('"text" is required.');
  if (text.length > MAX_TEXT_LENGTH) throw bad(`"text" must be at most ${MAX_TEXT_LENGTH} characters.`);
  if (!CONTRIB_LABELS.has(label)) throw bad('"label" must be scam, legit or unsure.');
  if (!CONTRIB_LANGUAGES.has(language)) throw bad('"language" is not a recognised value.');
  if (!KINDS.has(kind)) throw bad('"kind" must be message, url or screenshot.');
  if (!Array.isArray(findings) || findings.length > 20 || !findings.every((c) => typeof c === 'string' && FINDING_CODE.test(c))) {
    throw bad('"findings" must be up to 20 finding codes.');
  }
  const trimmed = text.trim();
  if (looksUnredacted(trimmed)) {
    throw new HttpError(400, 'unredacted', 'Submission still contains personal data; it must be redacted on-device first.');
  }
  return { text: trimmed, label, language, kind, findings };
}
