package io.cybershield.app.security

enum class RiskLevel { LOW, MEDIUM, HIGH;

    companion object {
        fun of(score: Int): RiskLevel = when {
            score >= 60 -> HIGH
            score >= 30 -> MEDIUM
            else -> LOW
        }
    }
}

enum class ScanKind { MESSAGE, URL, QR, SCREENSHOT }

/**
 * Every signal the local engine can raise. [weight] feeds the 0–100 score; [english] is the canonical
 * explanation used in tests, logs-free history and backend prompts. The UI shows a localised string
 * for each code (see ui/Strings.kt), so English text never needs to be parsed.
 */
enum class FindingCode(val weight: Int, val english: String) {
    // Messages
    URGENCY(20, "Uses urgency or pressure language to rush you."),
    ACCOUNT_THREAT(25, "Threatens that an account, card or SIM will be blocked or suspended."),
    SERVICE_DISCONNECTION(25, "Threatens to cut electricity, gas or mobile service unless you act now."),
    OTP_REQUEST(40, "Asks you to share an OTP. Banks and UPI apps never ask for it; OTPs and other payment secrets must never be shared."),
    UPI_PIN_REQUEST(45, "Asks for a UPI PIN or MPIN. These payment secrets are only typed into your own UPI app to send money."),
    CARD_DETAILS_REQUEST(40, "Asks for card number, CVV or expiry date. These payment secrets must never be shared."),
    PASSWORD_REQUEST(35, "Asks for a password or net-banking login. Never share credentials or payment secrets."),
    SECRET_MENTION(10, "Mentions an OTP, PIN, CVV or password."),
    RECEIVE_MONEY_TRICK(45, "Claims you must enter a PIN, scan a QR code or approve a request to RECEIVE money. UPI PINs are only needed to send money."),
    PAYMENT_REQUEST(15, "Pushes you to make or approve a payment."),
    KYC_UPDATE(20, "Asks you to update or verify KYC, PAN or Aadhaar details."),
    REWARD_LURE(20, "Promises a prize, lottery, reward or cashback."),
    REFUND_LURE(15, "Offers a refund that you must claim."),
    LOAN_LURE(15, "Advertises an instant or pre-approved loan."),
    JOB_TASK_SCAM(25, "Offers easy work-from-home or task-based income, a common prepaid-task scam."),
    INVESTMENT_LURE(25, "Promises guaranteed or unusually high investment returns."),
    FEE_REQUEST(15, "Asks for an upfront fee, deposit or charge."),
    AUTHORITY_IMPERSONATION(25, "Invokes police, CBI, customs, courts or other authorities, a pattern used in 'digital arrest' scams."),
    REMOTE_ACCESS_APP(35, "Asks you to install screen-sharing or remote-access apps such as AnyDesk or TeamViewer."),
    APK_LURE(35, "Asks you to install an APK file from outside the Play Store."),
    SECRECY(20, "Asks you to keep the conversation secret or stay on the call."),
    CALLBACK_REQUEST(10, "Asks you to call or message a number given in the text."),
    CONTAINS_LINK(10, "Contains a link; its destination was checked separately."),
    SUSPICIOUS_LINK(0, "Contains a link with risky characteristics."),

    // URLs
    URL_PARSE_FAILED(55, "Could not parse a complete URL with a host."),
    URL_DANGEROUS_SCHEME(70, "Uses a scheme (javascript:, data:, intent:, file:) that can run code or open apps directly."),
    URL_NO_SCHEME(5, "The address has no scheme; it was checked as https://."),
    URL_NO_HTTPS(20, "The address does not use HTTPS, so the connection is not encrypted."),
    URL_USER_INFO(45, "The URL embeds user-info before the host, which can disguise its real destination."),
    URL_IP_HOST(30, "The host is an IP address rather than a conventional domain."),
    URL_ENCODED_HOST(30, "The hostname contains percent-encoded characters."),
    URL_INTERNATIONALIZED(20, "The domain uses international (punycode) characters; verify its spelling carefully."),
    URL_MIXED_SCRIPT(40, "The domain mixes alphabets (for example Latin and Cyrillic), a homograph trick."),
    URL_BRAND_IMPERSONATION(45, "Mentions a well-known brand, but the real domain is not owned by that brand."),
    URL_LOOKALIKE_DOMAIN(45, "The domain looks like a misspelling of a well-known brand."),
    URL_SHORTENER(15, "Uses a link shortener that hides the final destination."),
    URL_FREE_HOSTING(15, "Hosted on a free website or tunnelling service often used for throwaway phishing pages."),
    URL_SUSPICIOUS_TLD(15, "Uses a domain ending that is frequently abused for scams."),
    URL_SENSITIVE_KEYWORD(15, "Contains words such as login, verify or KYC that are common in impersonation links."),
    URL_MANY_SUBDOMAINS(15, "Has an unusually long chain of subdomains."),
    URL_MANY_HYPHENS(10, "The hostname contains several hyphens."),
    URL_NONSTANDARD_PORT(15, "Uses a non-standard network port."),
    URL_APK_DOWNLOAD(35, "Links directly to an Android APK download."),
    URL_EMBEDDED_REDIRECT(20, "Contains another web address inside it, which can redirect you elsewhere."),
    URL_EXCESSIVE_LENGTH(5, "The address is unusually long."),

    // QR / UPI
    QR_EMPTY(40, "The QR code is empty or unreadable."),
    QR_TEXT_ONLY(0, "The QR code contains plain text, not a link or payment."),
    QR_OTHER_ACTION(10, "The QR code triggers an action (call, SMS, e-mail, Wi-Fi or contact)."),
    UPI_PAYMENT(0, "This is a UPI payment QR. Scanning it and entering your PIN SENDS money from your account."),
    UPI_MISSING_PAYEE(60, "The UPI request has no valid payee address."),
    UPI_INVALID_VPA(40, "The payee UPI ID is malformed."),
    UPI_COLLECT_REQUEST(35, "This is a collect request: approving it takes money out of your account."),
    UPI_LARGE_AMOUNT(20, "The pre-filled amount is large."),
    UPI_FIXED_AMOUNT(5, "The amount is pre-filled; check it before paying."),
    UPI_SUSPICIOUS_NOTE(40, "The payee name or note mentions refunds, prizes, KYC or receiving money."),
    UPI_IMPERSONATION_NAME(30, "The payee name claims to be a bank, government body or customer-care desk."),
    UPI_NON_INR(25, "The payment currency is not INR."),
    UPI_UNKNOWN_HANDLE(10, "The UPI handle is not one of the common bank or app handles."),
}

data class Finding(
    val code: FindingCode,
    /** The phrase, host or value that triggered the finding, already safe to display. */
    val evidence: String? = null,
    /** Character range in the analysed text, used to highlight the phrase. */
    val range: IntRange? = null,
    /** Extra points beyond [FindingCode.weight], for example a linked URL's own score. */
    val extraWeight: Int = 0,
)

enum class Recommendation(val english: String) {
    NEVER_SHARE_SECRETS("Never share OTPs, UPI PINs, CVVs or passwords with anyone, including callers claiming to be from your bank."),
    PIN_ONLY_TO_PAY("You never need a UPI PIN or a QR scan to receive money. Decline the request."),
    DONT_OPEN_LINK("Do not open the link. Type the official website address yourself instead."),
    VERIFY_OFFICIAL_CHANNEL("Contact the organisation through its official app, website or the number printed on your card, not the contact in this message."),
    NO_REMOTE_APPS("Do not install apps or APK files that someone sends you. Uninstall any screen-sharing app a stranger asked you to install."),
    NO_UPFRONT_FEES("Genuine prizes, jobs and loans never ask you to pay a fee first."),
    NO_DIGITAL_ARREST("Police and government agencies do not arrest people on video calls or ask for money to close a case. Hang up."),
    CHECK_PAYEE("Check the payee name and amount in your UPI app before entering your PIN."),
    REPORT_1930("If money was lost, call 1930 or report at cybercrime.gov.in immediately."),
    REPORT_CHAKSHU("Report fraudulent calls, SMS or WhatsApp messages on the Sanchar Saathi 'Chakshu' portal."),
    STAY_CAUTIOUS("No strong red flags were found locally. Still verify the sender before acting."),
}

/** Best-effort language guess from script and marker words. Not a trained classifier. */
enum class DetectedLanguage { ENGLISH, HINDI, MARATHI, ROMANISED_HINDI, DEVANAGARI_UNKNOWN, MIXED, UNKNOWN }

data class RiskAssessment(
    val score: Int,
    val verdict: String,
    val reasons: List<String>,
    val disclaimer: String = "Heuristic result only. This is not proof that content is malicious.",
    val level: RiskLevel = RiskLevel.of(score),
    val findings: List<Finding> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val kind: ScanKind = ScanKind.MESSAGE,
    val language: DetectedLanguage = DetectedLanguage.UNKNOWN,
    /** URLs found inside the message, each with its own assessment. */
    val links: List<Pair<String, RiskAssessment>> = emptyList(),
) {
    companion object {
        fun verdictFor(level: RiskLevel) = when (level) {
            RiskLevel.HIGH -> "High risk signals"
            RiskLevel.MEDIUM -> "Caution advised"
            RiskLevel.LOW -> "No obvious red flags"
        }

        /** Builds an assessment from findings: weights are summed and clamped to 0–100. */
        fun from(
            findings: List<Finding>,
            kind: ScanKind,
            bonus: Int = 0,
            recommendations: List<Recommendation> = emptyList(),
            language: DetectedLanguage = DetectedLanguage.UNKNOWN,
            links: List<Pair<String, RiskAssessment>> = emptyList(),
        ): RiskAssessment {
            val distinct = findings.distinctBy { it.code to it.evidence }
            // Each code counts once; repeated distinct evidence adds up to +10 (5 per extra phrase).
            val perCode = distinct.groupBy { it.code }.map { (code, items) ->
                val intensity = if (code.weight > 0) minOf(10, 5 * (items.size - 1)) else 0
                code.weight + intensity + items.maxOf { it.extraWeight }
            }
            val score = (perCode.sum() + bonus).coerceIn(0, 100)
            val level = RiskLevel.of(score)
            return RiskAssessment(
                score = score,
                verdict = verdictFor(level),
                reasons = distinct.map { it.code }.distinct().map { it.english }
                    .ifEmpty { listOf("No obvious red flags were found by the local rules. This does not establish that it is safe.") },
                level = level,
                findings = distinct,
                recommendations = recommendations.distinct(),
                kind = kind,
                language = language,
                links = links,
            )
        }
    }
}
