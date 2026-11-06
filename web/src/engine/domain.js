// Port of android/.../security/DomainTools.kt (UrlParser and DomainTools).
import { toAscii, toUnicode } from './idn.js';
import { before, findAll, isDigit, isWhitespace, toIntOrNull, trimChars, trimEndChars, trimWs, urlDecode } from './kt.js';

const schemeRegex = /^([a-zA-Z][a-zA-Z0-9+.\-]*):/;
const bareDomain = /^[\p{L}\p{N}][\p{L}\p{N}\-]*(\.[\p{L}\p{N}\-]+)+(:[0-9]+)?([/?#].*)?$/u;

/** A URL split into parts without the WHATWG parser, which normalises away the tricks we look for. */
export function parseUrl(raw) {
  let text = trimEndChars(trimChars(trimWs(raw), '<>"\''), '.,;)]}!');
  if (text.length === 0 || text.length > 4096 || text.split('').some((c) => isWhitespace(c) || c.charCodeAt(0) < 0x20)) return null;
  let schemeMissing = false;
  const schemeMatch = schemeRegex.exec(text);
  let looksLikeHostPort = false;
  if (schemeMatch) {
    const rest = text.slice(schemeMatch[0].length);
    let digits = 0;
    while (digits < rest.length && isDigit(rest[digits])) digits++;
    looksLikeHostPort = digits > 0 && schemeMatch[1].includes('.');
  }
  if (!schemeMatch || looksLikeHostPort) {
    if (!bareDomain.test(text)) return null;
    text = `https://${text}`;
    schemeMissing = true;
  }
  const scheme = schemeRegex.exec(text)[1].toLowerCase();
  const rest = text.slice(scheme.length + 1);
  if (!rest.startsWith('//')) {
    // Opaque URIs such as javascript:, data:, mailto:, upi:. They have no host to analyse.
    return { original: raw, scheme, userInfo: null, host: '', unicodeHost: '', port: null, path: rest, query: null, schemeWasMissing: schemeMissing, hostWasPercentEncoded: false };
  }
  const afterSlashes = rest.slice(2);
  let authorityEnd = afterSlashes.search(/[/?#]/);
  if (authorityEnd < 0) authorityEnd = afterSlashes.length;
  const authority = afterSlashes.slice(0, authorityEnd);
  const tail = afterSlashes.slice(authorityEnd);
  const at = authority.lastIndexOf('@');
  const userInfo = at >= 0 ? authority.slice(0, at) : null;
  const hostPort = at >= 0 ? authority.slice(at + 1) : authority;
  let host;
  let port = null;
  if (hostPort.startsWith('[')) {
    const close = hostPort.indexOf(']');
    if (close < 0) return null;
    host = hostPort.slice(1, close);
    const rem = hostPort.slice(close + 1);
    if (rem.startsWith(':')) {
      port = toIntOrNull(rem.slice(1));
      if (port === null) return null;
    }
  } else {
    const colon = hostPort.lastIndexOf(':');
    if (colon >= 0) {
      const p = hostPort.slice(colon + 1);
      if (p.length > 0) {
        port = toIntOrNull(p);
        if (port === null) return null;
      }
      host = hostPort.slice(0, colon);
    } else host = hostPort;
  }
  let encoded = false;
  if (host.includes('%')) {
    encoded = true;
    try {
      host = urlDecode(host);
    } catch {
      return null;
    }
  }
  host = trimEndChars(host, '.').toLowerCase();
  if (host.length === 0 || (port !== null && (port < 1 || port > 65535))) return null;
  let ascii;
  try {
    ascii = toAscii(host).toLowerCase();
  } catch {
    return null;
  }
  if (ascii.length === 0 || !/^[\p{L}\p{Nd}.\-:_]+$/u.test(ascii)) return null;
  let unicode;
  try {
    unicode = toUnicode(ascii);
  } catch {
    unicode = host;
  }
  const queryStart = tail.indexOf('?');
  let fragmentStart = tail.indexOf('#');
  if (fragmentStart < 0) fragmentStart = tail.length;
  const hasQuery = queryStart >= 0 && queryStart < fragmentStart;
  const path = hasQuery ? tail.slice(0, queryStart) : tail.slice(0, fragmentStart);
  const query = hasQuery ? tail.slice(queryStart + 1, fragmentStart) : null;
  return { original: raw, scheme, userInfo, host: ascii, unicodeHost: unicode, port, path, query, schemeWasMissing: schemeMissing, hostWasPercentEncoded: encoded };
}

/** scheme://host[:port]. Drops credentials, path, query and fragment. */
export const originOf = (u) => `${u.scheme}://${u.host}${u.port !== null ? `:${u.port}` : ''}`;

const inText =
  /\b(?:https?:\/\/|www\.)[^ \t\n\x0B\f\r<>"'`]+|\b[a-z0-9][a-z0-9\-]{0,62}(?:\.[a-z0-9\-]{1,63})*\.(?:com|in|net|org|info|biz|xyz|top|click|link|online|site|club|shop|live|app|io|me|ly|gl|co|cc|tk|ml|ga|cf|gq|icu|buzz|cyou|sbs|cfd|bond|rest|work|support|win|loan)(?::[0-9]{2,5})?(?:\/[^ \t\n\x0B\f\r<>"'`]*)?/gi;

/** Finds up to [limit] links in free text: [{url, start, end}], end exclusive. */
export function extractUrls(text, limit = 10) {
  const out = [];
  for (const m of findAll(inText, text)) {
    const trimmed = trimEndChars(m.value, '.,;:!?)]}\'"');
    const partOfEmail = (m.start > 0 && text[m.start - 1] === '@') || (trimmed.includes('@') && !trimmed.includes('://'));
    if (trimmed.length === 0 || partOfEmail) continue;
    out.push({ url: trimmed, start: m.start, end: m.start + trimmed.length });
    if (out.length >= limit) break;
  }
  return out;
}

/** Multi-label public suffixes we care about. A full Public Suffix List is not bundled. */
const multiPartSuffixes = new Set([
  'co.in', 'net.in', 'org.in', 'gov.in', 'nic.in', 'ac.in', 'edu.in', 'res.in', 'firm.in', 'gen.in', 'ind.in', 'mil.in', 'bank.in',
  'co.uk', 'org.uk', 'gov.uk', 'ac.uk', 'com.au', 'net.au', 'org.au', 'co.jp', 'com.br', 'com.sg', 'com.my', 'co.za',
  'com.cn', 'com.pk', 'com.bd', 'com.np', 'com.lk', 'co.nz', 'com.mx', 'com.tr', 'com.hk',
]);

/** Free hosting / tunnelling services where anyone can create a subdomain. Treated as public suffixes. */
export const freeHosting = [
  'web.app', 'firebaseapp.com', 'github.io', 'netlify.app', 'vercel.app', 'pages.dev', 'workers.dev', 'herokuapp.com',
  'blogspot.com', 'weebly.com', 'wixsite.com', '000webhostapp.com', 'glitch.me', 'repl.co', 'replit.app', 'ngrok.io',
  'ngrok-free.app', 'ngrok.app', 'trycloudflare.com', 'onrender.com', 'azurewebsites.net', 'appspot.com', 'square.site',
  'webflow.io', 'framer.website', 'godaddysites.com', 'mystrikingly.com', 'carrd.co', 'surge.sh', 'loca.lt',
];
const suffixes = new Set([...multiPartSuffixes, ...freeHosting]);

/** Registrable endings that only vetted organisations can obtain in India. */
const restrictedSuffixes = ['gov.in', 'nic.in', 'bank.in', 'mil.in', 'ac.in', 'edu.in', 'res.in'];

export const shorteners = new Set([
  'bit.ly', 'tinyurl.com', 'goo.gl', 'ow.ly', 'is.gd', 'cutt.ly', 'rb.gy', 'shorturl.at', 'tiny.cc', 'rebrand.ly',
  'bl.ink', 't.ly', 'v.gd', 's.id', 'shorte.st', 'adf.ly', 'buff.ly', 'surl.li', 'short.gy', 'tiny.one', 'shrtco.de',
  't.co', 'lnkd.in', 'wa.link', 'qr.ae', 'u.to', 'clck.ru', 'bitly.ws', 'shorturl.asia', 'y2u.be',
]);

export const suspiciousTlds = new Set([
  'xyz', 'top', 'click', 'link', 'zip', 'mov', 'icu', 'buzz', 'tk', 'ml', 'ga', 'cf', 'gq', 'rest', 'cyou', 'sbs', 'cfd',
  'bond', 'lol', 'monster', 'quest', 'support', 'win', 'loan', 'work', 'country', 'kim', 'date', 'racing', 'review',
  'stream', 'download', 'men', 'party', 'trade', 'accountant', 'science', 'faith', 'cricket', 'best', 'beauty', 'hair',
  'online', 'site', 'shop', 'store', 'live', 'cc',
]);

const brand = (keyword, official, exact = false) => ({ keyword, official: new Set(official), exact });

export const brands = [
  brand('sbi', ['sbi.co.in', 'onlinesbi.sbi', 'sbicard.com', 'sbilife.co.in', 'sbimf.com', 'sbi.bank.in'], true),
  brand('onlinesbi', ['onlinesbi.sbi', 'onlinesbi.com']),
  brand('yono', ['onlinesbi.sbi', 'sbi.co.in', 'yonobusiness.sbi'], true),
  brand('hdfc', ['hdfcbank.com', 'hdfc.com', 'hdfclife.com', 'hdfcergo.com', 'hdfcsec.com', 'hdfcbank.bank.in']),
  brand('icici', ['icicibank.com', 'icicidirect.com', 'iciciprulife.com', 'icicilombard.com', 'icicibank.bank.in']),
  brand('axis', ['axisbank.com', 'axisbank.bank.in'], true),
  brand('axisbank', ['axisbank.com', 'axisbank.bank.in']),
  brand('kotak', ['kotak.com', 'kotaksecurities.com', 'kotak.bank.in']),
  brand('paytm', ['paytm.com', 'paytmbank.com', 'paytmmoney.com', 'paytmmall.com', 'paytm.in']),
  brand('phonepe', ['phonepe.com']),
  brand('gpay', ['google.com'], true),
  brand('googlepay', ['google.com']),
  brand('bhim', ['bhimupi.org.in', 'npci.org.in'], true),
  brand('npci', ['npci.org.in', 'bhimupi.org.in']),
  brand('uidai', ['uidai.gov.in']),
  brand('aadhaar', ['uidai.gov.in']),
  brand('aadhar', ['uidai.gov.in']),
  brand('incometax', ['incometax.gov.in', 'incometaxindia.gov.in']),
  brand('irctc', ['irctc.co.in', 'irctc.com']),
  brand('amazon', ['amazon.in', 'amazon.com', 'amazon.co.uk', 'amazonpay.in', 'amazon.jobs']),
  brand('flipkart', ['flipkart.com']),
  brand('indiapost', ['indiapost.gov.in', 'ippbonline.com']),
  brand('epfo', ['epfindia.gov.in']),
  brand('parivahan', ['parivahan.gov.in']),
  brand('pmkisan', ['pmkisan.gov.in']),
  brand('fastag', ['ihmcl.co.in', 'ihmcl.com', 'npci.org.in']),
  brand('rbi', ['rbi.org.in'], true),
  brand('lic', ['licindia.in'], true),
  brand('whatsapp', ['whatsapp.com', 'whatsapp.net', 'wa.me']),
  brand('airtel', ['airtel.in', 'airtel.com']),
  brand('jio', ['jio.com', 'jiomart.com', 'jiocinema.com'], true),
  brand('bsnl', ['bsnl.co.in', 'bsnl.in']),
  brand('mahadiscom', ['mahadiscom.in']),
  brand('msedcl', ['mahadiscom.in']),
  brand('fedex', ['fedex.com']),
  brand('bluedart', ['bluedart.com']),
  brand('dtdc', ['dtdc.in', 'dtdc.com']),
  brand('netflix', ['netflix.com']),
  brand('google', ['google.com', 'google.co.in', 'youtube.com', 'goo.gl', 'g.co']),
  brand('microsoft', ['microsoft.com', 'live.com', 'office.com', 'microsoftonline.com']),
  brand('apple', ['apple.com', 'icloud.com']),
  brand('paypal', ['paypal.com', 'paypal.me']),
];

/** First label of each official domain → brand keyword, for misspelling checks (later entries win, like Kotlin associate). */
const officialLabels = (() => {
  const map = new Map();
  for (const b of brands) for (const domain of b.official) map.set(before(domain, '.'), b.keyword);
  for (const key of [...map.keys()]) if (key.length < 5) map.delete(key);
  return map;
})();

export const isIpAddress = (host) => host.includes(':') || /^(0x[0-9a-f]+|\d+)(\.(0x[0-9a-f]+|\d+)){0,3}$/.test(host);

/** Best-effort registrable domain ("eTLD+1"), e.g. login.sbi.co.in.evil.xyz → evil.xyz. */
export function registrableDomain(host) {
  if (isIpAddress(host)) return host;
  const labels = host.split('.').filter((l) => l.length > 0);
  if (labels.length <= 2) return labels.join('.');
  for (let take = 3; take >= 2; take--) {
    if (labels.length > take) {
      const candidate = labels.slice(-take).join('.');
      if (suffixes.has(candidate)) return labels.slice(-(take + 1)).join('.');
    }
    if (labels.length >= take && suffixes.has(labels.slice(-take).join('.'))) return labels.slice(-take).join('.');
  }
  return labels.slice(-2).join('.');
}

export const tld = (host) => {
  const i = host.lastIndexOf('.');
  return i < 0 ? host : host.slice(i + 1);
};

const isRestricted = (registrable) =>
  restrictedSuffixes.some((s) => registrable.endsWith(`.${s}`) || registrable === s) || tld(registrable) === 'sbi';

export const isOfficial = (registrable) => isRestricted(registrable) || brands.some((b) => b.official.has(registrable));

export const freeHostingSuffix = (host) => freeHosting.find((s) => host.endsWith(`.${s}`)) ?? null;

export const isShortener = (host) => shorteners.has(host.startsWith('www.') ? host.slice(4) : host);

const parts = (host) => host.split(/[.\-]/).filter((p) => p.length > 0);

/** Brands named in the hostname whose official domains do not include the actual registrable domain. */
export function impersonatedBrands(host, digitSwaps = false) {
  const registrable = registrableDomain(host);
  if (isOfficial(registrable)) return [];
  const hostParts = parts(digitSwaps ? confusableDigits(skeleton(host)) : skeleton(host));
  const out = [];
  for (const b of brands) {
    const named = hostParts.some((p) => p === b.keyword || (!b.exact && b.keyword.length >= 4 && p.includes(b.keyword)));
    if (named && !b.official.has(registrable) && !out.includes(b.keyword)) out.push(b.keyword);
  }
  return out;
}

/** A brand whose official label is one or two edits away from this domain's label. */
export function lookalikeOf(host) {
  const registrable = registrableDomain(host);
  if (isOfficial(registrable)) return null;
  const label = skeleton(before(registrable, '.'));
  const normalized = confusableDigits(label);
  for (const [official, brandKeyword] of officialLabels) {
    if (label === official) {
      if (before(registrable, '.') !== official || !brands.some((b) => b.official.has(registrable))) return brandKeyword;
      continue;
    }
    if (normalized === official) return brandKeyword;
    const maxEdits = official.length >= 8 ? 2 : 1;
    if (Math.abs(label.length - official.length) <= maxEdits && levenshtein(label, official) <= maxEdits) return brandKeyword;
  }
  return null;
}

const confusableDigits = (s) =>
  s.replaceAll('rn', 'm').replaceAll('vv', 'w').replaceAll('0', 'o').replaceAll('1', 'l').replaceAll('3', 'e')
    .replaceAll('5', 's').replaceAll('4', 'a').replaceAll('7', 't').replaceAll('8', 'b');

const confusables = {
  'а': 'a', 'е': 'e', 'о': 'o', 'р': 'p', 'с': 'c', 'х': 'x', 'у': 'y', 'і': 'i', 'ѕ': 's',
  'ԁ': 'd', 'ј': 'j', 'ӏ': 'l', 'һ': 'h', 'ԛ': 'q', 'ԝ': 'w', 'к': 'k', 'м': 'm', 'т': 't',
  'в': 'b', 'н': 'h', 'ο': 'o', 'α': 'a', 'ν': 'v', 'ρ': 'p', 'τ': 't', 'ι': 'i', 'κ': 'k',
  'ε': 'e', 'μ': 'u', 'ɡ': 'g', 'ı': 'i',
};

/** Maps common Cyrillic/Greek look-alike letters to Latin, so "раytm" (Cyrillic р, а) becomes "paytm". */
export const skeleton = (s) => s.split('').map((c) => confusables[c] ?? c).join('');

/** True when a single hostname label mixes Latin with Cyrillic or Greek letters. */
export function hasMixedScript(unicodeHost) {
  return unicodeHost.split('.').some((label) => {
    let latin = false;
    let other = false;
    for (const c of label.split('')) {
      if (/\p{Script=Latin}/u.test(c)) latin = true;
      else if (/[\p{Script=Cyrillic}\p{Script=Greek}]/u.test(c)) other = true;
    }
    return latin && other;
  });
}

export function levenshtein(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = cur;
  }
  return prev[b.length];
}
