package io.cybershield.app.security

/**
 * Local, offline message analysis for SMS, chat messages and OCR text.
 * Runs on every language dictionary regardless of UI language, because scam texts often mix scripts.
 */
object ScamLens {
    const val MAX_INPUT = 10_000

    private fun phraseRegex(phrases: List<String>): Regex {
        val body = phrases.distinct().sortedByDescending { it.length }.joinToString("|") { phrase ->
            val words = phrase.lowercase().trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            val first = phrase.first()
            val last = phrase.last()
            val devanagari = phrase.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.DEVANAGARI }
            val lead = if (first.isLetterOrDigit()) "(?<![\\p{L}\\p{M}\\p{N}])" else ""
            // Devanagari phrases may carry inflection suffixes (दें → देंगे), so no trailing boundary.
            val trail = if (last.isLetterOrDigit() && !devanagari) "(?![\\p{L}\\p{N}])" else ""
            "$lead$words$trail"
        }
        return Regex("(?:$body)")
    }

    private val categoryPatterns: List<Pair<FindingCode, Regex>> by lazy {
        val merged = mutableMapOf<FindingCode, MutableList<String>>()
        listOf(ScamPhrases.english, ScamPhrases.hinglish, ScamPhrases.hindi, ScamPhrases.marathi).forEach { dict ->
            dict.forEach { (code, list) -> merged.getOrPut(code) { mutableListOf() } += list }
        }
        merged.map { (code, list) -> code to phraseRegex(list) }
    }
    private val secretPatterns by lazy { ScamPhrases.secrets.map { (code, list) -> code to phraseRegex(list) } }
    private val verbPattern by lazy { phraseRegex(ScamPhrases.requestVerbs) }
    private val noticePattern by lazy { phraseRegex(ScamPhrases.safetyNotices) }

    private val receiveTricks = listOf(
        Regex("""(?:pin|scan|qr|approve|accept)[^.\n]{0,40}(?:receive|get|credit|claim)[^.\n]{0,25}(?:money|amount|payment|cashback|refund|rs\b|₹|prize)"""),
        Regex("""(?:receive|get|claim)[^.\n]{0,25}(?:money|amount|payment|cashback|refund|prize|rs\b|₹)[^.\n]{0,40}(?:enter|scan|approve|accept)[^.\n]{0,25}(?:pin|qr|request)"""),
        // Patterns run on lower-cased text, so "QR" is written as "qr".
        Regex("""(?:पैसे|राशि|रकम|कैशबैक|रिफंड)[^।.\n]{0,30}(?:पाने|प्राप्त|लेने)[^।.\n]{0,30}(?:पिन|स्कैन|क्यूआर|qr)"""),
        Regex("""(?:पैसे|रक्कम|कॅशबॅक|परतावा)[^।.\n]{0,30}(?:मिळवण्यासाठी|मिळेल|मिळतील)[^।.\n]{0,30}(?:पिन|स्कॅन|क्यूआर|qr)"""),
    )

    private val marathiMarkers = setOf("आहे", "आहेत", "तुमचे", "तुमच्या", "तुमचा", "करा", "आणि", "नाही", "होईल", "मध्ये", "आपले", "येईल", "झाले", "केले", "साठी", "लगेच")
    private val hindiMarkers = setOf("है", "हैं", "आपका", "आपके", "आपकी", "करें", "और", "नहीं", "होगा", "में", "जाएगा", "गया", "लिए", "कृपया", "अपना")
    private val hinglishMarkers = setOf("hai", "aapka", "aapke", "apna", "karein", "kare", "karo", "nahi", "hoga", "jayega", "kijiye", "turant", "aur", "mein", "ko", "ka", "ki")

    fun detectLanguage(text: String): DetectedLanguage {
        var dev = 0
        var latin = 0
        for (c in text) {
            if (!c.isLetter()) continue
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.DEVANAGARI -> dev++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        val total = dev + latin
        if (total == 0) return DetectedLanguage.UNKNOWN
        val tokens = text.lowercase().split(Regex("[\\s\\p{P}।]+")).filter { it.isNotEmpty() }
        return when {
            dev * 10 >= total * 7 -> {
                val mr = tokens.count { it in marathiMarkers }
                val hi = tokens.count { it in hindiMarkers }
                when {
                    mr > hi -> DetectedLanguage.MARATHI
                    hi > mr -> DetectedLanguage.HINDI
                    else -> DetectedLanguage.DEVANAGARI_UNKNOWN
                }
            }
            latin * 10 >= total * 7 ->
                if (tokens.count { it in hinglishMarkers } >= 2) DetectedLanguage.ROMANISED_HINDI else DetectedLanguage.ENGLISH
            else -> DetectedLanguage.MIXED
        }
    }

    fun analyze(input: String, kind: ScanKind = ScanKind.MESSAGE): RiskAssessment {
        val text = input.take(MAX_INPUT)
        // Char-by-char lowercasing keeps indices aligned with the original for highlighting.
        val lower = String(CharArray(text.length) { text[it].lowercaseChar() })
        val findings = mutableListOf<Finding>()

        for ((code, regex) in categoryPatterns) {
            regex.findAll(lower).take(3).forEach { m -> findings += Finding(code, text.substring(m.range), m.range) }
        }

        // Payment secrets: distinguish "send me your OTP" from "do not share your OTP".
        val notices = noticePattern.findAll(lower).map { it.range }.toList()
        val verbs = verbPattern.findAll(lower).map { it.range }
            .filter { v -> notices.none { n -> v.first >= n.first && v.last <= n.last + 1 } }.toList()
        for ((code, regex) in secretPatterns) {
            val mentions = regex.findAll(lower).toList()
            if (mentions.isEmpty()) continue
            val requested = mentions.filter { m -> verbs.any { v -> kotlin.math.abs(v.first - m.range.first) <= 80 } }
            when {
                requested.isNotEmpty() -> requested.take(2).forEach { findings += Finding(code, text.substring(it.range), it.range) }
                notices.isEmpty() -> findings += Finding(FindingCode.SECRET_MENTION, text.substring(mentions[0].range), mentions[0].range)
                else -> Unit // Only a safety notice: this is how genuine OTP messages look.
            }
        }

        for (regex in receiveTricks) {
            regex.find(lower)?.let { findings += Finding(FindingCode.RECEIVE_MONEY_TRICK, text.substring(it.range).take(80), it.range) }
        }

        // Links: analyse each one with LinkGuard.
        val links = UrlParser.extract(text).map { (url, range) -> Triple(url, range, LinkGuard.analyze(url)) }
        if (links.isNotEmpty()) {
            findings += Finding(FindingCode.CONTAINS_LINK, links.first().first.take(60), links.first().second)
            links.filter { it.third.score >= 30 }.forEach { (url, range, result) ->
                val host = UrlParser.parse(url)?.host ?: url.take(60)
                findings += Finding(FindingCode.SUSPICIOUS_LINK, host, range, extraWeight = (result.score * 6) / 10)
            }
        }

        val codes = findings.map { it.code }.toSet()
        val credential = codes.any { it in credentialCodes }
        val pressure = codes.any { it in pressureCodes }
        var bonus = 0
        if (credential && pressure) bonus += 15
        if (FindingCode.CONTAINS_LINK in codes && codes.any { it in linkLureCodes }) bonus += 15
        // Advance-fee fraud: a prize, job or loan that first needs a payment.
        if (codes.any { it == FindingCode.REWARD_LURE || it == FindingCode.JOB_TASK_SCAM || it == FindingCode.LOAN_LURE } && FindingCode.FEE_REQUEST in codes) bonus += 20
        if (FindingCode.AUTHORITY_IMPERSONATION in codes && (FindingCode.SECRECY in codes || FindingCode.PAYMENT_REQUEST in codes)) bonus += 15

        val result = RiskAssessment.from(
            findings, kind, bonus,
            recommendations = recommendationsFor(codes),
            language = detectLanguage(text),
            links = links.map { it.first to it.third },
        )
        return if (result.level == RiskLevel.LOW && result.recommendations.isEmpty())
            result.copy(recommendations = listOf(Recommendation.STAY_CAUTIOUS)) else result
    }

    private val credentialCodes = setOf(
        FindingCode.OTP_REQUEST, FindingCode.UPI_PIN_REQUEST, FindingCode.CARD_DETAILS_REQUEST, FindingCode.PASSWORD_REQUEST, FindingCode.RECEIVE_MONEY_TRICK,
    )
    private val pressureCodes = setOf(
        FindingCode.URGENCY, FindingCode.ACCOUNT_THREAT, FindingCode.SERVICE_DISCONNECTION, FindingCode.AUTHORITY_IMPERSONATION,
        FindingCode.KYC_UPDATE, FindingCode.REWARD_LURE, FindingCode.REFUND_LURE,
    )
    private val linkLureCodes = setOf(
        FindingCode.KYC_UPDATE, FindingCode.ACCOUNT_THREAT, FindingCode.REWARD_LURE, FindingCode.REFUND_LURE,
        FindingCode.SERVICE_DISCONNECTION, FindingCode.URGENCY,
    )

    private fun recommendationsFor(codes: Set<FindingCode>): List<Recommendation> = buildList {
        if (codes.any { it in credentialCodes } || FindingCode.SECRET_MENTION in codes) add(Recommendation.NEVER_SHARE_SECRETS)
        if (FindingCode.RECEIVE_MONEY_TRICK in codes || FindingCode.PAYMENT_REQUEST in codes) add(Recommendation.PIN_ONLY_TO_PAY)
        if (FindingCode.SUSPICIOUS_LINK in codes || FindingCode.CONTAINS_LINK in codes && codes.any { it in linkLureCodes }) add(Recommendation.DONT_OPEN_LINK)
        if (FindingCode.REMOTE_ACCESS_APP in codes || FindingCode.APK_LURE in codes) add(Recommendation.NO_REMOTE_APPS)
        if (FindingCode.AUTHORITY_IMPERSONATION in codes) add(Recommendation.NO_DIGITAL_ARREST)
        if (codes.any { it == FindingCode.FEE_REQUEST || it == FindingCode.REWARD_LURE || it == FindingCode.JOB_TASK_SCAM || it == FindingCode.LOAN_LURE || it == FindingCode.INVESTMENT_LURE }) add(Recommendation.NO_UPFRONT_FEES)
        if (codes.any { it == FindingCode.KYC_UPDATE || it == FindingCode.ACCOUNT_THREAT || it == FindingCode.SERVICE_DISCONNECTION || it == FindingCode.REFUND_LURE }) add(Recommendation.VERIFY_OFFICIAL_CHANNEL)
        if (isNotEmpty()) {
            add(Recommendation.REPORT_CHAKSHU)
            add(Recommendation.REPORT_1930)
        }
    }
}
