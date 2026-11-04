package io.cybershield.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAnalyzerTest {
    private fun RiskAssessment.has(code: FindingCode) = findings.any { it.code == code }

    // ---- Original MVP tests (kept unchanged in intent) ----

    @Test
    fun flagsUrgentAccountThreat() {
        val result = RiskAnalyzer.message("Your account will be blocked today. Verify immediately.")
        assertTrue(result.score >= 25)
        assertTrue(result.reasons.any { it.contains("urgency") })
    }

    @Test
    fun flagsCredentialRequests() {
        val result = RiskAnalyzer.message("Send your OTP and UPI PIN to claim refund")
        assertTrue(result.reasons.any { it.contains("payment secrets") })
    }

    @Test
    fun flagsHttpAndSuspiciousHostTerms() {
        val result = RiskAnalyzer.url("http://kyc-verify-example.com/login")
        assertTrue(result.reasons.any { it.contains("HTTPS") })
        assertTrue(result.reasons.any { it.contains("impersonation") })
    }

    @Test
    fun flagsUserInfoHostConfusion() {
        val result = RiskAnalyzer.url("https://trusted.example@evil.example/path")
        assertTrue(result.reasons.any { it.contains("user-info") })
    }

    @Test
    fun rejectsAddressWithoutHost() {
        val result = RiskAnalyzer.url("not-a-valid-url")
        assertTrue(result.reasons.any { it.contains("Could not parse") })
    }

    // ---- Messages ----

    @Test
    fun urgentScamMessageIsHighRisk() {
        val result = RiskAnalyzer.message(
            "Dear customer, your SBI account will be blocked today. Update KYC immediately at http://sbi-kyc-update.xyz/login"
        )
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(result.has(FindingCode.ACCOUNT_THREAT))
        assertTrue(result.has(FindingCode.URGENCY))
        assertTrue(result.has(FindingCode.KYC_UPDATE))
        assertTrue(result.has(FindingCode.SUSPICIOUS_LINK))
        assertTrue(Recommendation.DONT_OPEN_LINK in result.recommendations)
    }

    @Test
    fun otpRequestIsFlagged() {
        val result = RiskAnalyzer.message("This is bank officer. Please share the OTP you received to stop the transaction.")
        assertTrue(result.has(FindingCode.OTP_REQUEST))
        assertTrue(result.score >= 40)
        assertTrue(Recommendation.NEVER_SHARE_SECRETS in result.recommendations)
    }

    @Test
    fun upiPinRequestIsFlagged() {
        val result = RiskAnalyzer.message("To receive Rs 5000 cashback enter your UPI PIN in the request we sent.")
        assertTrue(result.has(FindingCode.UPI_PIN_REQUEST))
        assertTrue(result.has(FindingCode.RECEIVE_MONEY_TRICK))
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(Recommendation.PIN_ONLY_TO_PAY in result.recommendations)
    }

    @Test
    fun fakeKycMessageIsFlagged() {
        val result = RiskAnalyzer.message("Your Paytm KYC has expired. Complete KYC within 24 hours or wallet will be blocked. Call 9876543210")
        assertTrue(result.has(FindingCode.KYC_UPDATE))
        assertTrue(result.has(FindingCode.ACCOUNT_THREAT))
        assertTrue(result.level != RiskLevel.LOW)
        assertTrue(Recommendation.VERIFY_OFFICIAL_CHANNEL in result.recommendations)
    }

    @Test
    fun rewardScamIsFlagged() {
        val result = RiskAnalyzer.message("Congratulations! You have won Rs 25,00,000 in KBC lucky draw. Pay registration fee of Rs 4,999 to claim your prize.")
        assertTrue(result.has(FindingCode.REWARD_LURE))
        assertTrue(result.has(FindingCode.FEE_REQUEST))
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(Recommendation.NO_UPFRONT_FEES in result.recommendations)
    }

    @Test
    fun digitalArrestScamIsFlagged() {
        val result = RiskAnalyzer.message("This is CBI. A parcel with drugs is in your name. Digital arrest warrant issued. Do not disconnect the call and do not tell your family.")
        assertTrue(result.has(FindingCode.AUTHORITY_IMPERSONATION))
        assertTrue(result.has(FindingCode.SECRECY))
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(Recommendation.NO_DIGITAL_ARREST in result.recommendations)
    }

    @Test
    fun remoteAccessScamIsFlagged() {
        val result = RiskAnalyzer.message("To get your refund please install AnyDesk and share the code with our executive")
        assertTrue(result.has(FindingCode.REMOTE_ACCESS_APP))
        assertTrue(result.has(FindingCode.REFUND_LURE))
    }

    @Test
    fun genuineOtpMessageIsLowRisk() {
        val result = RiskAnalyzer.message("482913 is your OTP for transaction of Rs 1,250.00 at AMAZON. Do not share it with anyone. -HDFC Bank")
        assertFalse(result.has(FindingCode.OTP_REQUEST))
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun normalMessageIsLowRisk() {
        val result = RiskAnalyzer.message("Hi, are we still meeting for lunch tomorrow at 1? I'll bring the documents.")
        assertEquals(RiskLevel.LOW, result.level)
        assertTrue(result.findings.isEmpty())
        assertEquals(listOf(Recommendation.STAY_CAUTIOUS), result.recommendations)
    }

    @Test
    fun ordinaryIndianMessagesAreLowRisk() {
        listOf(
            "Rs.1,250.00 debited from A/c XX4521 on 03-11-26 to VPA swiggy@icici. Not you? SMS BLOCK 4521 to 9215676766 -HDFC Bank",
            "Your Amazon order #408-1234567 has been shipped and will arrive by Friday. Track at https://www.amazon.in/gp/your-account/order-history",
            "Dear Customer, your electricity bill of Rs 842 for Oct is generated. Due date 15-11-2026. Pay via official MSEDCL app. -MSEDCL",
            "आज शाम को घर पर पूजा है, सब लोग 7 बजे तक आ जाना।",
            "उद्या सकाळी 10 वाजता शाळेत पालक सभा आहे.",
            "Your IRCTC booking PNR 4521789632 is confirmed. Coach B2 Seat 34.",
        ).forEach { text ->
            val result = RiskAnalyzer.message(text)
            assertEquals("$text → ${result.findings.map { it.code to it.evidence }}", RiskLevel.LOW, result.level)
        }
    }

    @Test
    fun emptyAndOversizedInputAreHandled() {
        assertEquals(RiskLevel.LOW, RiskAnalyzer.message("").level)
        val huge = "urgent ".repeat(5000)
        val result = RiskAnalyzer.message(huge)
        assertTrue(result.score in 0..100)
    }

    @Test
    fun highlightRangesPointAtOriginalText() {
        val text = "Please   SHARE your OTP now"
        val finding = RiskAnalyzer.message(text).findings.first { it.code == FindingCode.OTP_REQUEST }
        assertEquals("OTP", text.substring(finding.range!!))
    }

    // ---- Indian languages ----

    @Test
    fun hindiKycScamIsFlagged() {
        val result = RiskAnalyzer.message("प्रिय ग्राहक, आपका खाता बंद हो जाएगा। तुरंत केवाईसी अपडेट करें और अपना ओटीपी बताएं।")
        assertTrue(result.has(FindingCode.ACCOUNT_THREAT))
        assertTrue(result.has(FindingCode.URGENCY))
        assertTrue(result.has(FindingCode.KYC_UPDATE))
        assertTrue(result.has(FindingCode.OTP_REQUEST))
        assertEquals(RiskLevel.HIGH, result.level)
        assertEquals(DetectedLanguage.HINDI, result.language)
    }

    @Test
    fun marathiElectricityScamIsFlagged() {
        val result = RiskAnalyzer.message("प्रिय ग्राहक, तुमचे वीज कनेक्शन आज रात्री 9:30 वाजता खंडित केले जाईल. लगेच या नंबरवर कॉल करा 9876543210")
        assertTrue(result.has(FindingCode.SERVICE_DISCONNECTION))
        assertTrue(result.has(FindingCode.URGENCY))
        assertTrue(result.has(FindingCode.CALLBACK_REQUEST))
        assertTrue(result.level != RiskLevel.LOW)
        assertEquals(DetectedLanguage.MARATHI, result.language)
    }

    @Test
    fun hindiOtpSafetyNoticeIsNotARequest() {
        val result = RiskAnalyzer.message("आपका ओटीपी 552910 है। इसे किसी के साथ साझा न करें। - एसबीआई")
        assertFalse(result.has(FindingCode.OTP_REQUEST))
        assertEquals(RiskLevel.LOW, result.level)
    }

    @Test
    fun hinglishScamIsFlagged() {
        val result = RiskAnalyzer.message("Aapka account band ho jayega, turant KYC update karein")
        assertTrue(result.has(FindingCode.ACCOUNT_THREAT))
        assertTrue(result.has(FindingCode.URGENCY))
        assertEquals(DetectedLanguage.ROMANISED_HINDI, result.language)
    }

    @Test
    fun englishLanguageIsDetected() {
        assertEquals(DetectedLanguage.ENGLISH, ScamLens.detectLanguage("Your parcel is waiting for delivery"))
        assertEquals(DetectedLanguage.UNKNOWN, ScamLens.detectLanguage("12345 !!"))
    }

    // ---- URLs ----

    @Test
    fun suspiciousUrlIsFlagged() {
        val result = RiskAnalyzer.url("https://hdfcbank-kyc-update.xyz/verify")
        assertTrue(result.has(FindingCode.URL_BRAND_IMPERSONATION))
        assertTrue(result.has(FindingCode.URL_SUSPICIOUS_TLD))
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun ipBasedUrlIsFlagged() {
        val result = RiskAnalyzer.url("http://192.168.10.5/login.php")
        assertTrue(result.has(FindingCode.URL_IP_HOST))
        assertTrue(result.has(FindingCode.URL_NO_HTTPS))
        assertTrue(RiskAnalyzer.url("http://3232235777/").has(FindingCode.URL_IP_HOST))
        assertTrue(RiskAnalyzer.url("http://[2001:db8::1]/").has(FindingCode.URL_IP_HOST))
    }

    @Test
    fun punycodeDomainIsFlagged() {
        val result = RiskAnalyzer.url("https://xn--pypal-4ve.com/signin")
        assertTrue(result.has(FindingCode.URL_INTERNATIONALIZED))
    }

    @Test
    fun cyrillicHomographIsFlagged() {
        // "раytm" with Cyrillic р and а.
        val result = RiskAnalyzer.url("https://\u0440\u0430ytm.com/login")
        assertTrue(result.has(FindingCode.URL_INTERNATIONALIZED))
        assertTrue(result.has(FindingCode.URL_LOOKALIKE_DOMAIN) || result.has(FindingCode.URL_BRAND_IMPERSONATION))
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun embeddedCredentialsAreFlagged() {
        val result = RiskAnalyzer.url("https://www.onlinesbi.sbi:secure@203.0.113.9/")
        assertTrue(result.has(FindingCode.URL_USER_INFO))
        assertTrue(result.has(FindingCode.URL_IP_HOST))
        assertEquals(RiskLevel.HIGH, result.level)
    }

    @Test
    fun lookalikeDomainIsFlagged() {
        assertTrue(RiskAnalyzer.url("https://icicbank.com/").has(FindingCode.URL_LOOKALIKE_DOMAIN)) // one letter missing
        assertTrue(RiskAnalyzer.url("https://paytrn.com/").has(FindingCode.URL_LOOKALIKE_DOMAIN)) // "rn" looks like "m"
        assertTrue(RiskAnalyzer.url("https://g00gle.com/").has(FindingCode.URL_LOOKALIKE_DOMAIN)) // digits for letters
        // "hdfc" appears inside the label, so this is reported as brand impersonation rather than a misspelling.
        assertTrue(RiskAnalyzer.url("https://hdfcbnk.com/").has(FindingCode.URL_BRAND_IMPERSONATION))
    }

    @Test
    fun shortenerAndApkAreFlagged() {
        assertTrue(RiskAnalyzer.url("https://bit.ly/3xYz").has(FindingCode.URL_SHORTENER))
        assertTrue(RiskAnalyzer.url("https://files.example.net/app/SBI-Rewards.apk").has(FindingCode.URL_APK_DOWNLOAD))
    }

    @Test
    fun brandOnFreeHostingIsFlagged() {
        val result = RiskAnalyzer.url("https://sbi-yono-rewards.web.app/")
        assertTrue(result.has(FindingCode.URL_FREE_HOSTING))
        assertTrue(result.has(FindingCode.URL_BRAND_IMPERSONATION))
    }

    @Test
    fun embeddedRedirectIsFlagged() {
        val result = RiskAnalyzer.url("https://www.google.com/url?q=x&url=https%3A%2F%2Fevil.example%2Fpay")
        assertTrue(result.has(FindingCode.URL_EMBEDDED_REDIRECT))
    }

    @Test
    fun dangerousSchemeIsHighRisk() {
        assertEquals(RiskLevel.HIGH, RiskAnalyzer.url("javascript:alert(document.cookie)").level)
    }

    @Test
    fun validNormalUrlsAreLowRisk() {
        listOf(
            "https://www.google.com",
            "https://www.onlinesbi.sbi/",
            "https://www.hdfcbank.com/personal/pay/cards",
            "https://uidai.gov.in/en/my-aadhaar/update-aadhaar.html",
            "https://en.wikipedia.org/wiki/Unified_Payments_Interface",
            "amazon.in/gp/your-account",
        ).forEach { url ->
            val result = RiskAnalyzer.url(url)
            assertEquals("$url → ${result.findings}", RiskLevel.LOW, result.level)
        }
    }

    @Test
    fun malformedUrlsDoNotCrash() {
        listOf("", "   ", "http://", "https://exa mple.com", "http://:80", "https://example.com:99999", "::::", "http://[::1", "%%%")
            .forEach { assertTrue(it, RiskAnalyzer.url(it).has(FindingCode.URL_PARSE_FAILED)) }
    }

    @Test
    fun registrableDomainHandlesIndianSuffixes() {
        assertEquals("sbi.co.in", DomainTools.registrableDomain("retail.sbi.co.in"))
        assertEquals("evil.xyz", DomainTools.registrableDomain("sbi.co.in.evil.xyz"))
        assertEquals("phish.web.app", DomainTools.registrableDomain("a.phish.web.app"))
    }

    @Test
    fun linksAreExtractedFromText() {
        val links = UrlParser.extract("Visit www.example.com/x, or http://a.b.in. Mail me at user@gmail.com")
        assertEquals(listOf("www.example.com/x", "http://a.b.in"), links.map { it.first })
    }
}
