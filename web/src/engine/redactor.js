// Port of android/.../security/Redactor.kt: removes personal data before text leaves the browser.
import { extractUrls, originOf, parseUrl } from './domain.js';
import { findAll } from './kt.js';
import { MAX_INPUT } from './scamlens.js';

const rules = [
  [/\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b/gi, '[EMAIL]'],
  [/\b[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}\b/g, '[UPI-ID]'],
  [/\b[A-Z]{5}[0-9]{4}[A-Z]\b/g, '[PAN]'],
  [/\b\d{4}[ \-]?\d{4}[ \-]?\d{4}[ \-]?\d{1,7}\b/g, '[CARD]'],
  [/\b\d{4}[ \-]\d{4}[ \-]\d{4}\b/g, '[AADHAAR]'],
  [/(?<!\d)(?:\+?91[ \-]?)?[6-9]\d{4}[ \-]?\d{5}(?!\d)/g, '[PHONE]'],
  [/\b(?:a\/?c|acct|account)(?:[ \t\n\x0B\f\r]*(?:no\.?|number))?[ \t\n\x0B\f\r]*[:.\-]?[ \t\n\x0B\f\r]*[xX*]*\d{3,18}\b/gi, '[ACCOUNT]'],
  [/(?<!\d)\d{4,18}(?!\d)/g, '[NUMBER]'],
];

const replaceRange = (s, start, end, value) => s.slice(0, start) + value + s.slice(end);

/** Keeps scheme, host and path of URLs; drops query strings and fragments, which often carry tokens. */
function stripUrlQueries(text) {
  let out = text;
  for (const r of extractUrls(text, 20).sort((a, b) => b.start - a.start)) {
    const parsed = parseUrl(r.url);
    if (!parsed) continue;
    out = replaceRange(out, r.start, r.end, originOf(parsed) + parsed.path.slice(0, 80));
  }
  return out;
}

const keptInReports = [
  /(?:\brs\.?|\binr|₹)[ \t\n\x0B\f\r]?\d[\d,]*(?:\.\d{1,2})?/gi,
  /\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b/gi,
  /\b[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}\b/g,
  /(?<!\d)(?:\+?91[ \-]?)?[6-9]\d{4}[ \-]?\d{5}(?!\d)/g,
  /(?<!\d)(?:1800|1860)[ \-]?\d{3}[ \-]?\d{4}(?!\d)/g,
  /(?<!\d)0\d{2,4}[ \-]\d{6,8}(?!\d)/g,
];
const userSecrets = rules.filter(([, label]) => ['[PAN]', '[CARD]', '[AADHAAR]', '[ACCOUNT]', '[NUMBER]'].includes(label));

/**
 * Text for a fraud report the user sends themselves: removes the user's own secrets and link query strings,
 * keeps the sender's phone numbers, UPI IDs, e-mail addresses, links and rupee amounts.
 */
export function forReport(text) {
  let out = stripUrlQueries(text.slice(0, MAX_INPUT));
  const kept = [];
  const protect = (start, end) => {
    kept.push(out.slice(start, end));
    out = replaceRange(out, start, end, `\u0000${kept.length - 1}\u0000`);
  };
  for (const r of extractUrls(out, 20).sort((a, b) => b.start - a.start)) protect(r.start, r.end);
  for (const regex of keptInReports) for (const m of findAll(regex, out).reverse()) protect(m.start, m.end);
  for (const [regex, replacement] of userSecrets) out = out.replace(regex, replacement);
  // Function replacements keep "$" in the kept text literal, like Kotlin's String.replace.
  for (let i = kept.length - 1; i >= 0; i--) out = out.replaceAll(`\u0000${i}\u0000`, () => kept[i]);
  return out;
}

export function redact(text) {
  let out = stripUrlQueries(text.slice(0, MAX_INPUT));
  // Protect URLs from number/e-mail rules by swapping them for placeholders first.
  const urls = extractUrls(out, 20);
  const placeholders = [];
  for (const r of [...urls].sort((a, b) => b.start - a.start)) {
    placeholders.unshift(r.url);
    out = replaceRange(out, r.start, r.end, `\u0000${urls.length - placeholders.length}\u0000`);
  }
  for (const [regex, replacement] of rules) out = out.replace(regex, replacement);
  placeholders.forEach((url, i) => {
    out = out.replaceAll(`\u0000${i}\u0000`, () => url);
  });
  return out;
}
