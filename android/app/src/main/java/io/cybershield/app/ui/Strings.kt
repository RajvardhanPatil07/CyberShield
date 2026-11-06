package io.cybershield.app.ui

import androidx.annotation.StringRes
import io.cybershield.app.R
import io.cybershield.app.cloud.ProviderStatus
import io.cybershield.app.security.DetectedLanguage
import io.cybershield.app.security.FindingCode
import io.cybershield.app.security.FindingCode.*
import io.cybershield.app.security.QrContentType
import io.cybershield.app.security.Recommendation
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScanKind

/** Exhaustive mappings: adding an engine code without a translated string fails compilation. */
@StringRes
fun FindingCode.label(): Int = when (this) {
    URGENCY -> R.string.f_urgency
    ACCOUNT_THREAT -> R.string.f_account_threat
    SERVICE_DISCONNECTION -> R.string.f_service_disconnection
    OTP_REQUEST -> R.string.f_otp_request
    UPI_PIN_REQUEST -> R.string.f_upi_pin_request
    CARD_DETAILS_REQUEST -> R.string.f_card_details_request
    PASSWORD_REQUEST -> R.string.f_password_request
    SECRET_MENTION -> R.string.f_secret_mention
    RECEIVE_MONEY_TRICK -> R.string.f_receive_money_trick
    PAYMENT_REQUEST -> R.string.f_payment_request
    KYC_UPDATE -> R.string.f_kyc_update
    REWARD_LURE -> R.string.f_reward_lure
    REFUND_LURE -> R.string.f_refund_lure
    LOAN_LURE -> R.string.f_loan_lure
    JOB_TASK_SCAM -> R.string.f_job_task_scam
    INVESTMENT_LURE -> R.string.f_investment_lure
    FEE_REQUEST -> R.string.f_fee_request
    AUTHORITY_IMPERSONATION -> R.string.f_authority_impersonation
    REMOTE_ACCESS_APP -> R.string.f_remote_access_app
    APK_LURE -> R.string.f_apk_lure
    SECRECY -> R.string.f_secrecy
    CALLBACK_REQUEST -> R.string.f_callback_request
    CONTAINS_LINK -> R.string.f_contains_link
    SUSPICIOUS_LINK -> R.string.f_suspicious_link
    ML_PATTERN_MATCH -> R.string.f_ml_pattern_match
    URL_PARSE_FAILED -> R.string.f_url_parse_failed
    URL_DANGEROUS_SCHEME -> R.string.f_url_dangerous_scheme
    URL_NO_SCHEME -> R.string.f_url_no_scheme
    URL_NO_HTTPS -> R.string.f_url_no_https
    URL_USER_INFO -> R.string.f_url_user_info
    URL_IP_HOST -> R.string.f_url_ip_host
    URL_ENCODED_HOST -> R.string.f_url_encoded_host
    URL_INTERNATIONALIZED -> R.string.f_url_internationalized
    URL_MIXED_SCRIPT -> R.string.f_url_mixed_script
    URL_BRAND_IMPERSONATION -> R.string.f_url_brand_impersonation
    URL_LOOKALIKE_DOMAIN -> R.string.f_url_lookalike_domain
    URL_SHORTENER -> R.string.f_url_shortener
    URL_FREE_HOSTING -> R.string.f_url_free_hosting
    URL_SUSPICIOUS_TLD -> R.string.f_url_suspicious_tld
    URL_SENSITIVE_KEYWORD -> R.string.f_url_sensitive_keyword
    URL_MANY_SUBDOMAINS -> R.string.f_url_many_subdomains
    URL_MANY_HYPHENS -> R.string.f_url_many_hyphens
    URL_NONSTANDARD_PORT -> R.string.f_url_nonstandard_port
    URL_APK_DOWNLOAD -> R.string.f_url_apk_download
    URL_EMBEDDED_REDIRECT -> R.string.f_url_embedded_redirect
    URL_EXCESSIVE_LENGTH -> R.string.f_url_excessive_length
    QR_EMPTY -> R.string.f_qr_empty
    QR_TEXT_ONLY -> R.string.f_qr_text_only
    QR_OTHER_ACTION -> R.string.f_qr_other_action
    UPI_PAYMENT -> R.string.f_upi_payment
    UPI_MISSING_PAYEE -> R.string.f_upi_missing_payee
    UPI_INVALID_VPA -> R.string.f_upi_invalid_vpa
    UPI_COLLECT_REQUEST -> R.string.f_upi_collect_request
    UPI_LARGE_AMOUNT -> R.string.f_upi_large_amount
    UPI_FIXED_AMOUNT -> R.string.f_upi_fixed_amount
    UPI_SUSPICIOUS_NOTE -> R.string.f_upi_suspicious_note
    UPI_IMPERSONATION_NAME -> R.string.f_upi_impersonation_name
    UPI_NON_INR -> R.string.f_upi_non_inr
    UPI_UNKNOWN_HANDLE -> R.string.f_upi_unknown_handle
}

@StringRes
fun Recommendation.label(): Int = when (this) {
    Recommendation.NEVER_SHARE_SECRETS -> R.string.rec_never_share_secrets
    Recommendation.PIN_ONLY_TO_PAY -> R.string.rec_pin_only_to_pay
    Recommendation.DONT_OPEN_LINK -> R.string.rec_dont_open_link
    Recommendation.VERIFY_OFFICIAL_CHANNEL -> R.string.rec_verify_official_channel
    Recommendation.NO_REMOTE_APPS -> R.string.rec_no_remote_apps
    Recommendation.NO_UPFRONT_FEES -> R.string.rec_no_upfront_fees
    Recommendation.NO_DIGITAL_ARREST -> R.string.rec_no_digital_arrest
    Recommendation.CHECK_PAYEE -> R.string.rec_check_payee
    Recommendation.REPORT_1930 -> R.string.rec_report_1930
    Recommendation.REPORT_CHAKSHU -> R.string.rec_report_chakshu
    Recommendation.STAY_CAUTIOUS -> R.string.rec_stay_cautious
}

@StringRes
fun RiskLevel.label(): Int = when (this) {
    RiskLevel.LOW -> R.string.level_low
    RiskLevel.MEDIUM -> R.string.level_medium
    RiskLevel.HIGH -> R.string.level_high
}

@StringRes
fun ScanKind.label(): Int = when (this) {
    ScanKind.MESSAGE -> R.string.kind_message
    ScanKind.URL -> R.string.kind_url
    ScanKind.QR -> R.string.kind_qr
    ScanKind.SCREENSHOT -> R.string.kind_screenshot
}

@StringRes
fun DetectedLanguage.label(): Int = when (this) {
    DetectedLanguage.ENGLISH -> R.string.lang_detect_en
    DetectedLanguage.HINDI -> R.string.lang_detect_hi
    DetectedLanguage.MARATHI -> R.string.lang_detect_mr
    DetectedLanguage.ROMANISED_HINDI -> R.string.lang_detect_hinglish
    DetectedLanguage.DEVANAGARI_UNKNOWN -> R.string.lang_detect_devanagari
    DetectedLanguage.MIXED -> R.string.lang_detect_mixed
    DetectedLanguage.UNKNOWN -> R.string.lang_detect_unknown
}

/** Language code sent with a donated sample. Matches the backend's accepted contribution languages. */
fun DetectedLanguage.contributionCode(): String = when (this) {
    DetectedLanguage.ENGLISH -> "en"
    DetectedLanguage.HINDI -> "hi"
    DetectedLanguage.MARATHI -> "mr"
    DetectedLanguage.ROMANISED_HINDI -> "hinglish"
    DetectedLanguage.DEVANAGARI_UNKNOWN, DetectedLanguage.MIXED -> "mixed"
    DetectedLanguage.UNKNOWN -> "unknown"
}

@StringRes
fun QrContentType.label(): Int = when (this) {
    QrContentType.WEB_URL -> R.string.qr_type_web
    QrContentType.UPI_PAYMENT -> R.string.qr_type_upi
    QrContentType.PHONE -> R.string.qr_type_phone
    QrContentType.SMS -> R.string.qr_type_sms
    QrContentType.EMAIL -> R.string.qr_type_email
    QrContentType.WIFI -> R.string.qr_type_wifi
    QrContentType.CONTACT -> R.string.qr_type_contact
    QrContentType.LOCATION -> R.string.qr_type_location
    QrContentType.APP_LINK -> R.string.qr_type_app
    QrContentType.TEXT -> R.string.qr_type_text
    QrContentType.INVALID -> R.string.qr_type_invalid
}

@StringRes
fun ProviderStatus.label(): Int = when (this) {
    ProviderStatus.MALICIOUS -> R.string.provider_status_malicious
    ProviderStatus.SUSPICIOUS -> R.string.provider_status_suspicious
    ProviderStatus.CLEAN -> R.string.provider_status_clean
    ProviderStatus.UNKNOWN -> R.string.provider_status_unknown
    ProviderStatus.ERROR -> R.string.provider_status_error
    ProviderStatus.NOT_CONFIGURED -> R.string.provider_status_not_configured
    ProviderStatus.RATE_LIMITED -> R.string.provider_status_rate_limited
}

/** Human-readable provider names. These are product names and are not translated. */
fun providerName(id: String): String = when (id) {
    "google_safe_browsing" -> "Google Safe Browsing"
    "virustotal" -> "VirusTotal"
    "urlhaus" -> "URLhaus (abuse.ch)"
    else -> id
}
