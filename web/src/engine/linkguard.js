// Port of android/.../security/LinkGuard.kt. Never fetches the URL.
import {
  freeHostingSuffix, hasMixedScript, impersonatedBrands, isIpAddress, isOfficial, isShortener, lookalikeOf, parseUrl,
  registrableDomain, suspiciousTlds, tld,
} from './domain.js';
import { idiv, tryUrlDecode } from './kt.js';
import { assess, finding, Rec, weightOf } from './model.js';

const dangerousSchemes = new Set(['javascript', 'data', 'vbscript', 'file', 'intent', 'content', 'blob']);
const sensitiveWords = [
  'login', 'signin', 'verify', 'verification', 'kyc', 'ekyc', 'update', 'secure', 'account', 'wallet', 'claim',
  'reward', 'refund', 'prize', 'bonus', 'banking', 'netbanking', 'otp', 'password', 'unlock', 'suspend', 'blocked',
  'confirm', 'cashback', 'lottery', 'aadhaar', 'aadhar', 'redeem', 'gift', 'challan', 'penalty', 'parcel', 'electricity',
];
/** Short sensitive words that only count as whole hostname/path parts ("pan" must not match "company"). */
const sensitiveExact = new Set(['pan', 'upi', 'bank', 'pay', 'free', 'win', 'kyc', 'bill']);
const redirectParams = new Set(['url', 'u', 'r', 'redirect', 'redirect_uri', 'redirect_url', 'next', 'link', 'goto', 'dest', 'destination', 'target', 'continue', 'return', 'returnurl']);

export function analyzeLink(raw) {
  const parsed = parseUrl(raw);
  if (!parsed) return assess([finding('URL_PARSE_FAILED')], 'URL', { recommendations: [Rec.DONT_OPEN_LINK] });
  return analyzeParsed(parsed, 0);
}

export function analyzeParsed(url, depth = 0) {
  const f = [];
  if (dangerousSchemes.has(url.scheme)) {
    f.push(finding('URL_DANGEROUS_SCHEME', `${url.scheme}:`));
    return assess(f, 'URL', { recommendations: [Rec.DONT_OPEN_LINK] });
  }
  if (url.host.length === 0) return assess([finding('URL_PARSE_FAILED')], 'URL');
  const { host, unicodeHost } = url;
  const registrable = registrableDomain(unicodeHost);
  const official = isOfficial(registrable);

  if (url.schemeWasMissing) f.push(finding('URL_NO_SCHEME'));
  if (url.scheme === 'http') f.push(finding('URL_NO_HTTPS'));
  if (url.userInfo !== null) f.push(finding('URL_USER_INFO', url.userInfo.slice(0, 40)));
  if (url.hostWasPercentEncoded) f.push(finding('URL_ENCODED_HOST'));
  const isIp = isIpAddress(host);
  if (isIp) f.push(finding('URL_IP_HOST', host));
  if (url.port !== null && url.port !== 80 && url.port !== 443) f.push(finding('URL_NONSTANDARD_PORT', String(url.port)));

  if (!isIp) {
    if (host.split('.').some((l) => l.startsWith('xn--'))) f.push(finding('URL_INTERNATIONALIZED', unicodeHost));
    if (hasMixedScript(unicodeHost)) f.push(finding('URL_MIXED_SCRIPT', unicodeHost));
    const named = impersonatedBrands(unicodeHost);
    for (const b of named) f.push(finding('URL_BRAND_IMPERSONATION', b));
    if (named.length === 0) {
      const look = lookalikeOf(unicodeHost) ?? impersonatedBrands(unicodeHost, true)[0] ?? null;
      if (look !== null) f.push(finding('URL_LOOKALIKE_DOMAIN', look));
    }
    if (isShortener(host)) f.push(finding('URL_SHORTENER', host));
    const free = freeHostingSuffix(host);
    if (free !== null) f.push(finding('URL_FREE_HOSTING', free));
    if (suspiciousTlds.has(tld(host))) f.push(finding('URL_SUSPICIOUS_TLD', `.${tld(host)}`));
    let sub = unicodeHost.endsWith(registrable) ? unicodeHost.slice(0, unicodeHost.length - registrable.length) : unicodeHost;
    while (sub.endsWith('.')) sub = sub.slice(0, -1);
    const subdomainLabels = sub.split('.').filter((l) => l.length > 0 && l !== 'www');
    if (subdomainLabels.length >= 3) f.push(finding('URL_MANY_SUBDOMAINS', String(subdomainLabels.length)));
    if ((host.match(/-/g) ?? []).length >= 3) f.push(finding('URL_MANY_HYPHENS'));
  }

  const keywords = official ? [] : sensitiveKeywords(host, url.path);
  for (const k of keywords.slice(0, 2)) f.push(finding('URL_SENSITIVE_KEYWORD', k));
  const keywordsInHost = official || isIp ? 0 : sensitiveKeywords(host, '').length;

  const path = url.path.toLowerCase();
  if (path.endsWith('.apk') || path.includes('.apk/')) f.push(finding('URL_APK_DOWNLOAD'));
  const target = embeddedRedirect(url);
  if (target) {
    const extra = depth === 0 ? idiv(analyzeParsed(target, depth + 1).score * 6, 10) : 0;
    f.push(finding('URL_EMBEDDED_REDIRECT', target.host, null, extra));
  }
  if (url.original.length > 150) f.push(finding('URL_EXCESSIVE_LENGTH'));

  const has = (...codes) => f.some((x) => codes.includes(x.code));
  const bonus = has('URL_BRAND_IMPERSONATION', 'URL_LOOKALIKE_DOMAIN') && has('URL_SENSITIVE_KEYWORD', 'URL_SUSPICIOUS_TLD', 'URL_FREE_HOSTING') ? 10 : 0;
  const keywordBonus = keywordsInHost >= 2 ? 10 : 0;
  const recs = [];
  const raw = f.reduce((s, x) => s + weightOf(x.code) + x.extraWeight, 0) + bonus + keywordBonus;
  if (raw >= 30) recs.push(Rec.DONT_OPEN_LINK);
  if (has('URL_BRAND_IMPERSONATION', 'URL_LOOKALIKE_DOMAIN')) recs.push(Rec.VERIFY_OFFICIAL_CHANNEL);
  if (has('URL_APK_DOWNLOAD')) recs.push(Rec.NO_REMOTE_APPS);
  if (recs.length === 0) recs.push(Rec.STAY_CAUTIOUS);
  return assess(f, 'URL', { bonus: bonus + keywordBonus, recommendations: recs });
}

function sensitiveKeywords(host, path) {
  const all = [...host.split(/[.\-_]/), ...path.toLowerCase().split(/[/\-_.?=&]/)].filter((p) => p.length > 0);
  const out = [];
  for (const part of all) {
    const k = sensitiveExact.has(part) ? part : sensitiveWords.find((w) => part.includes(w)) ?? null;
    if (k !== null && !out.includes(k)) out.push(k);
  }
  return out;
}

function embeddedRedirect(url) {
  if (url.query === null) return null;
  for (const pair of url.query.split('&')) {
    const eq = pair.indexOf('=');
    const key = (eq < 0 ? pair : pair.slice(0, eq)).toLowerCase();
    if (!redirectParams.has(key)) continue;
    const value = tryUrlDecode(eq < 0 ? '' : pair.slice(eq + 1));
    if (value === null) continue;
    const lower = value.toLowerCase();
    if (!lower.startsWith('http://') && !lower.startsWith('https://') && !value.startsWith('//')) continue;
    const target = parseUrl(value.startsWith('//') ? `https:${value}` : value);
    if (!target) continue;
    if (registrableDomain(target.host) !== registrableDomain(url.host)) return target;
  }
  return null;
}
