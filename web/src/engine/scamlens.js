// Port of android/.../security/ScamLens.kt: offline message analysis in English, Hindi, Marathi and romanised Hindi.
import { extractUrls, parseUrl } from './domain.js';
import { distinct, escapeRegex, find, findAll, idiv, isLetter, isLetterOrDigit, lowerCharwise } from './kt.js';
import { analyzeLink } from './linkguard.js';
import { assess, finding, levelOf, Rec } from './model.js';
import * as P from './phrases.js';

export const MAX_INPUT = 10_000;
// Java's \s (no UNICODE_CHARACTER_CLASS) is ASCII whitespace only.
const WS = '[ \\t\\n\\x0B\\f\\r]';
const isDevanagari = (c) => /\p{Script=Devanagari}/u.test(c);

function phraseRegex(phrases) {
  const sorted = distinct(phrases).sort((a, b) => b.length - a.length);
  const body = sorted
    .map((phrase) => {
      const words = phrase.toLowerCase().trim().split(/\s+/).map(escapeRegex).join(`${WS}+`);
      const first = phrase[0];
      const last = phrase[phrase.length - 1];
      const devanagari = phrase.split('').some(isDevanagari);
      const lead = isLetterOrDigit(first) ? '(?<![\\p{L}\\p{M}\\p{N}])' : '';
      // Devanagari phrases may carry inflection suffixes (दें → देंगे), so no trailing boundary.
      const trail = isLetterOrDigit(last) && !devanagari ? '(?![\\p{L}\\p{N}])' : '';
      return `${lead}${words}${trail}`;
    })
    .join('|');
  return new RegExp(`(?:${body})`, 'gu');
}

let compiled = null;
function patterns() {
  if (compiled) return compiled;
  const merged = new Map();
  for (const dict of [P.english, P.hinglish, P.hindi, P.marathi]) {
    for (const [code, list] of Object.entries(dict)) {
      if (!merged.has(code)) merged.set(code, []);
      merged.get(code).push(...list);
    }
  }
  compiled = {
    categories: [...merged].map(([code, list]) => [code, phraseRegex(list)]),
    secrets: Object.entries(P.secrets).map(([code, list]) => [code, phraseRegex(list)]),
    verbs: phraseRegex(P.requestVerbs),
    notices: phraseRegex(P.safetyNotices),
  };
  return compiled;
}

const receiveTricks = [
  /(?:pin|scan|qr|approve|accept)[^.\n]{0,40}(?:receive|get|credit|claim)[^.\n]{0,25}(?:money|amount|payment|cashback|refund|rs\b|₹|prize)/,
  /(?:receive|get|claim)[^.\n]{0,25}(?:money|amount|payment|cashback|refund|prize|rs\b|₹)[^.\n]{0,40}(?:enter|scan|approve|accept)[^.\n]{0,25}(?:pin|qr|request)/,
  /(?:पैसे|राशि|रकम|कैशबैक|रिफंड)[^।.\n]{0,30}(?:पाने|प्राप्त|लेने)[^।.\n]{0,30}(?:पिन|स्कैन|क्यूआर|qr)/,
  /(?:पैसे|रक्कम|कॅशबॅक|परतावा)[^।.\n]{0,30}(?:मिळवण्यासाठी|मिळेल|मिळतील)[^।.\n]{0,30}(?:पिन|स्कॅन|क्यूआर|qr)/,
];

const marathiMarkers = new Set(['आहे', 'आहेत', 'तुमचे', 'तुमच्या', 'तुमचा', 'करा', 'आणि', 'नाही', 'होईल', 'मध्ये', 'आपले', 'येईल', 'झाले', 'केले', 'साठी', 'लगेच']);
const hindiMarkers = new Set(['है', 'हैं', 'आपका', 'आपके', 'आपकी', 'करें', 'और', 'नहीं', 'होगा', 'में', 'जाएगा', 'गया', 'लिए', 'कृपया', 'अपना']);
const hinglishMarkers = new Set(['hai', 'aapka', 'aapke', 'apna', 'karein', 'kare', 'karo', 'nahi', 'hoga', 'jayega', 'kijiye', 'turant', 'aur', 'mein', 'ko', 'ka', 'ki']);

/** Best-effort language guess from script and marker words. Not a trained classifier. */
export function detectLanguage(input) {
  // Links are Latin script whatever the message language, so they are not counted.
  let text = input;
  for (const r of extractUrls(input, 20).sort((a, b) => b.start - a.start)) text = text.slice(0, r.start) + ' ' + text.slice(r.end);
  let dev = 0;
  let latin = 0;
  for (const c of text.split('')) {
    if (!isLetter(c)) continue;
    if (isDevanagari(c)) dev++;
    else if (/\p{Script=Latin}/u.test(c)) latin++;
  }
  const total = dev + latin;
  if (total === 0) return 'UNKNOWN';
  const tokens = text.toLowerCase().split(/[ \t\n\x0B\f\r\p{P}।]+/u).filter((t) => t.length > 0);
  if (dev * 10 >= total * 7) {
    const mr = tokens.filter((t) => marathiMarkers.has(t)).length;
    const hi = tokens.filter((t) => hindiMarkers.has(t)).length;
    return mr > hi ? 'MARATHI' : hi > mr ? 'HINDI' : 'DEVANAGARI_UNKNOWN';
  }
  if (dev * 10 < total) return tokens.filter((t) => hinglishMarkers.has(t)).length >= 2 ? 'ROMANISED_HINDI' : 'ENGLISH';
  return 'MIXED';
}

const credentialCodes = new Set(['OTP_REQUEST', 'UPI_PIN_REQUEST', 'CARD_DETAILS_REQUEST', 'PASSWORD_REQUEST', 'RECEIVE_MONEY_TRICK']);
const pressureCodes = new Set(['URGENCY', 'ACCOUNT_THREAT', 'SERVICE_DISCONNECTION', 'AUTHORITY_IMPERSONATION', 'KYC_UPDATE', 'REWARD_LURE', 'REFUND_LURE']);
const linkLureCodes = new Set(['KYC_UPDATE', 'ACCOUNT_THREAT', 'REWARD_LURE', 'REFUND_LURE', 'SERVICE_DISCONNECTION', 'URGENCY', 'FEE_REQUEST']);
const advanceFeeLures = new Set(['REWARD_LURE', 'JOB_TASK_SCAM', 'LOAN_LURE', 'REFUND_LURE', 'INVESTMENT_LURE']);
/** "Pay a fee to release / withdraw / receive the money": the payout is the bait. */
const payoutPattern = /\b(?:to release|release the amount|to withdraw|to receive (?:the |your )?(?:amount|money|payment|prize|reward|refund|loan))/;

export function analyzeMessage(input, kind = 'MESSAGE') {
  const text = input.slice(0, MAX_INPUT);
  // Char-by-char lowercasing keeps indices aligned with the original for highlighting.
  const lower = lowerCharwise(text);
  const p = patterns();
  const findings = [];

  for (const [code, regex] of p.categories) {
    for (const m of findAll(regex, lower).slice(0, 3)) findings.push(finding(code, text.slice(m.start, m.end), m));
  }

  // Payment secrets: distinguish "send me your OTP" from "do not share your OTP".
  const notices = findAll(p.notices, lower);
  // Kotlin ranges are inclusive: v.last <= n.last + 1  ⇔  v.end <= n.end + 1.
  const verbs = findAll(p.verbs, lower).filter((v) => !notices.some((n) => v.start >= n.start && v.end <= n.end + 1));
  for (const [code, regex] of p.secrets) {
    const mentions = findAll(regex, lower);
    if (mentions.length === 0) continue;
    const requested = mentions.filter((m) => verbs.some((v) => Math.abs(v.start - m.start) <= 80));
    if (requested.length > 0) {
      for (const m of requested.slice(0, 2)) findings.push(finding(code, text.slice(m.start, m.end), m));
    } else if (notices.length === 0) {
      findings.push(finding('SECRET_MENTION', text.slice(mentions[0].start, mentions[0].end), mentions[0]));
    }
  }

  for (const regex of receiveTricks) {
    const m = find(regex, lower);
    if (m) findings.push(finding('RECEIVE_MONEY_TRICK', text.slice(m.start, m.end).slice(0, 80), m));
  }

  // Links: analyse each one with LinkGuard.
  const links = extractUrls(text).map((l) => ({ ...l, result: analyzeLink(l.url) }));
  if (links.length > 0) {
    findings.push(finding('CONTAINS_LINK', links[0].url.slice(0, 60), links[0]));
    for (const l of links.filter((x) => x.result.score >= 30)) {
      const host = parseUrl(l.url)?.host ?? l.url.slice(0, 60);
      findings.push(finding('SUSPICIOUS_LINK', host, l, idiv(l.result.score * 6, 10)));
    }
  }

  const codes = new Set(findings.map((f) => f.code));
  const any = (set) => [...codes].some((c) => set.has(c));
  let bonus = 0;
  if (any(credentialCodes) && any(pressureCodes)) bonus += 15;
  if (codes.has('CONTAINS_LINK') && any(linkLureCodes)) bonus += 15;
  if (codes.has('FEE_REQUEST') && (any(advanceFeeLures) || payoutPattern.test(lower))) bonus += 20;
  if (codes.has('AUTHORITY_IMPERSONATION') && (codes.has('SECRECY') || codes.has('PAYMENT_REQUEST'))) bonus += 15;

  const result = assess(findings, kind, {
    bonus,
    recommendations: recommendationsFor(codes, any),
    language: detectLanguage(text),
    links: links.map((l) => ({ url: l.url, start: l.start, end: l.end, result: l.result })),
  });
  if (levelOf(result.score) === 'LOW' && result.recommendations.length === 0) result.recommendations = [Rec.STAY_CAUTIOUS];
  return result;
}

function recommendationsFor(codes, any) {
  const out = [];
  if (any(credentialCodes) || codes.has('SECRET_MENTION')) out.push(Rec.NEVER_SHARE_SECRETS);
  if (codes.has('RECEIVE_MONEY_TRICK') || codes.has('PAYMENT_REQUEST')) out.push(Rec.PIN_ONLY_TO_PAY);
  if (codes.has('SUSPICIOUS_LINK') || (codes.has('CONTAINS_LINK') && any(linkLureCodes))) out.push(Rec.DONT_OPEN_LINK);
  if (codes.has('REMOTE_ACCESS_APP') || codes.has('APK_LURE')) out.push(Rec.NO_REMOTE_APPS);
  if (codes.has('AUTHORITY_IMPERSONATION')) out.push(Rec.NO_DIGITAL_ARREST);
  if (['FEE_REQUEST', 'REWARD_LURE', 'JOB_TASK_SCAM', 'LOAN_LURE', 'INVESTMENT_LURE'].some((c) => codes.has(c))) out.push(Rec.NO_UPFRONT_FEES);
  if (['KYC_UPDATE', 'ACCOUNT_THREAT', 'SERVICE_DISCONNECTION', 'REFUND_LURE'].some((c) => codes.has(c))) out.push(Rec.VERIFY_OFFICIAL_CHANNEL);
  if (out.length > 0) out.push(Rec.REPORT_CHAKSHU, Rec.REPORT_1930);
  return out;
}
