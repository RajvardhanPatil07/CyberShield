// Port of android/.../security/Model.kt. Keep weights and order identical to the Kotlin enum.

export const RiskLevel = { LOW: 'LOW', MEDIUM: 'MEDIUM', HIGH: 'HIGH' };
export const levelOf = (score) => (score >= 60 ? 'HIGH' : score >= 30 ? 'MEDIUM' : 'LOW');

/** code → [weight, canonical English explanation] */
export const FINDINGS = {
  URGENCY: [20, 'Uses urgency or pressure language to rush you.'],
  ACCOUNT_THREAT: [25, 'Threatens that an account, card or SIM will be blocked or suspended.'],
  SERVICE_DISCONNECTION: [25, 'Threatens to cut electricity, gas or mobile service unless you act now.'],
  OTP_REQUEST: [40, 'Asks you to share an OTP. Banks and UPI apps never ask for it; OTPs and other payment secrets must never be shared.'],
  UPI_PIN_REQUEST: [45, 'Asks for a UPI PIN or MPIN. These payment secrets are only typed into your own UPI app to send money.'],
  CARD_DETAILS_REQUEST: [40, 'Asks for card number, CVV or expiry date. These payment secrets must never be shared.'],
  PASSWORD_REQUEST: [35, 'Asks for a password or net-banking login. Never share credentials or payment secrets.'],
  SECRET_MENTION: [10, 'Mentions an OTP, PIN, CVV or password.'],
  RECEIVE_MONEY_TRICK: [45, 'Claims you must enter a PIN, scan a QR code or approve a request to RECEIVE money. UPI PINs are only needed to send money.'],
  PAYMENT_REQUEST: [15, 'Pushes you to make or approve a payment.'],
  KYC_UPDATE: [20, 'Asks you to update or verify KYC, PAN or Aadhaar details.'],
  REWARD_LURE: [20, 'Promises a prize, lottery, reward or cashback.'],
  REFUND_LURE: [15, 'Offers a refund that you must claim.'],
  LOAN_LURE: [15, 'Advertises an instant or pre-approved loan.'],
  JOB_TASK_SCAM: [25, 'Offers easy work-from-home or task-based income, a common prepaid-task scam.'],
  INVESTMENT_LURE: [25, 'Promises guaranteed or unusually high investment returns.'],
  FEE_REQUEST: [15, 'Asks for an upfront fee, deposit or charge.'],
  AUTHORITY_IMPERSONATION: [25, "Invokes police, CBI, customs, courts or other authorities, a pattern used in 'digital arrest' scams."],
  REMOTE_ACCESS_APP: [35, 'Asks you to install screen-sharing or remote-access apps such as AnyDesk or TeamViewer.'],
  APK_LURE: [35, 'Asks you to install an APK file from outside the Play Store.'],
  SECRECY: [20, 'Asks you to keep the conversation secret or stay on the call.'],
  CALLBACK_REQUEST: [10, 'Asks you to call or message a number given in the text.'],
  CONTAINS_LINK: [10, 'Contains a link; its destination was checked separately.'],
  SUSPICIOUS_LINK: [0, 'Contains a link with risky characteristics.'],

  URL_PARSE_FAILED: [55, 'Could not parse a complete URL with a host.'],
  URL_DANGEROUS_SCHEME: [70, 'Uses a scheme (javascript:, data:, intent:, file:) that can run code or open apps directly.'],
  URL_NO_SCHEME: [5, 'The address has no scheme; it was checked as https://.'],
  URL_NO_HTTPS: [20, 'The address does not use HTTPS, so the connection is not encrypted.'],
  URL_USER_INFO: [45, 'The URL embeds user-info before the host, which can disguise its real destination.'],
  URL_IP_HOST: [30, 'The host is an IP address rather than a conventional domain.'],
  URL_ENCODED_HOST: [30, 'The hostname contains percent-encoded characters.'],
  URL_INTERNATIONALIZED: [20, 'The domain uses international (punycode) characters; verify its spelling carefully.'],
  URL_MIXED_SCRIPT: [40, 'The domain mixes alphabets (for example Latin and Cyrillic), a homograph trick.'],
  URL_BRAND_IMPERSONATION: [45, 'Mentions a well-known brand, but the real domain is not owned by that brand.'],
  URL_LOOKALIKE_DOMAIN: [45, 'The domain looks like a misspelling of a well-known brand.'],
  URL_SHORTENER: [15, 'Uses a link shortener that hides the final destination.'],
  URL_FREE_HOSTING: [15, 'Hosted on a free website or tunnelling service often used for throwaway phishing pages.'],
  URL_SUSPICIOUS_TLD: [15, 'Uses a domain ending that is frequently abused for scams.'],
  URL_SENSITIVE_KEYWORD: [15, 'Contains words such as login, verify or KYC that are common in impersonation links.'],
  URL_MANY_SUBDOMAINS: [15, 'Has an unusually long chain of subdomains.'],
  URL_MANY_HYPHENS: [10, 'The hostname contains several hyphens.'],
  URL_NONSTANDARD_PORT: [15, 'Uses a non-standard network port.'],
  URL_APK_DOWNLOAD: [35, 'Links directly to an Android APK download.'],
  URL_EMBEDDED_REDIRECT: [20, 'Contains another web address inside it, which can redirect you elsewhere.'],
  URL_EXCESSIVE_LENGTH: [5, 'The address is unusually long.'],

  QR_EMPTY: [40, 'The QR code is empty or unreadable.'],
  QR_TEXT_ONLY: [0, 'The QR code contains plain text, not a link or payment.'],
  QR_OTHER_ACTION: [10, 'The QR code triggers an action (call, SMS, e-mail, Wi-Fi or contact).'],
  UPI_PAYMENT: [0, 'This is a UPI payment QR. Scanning it and entering your PIN SENDS money from your account.'],
  UPI_MISSING_PAYEE: [60, 'The UPI request has no valid payee address.'],
  UPI_INVALID_VPA: [40, 'The payee UPI ID is malformed.'],
  UPI_COLLECT_REQUEST: [35, 'This is a collect request: approving it takes money out of your account.'],
  UPI_LARGE_AMOUNT: [20, 'The pre-filled amount is large.'],
  UPI_FIXED_AMOUNT: [5, 'The amount is pre-filled; check it before paying.'],
  UPI_SUSPICIOUS_NOTE: [40, 'The payee name or note mentions refunds, prizes, KYC or receiving money.'],
  UPI_IMPERSONATION_NAME: [30, 'The payee name claims to be a bank, government body or customer-care desk.'],
  UPI_NON_INR: [25, 'The payment currency is not INR.'],
  UPI_UNKNOWN_HANDLE: [10, 'The UPI handle is not one of the common bank or app handles.'],
};

export const weightOf = (code) => FINDINGS[code][0];

/** A finding. start/end (end exclusive) locate the phrase in the analysed text, when known. */
export const finding = (code, evidence = null, range = null, extraWeight = 0) => ({
  code,
  evidence,
  start: range ? range.start : null,
  end: range ? range.end : null,
  extraWeight,
});

export const Rec = {
  NEVER_SHARE_SECRETS: 'NEVER_SHARE_SECRETS',
  PIN_ONLY_TO_PAY: 'PIN_ONLY_TO_PAY',
  DONT_OPEN_LINK: 'DONT_OPEN_LINK',
  VERIFY_OFFICIAL_CHANNEL: 'VERIFY_OFFICIAL_CHANNEL',
  NO_REMOTE_APPS: 'NO_REMOTE_APPS',
  NO_UPFRONT_FEES: 'NO_UPFRONT_FEES',
  NO_DIGITAL_ARREST: 'NO_DIGITAL_ARREST',
  CHECK_PAYEE: 'CHECK_PAYEE',
  REPORT_1930: 'REPORT_1930',
  REPORT_CHAKSHU: 'REPORT_CHAKSHU',
  STAY_CAUTIOUS: 'STAY_CAUTIOUS',
};

/** Builds an assessment from findings: weights are summed and clamped to 0–100 (RiskAssessment.from). */
export function assess(findings, kind, { bonus = 0, recommendations = [], language = 'UNKNOWN', links = [] } = {}) {
  const seen = new Set();
  const unique = [];
  for (const f of findings) {
    const key = `${f.code}\u0000${f.evidence === null ? '\u0001null' : f.evidence}`;
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(f);
  }
  const groups = new Map();
  for (const f of unique) {
    if (!groups.has(f.code)) groups.set(f.code, []);
    groups.get(f.code).push(f);
  }
  let sum = 0;
  for (const [code, items] of groups) {
    const w = weightOf(code);
    const intensity = w > 0 ? Math.min(10, 5 * (items.length - 1)) : 0;
    sum += w + intensity + Math.max(...items.map((i) => i.extraWeight));
  }
  const score = Math.min(100, Math.max(0, sum + bonus));
  return {
    score,
    level: levelOf(score),
    findings: unique,
    recommendations: [...new Set(recommendations)],
    kind,
    language,
    links,
  };
}
