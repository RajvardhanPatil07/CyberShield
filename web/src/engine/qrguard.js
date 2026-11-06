// Port of android/.../security/QrGuard.kt: classifies QR payloads and checks UPI payment requests.
import { parseUrl } from './domain.js';
import { escapeRegex, trimEndChars, trimWs, tryUrlDecode } from './kt.js';
import { analyzeLink } from './linkguard.js';
import { assess, finding, Rec } from './model.js';
import { analyzeMessage } from './scamlens.js';

const vpa = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}$/;

/** Common bank and app UPI handles. An unknown handle is a weak signal only. */
export const knownHandles = new Set([
  'ybl', 'ibl', 'axl', 'okaxis', 'okhdfcbank', 'okicici', 'oksbi', 'paytm', 'ptyes', 'ptaxis', 'pthdfc', 'ptsbi', 'apl',
  'yapl', 'rapl', 'upi', 'sbi', 'hdfcbank', 'icici', 'axisbank', 'kotak', 'kmbl', 'yesbank', 'yesbankltd', 'boi',
  'pnb', 'barodampay', 'unionbank', 'cnrb', 'indus', 'federal', 'idfcbank', 'idfcfirst', 'aubank', 'rbl', 'jupiteraxis',
  'fam', 'slc', 'superyes', 'naviaxis', 'waicici', 'waaxis', 'wahdfcbank', 'wasbi', 'freecharge', 'airtel', 'jio', 'amazonpay',
  'dbs', 'citi', 'hsbc', 'sc', 'ikwik', 'mahb', 'centralbank', 'uco', 'allbank', 'indianbank', 'iob', 'psb', 'kbl', 'kvb',
  'tjsb', 'cosb', 'dlb', 'equitas', 'ujjivan', 'pingpay', 'abfspay', 'timecosmos', 'postbank', 'goaxb', 'okbizaxis',
]);
const impersonationNames = [
  'bank', 'rbi', 'reserve bank', 'npci', 'customer care', 'customer support', 'helpdesk', 'help desk', 'support team',
  'government', 'govt', 'income tax', 'police', 'cyber cell', 'electricity', 'kyc', 'refund desk', 'sbi', 'hdfc', 'icici',
];
const suspiciousNotes = [
  'refund', 'prize', 'lottery', 'cashback', 'kyc', 'receive', 'reward', 'winner', 'gift', 'verify', 'verification',
  'unblock', 'reactivate', 'penalty', 'fine', 'challan', 'रिफंड', 'इनाम', 'कैशबैक', 'बक्षीस', 'परतावा',
];

/** Result: { type, assessment, upi, openTarget, summary } */
export function analyzeQr(raw) {
  const content = raw == null ? '' : trimWs(raw);
  if (content.length === 0) return qr('INVALID', assess([finding('QR_EMPTY')], 'QR'), 'Empty QR');
  const lower = content.toLowerCase();
  if (lower.startsWith('upi:')) return analyzeUpi(content);
  if (lower.startsWith('http://') || lower.startsWith('https://') || lower.startsWith('www.')) return analyzeWeb(content);
  if (lower.startsWith('tel:')) return other('PHONE', 'Phone number');
  if (lower.startsWith('sms:') || lower.startsWith('smsto:')) return other('SMS', 'SMS');
  if (lower.startsWith('mailto:') || lower.startsWith('matmsg:')) return other('EMAIL', 'E-mail');
  if (lower.startsWith('wifi:')) return other('WIFI', 'Wi-Fi network');
  if (lower.startsWith('begin:vcard') || lower.startsWith('mecard:')) return other('CONTACT', 'Contact card');
  if (lower.startsWith('geo:')) return other('LOCATION', 'Location');
  if (lower.startsWith('market:')) return other('APP_LINK', 'Play Store link');
  if (/^(javascript|data|intent|file|content|vbscript):/.test(lower)) {
    const a = assess([finding('URL_DANGEROUS_SCHEME', lower.slice(0, lower.indexOf(':')) + ':')], 'QR', { recommendations: [Rec.DONT_OPEN_LINK] });
    return qr('APP_LINK', a, 'Executable link');
  }
  const parsed = parseUrl(content);
  if (parsed && parsed.host.includes('.') && !content.includes(' ')) return analyzeWeb(content);
  const text = analyzeMessage(content, 'QR');
  if (text.findings.length === 0) text.findings = [finding('QR_TEXT_ONLY')];
  return qr('TEXT', text, 'Text');
}

const qr = (type, assessment, summary, upi = null, openTarget = null) => ({ type, assessment, upi, openTarget, summary });

const other = (type, summary) =>
  qr(type, assess([finding('QR_OTHER_ACTION', summary)], 'QR', { recommendations: [Rec.STAY_CAUTIOUS] }), summary);

function analyzeWeb(content) {
  const parsed = parseUrl(content);
  const result = { ...analyzeLink(content), kind: 'QR' };
  const safeToOffer = parsed !== null && (parsed.scheme === 'http' || parsed.scheme === 'https') && parsed.host.length > 0;
  const target = safeToOffer ? (parsed.schemeWasMissing ? `https://${trimWs(content)}` : trimWs(content)) : null;
  return qr('WEB_URL', result, parsed?.host ?? 'Web link', null, target);
}

export function parseUpi(content) {
  let afterScheme = content.includes(':') ? content.slice(content.indexOf(':') + 1) : content;
  if (afterScheme.startsWith('//')) afterScheme = afterScheme.slice(2);
  const q = afterScheme.indexOf('?');
  const action = trimEndChars(q < 0 ? afterScheme : afterScheme.slice(0, q), '/').toLowerCase();
  if (action.length === 0) return null;
  const params = {};
  for (const pair of (q < 0 ? '' : afterScheme.slice(q + 1)).split('&')) {
    if (!pair.includes('=')) continue;
    const eq = pair.indexOf('=');
    const rawValue = pair.slice(eq + 1);
    params[pair.slice(0, eq).toLowerCase()] = trimWs(tryUrlDecode(rawValue) ?? rawValue);
  }
  const opt = (k) => (params[k] ? params[k] : null);
  const payeeAddress = opt('pa');
  const domain = payeeAddress && payeeAddress.includes('@') ? payeeAddress.slice(payeeAddress.indexOf('@') + 1) : '';
  return {
    action,
    payeeAddress,
    payeeName: opt('pn'),
    amount: opt('am'),
    currency: opt('cu'),
    note: opt('tn'),
    merchantCode: opt('mc'),
    /** Only the PSP handle, e.g. "@ybl". */
    handle: domain ? `@${domain}` : null,
  };
}

// java.math.BigDecimal(String) syntax.
const decimal = /^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$/;

function analyzeUpi(content) {
  const upi = parseUpi(content);
  if (!upi) return qr('UPI_PAYMENT', assess([finding('UPI_MISSING_PAYEE')], 'QR'), 'UPI');
  const f = [finding('UPI_PAYMENT')];
  const payee = upi.payeeAddress;
  if (payee === null) f.push(finding('UPI_MISSING_PAYEE'));
  else if (!vpa.test(payee)) f.push(finding('UPI_INVALID_VPA', payee.slice(0, 60)));
  else if (!knownHandles.has(payee.slice(payee.indexOf('@') + 1).toLowerCase())) f.push(finding('UPI_UNKNOWN_HANDLE', upi.handle));
  if (upi.action === 'collect' || upi.action === 'mandate') f.push(finding('UPI_COLLECT_REQUEST', upi.action));
  if (upi.amount !== null) {
    const large = decimal.test(upi.amount) && Number(upi.amount) >= 10_000;
    f.push(finding(large ? 'UPI_LARGE_AMOUNT' : 'UPI_FIXED_AMOUNT', `₹${upi.amount}`));
  }
  if (upi.currency !== null && upi.currency.toUpperCase() !== 'INR') f.push(finding('UPI_NON_INR', upi.currency));
  const nameAndNote = [upi.payeeName, upi.note].filter((x) => x !== null).join(' ').toLowerCase();
  const note = suspiciousNotes.find((n) => nameAndNote.includes(n));
  if (note) f.push(finding('UPI_SUSPICIOUS_NOTE', note));
  if (upi.payeeName !== null) {
    const name = upi.payeeName.toLowerCase();
    const hit = impersonationNames.find((n) => new RegExp(`(?<![a-z])${escapeRegex(n)}(?![a-z])`).test(name));
    if (hit) f.push(finding('UPI_IMPERSONATION_NAME', hit));
  }
  const recs = [Rec.CHECK_PAYEE, Rec.PIN_ONLY_TO_PAY];
  if (f.some((x) => ['UPI_SUSPICIOUS_NOTE', 'UPI_IMPERSONATION_NAME', 'UPI_COLLECT_REQUEST'].includes(x.code))) {
    recs.push(Rec.VERIFY_OFFICIAL_CHANNEL, Rec.REPORT_1930);
  }
  const valid = payee !== null && vpa.test(payee);
  return qr('UPI_PAYMENT', assess(f, 'QR', { recommendations: recs }), `UPI ${upi.handle ?? ''}`, upi,
    valid && upi.action === 'pay' ? trimWs(content) : null);
}
