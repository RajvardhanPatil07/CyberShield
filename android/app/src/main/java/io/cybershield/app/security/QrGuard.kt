package io.cybershield.app.security

import java.net.URLDecoder

enum class QrContentType { WEB_URL, UPI_PAYMENT, PHONE, SMS, EMAIL, WIFI, CONTACT, LOCATION, APP_LINK, TEXT, INVALID }

/** Parsed `upi://pay` (or `upi://collect`) URI as defined by NPCI's UPI linking specification. */
data class UpiPayment(
    val action: String,
    val payeeAddress: String?,
    val payeeName: String?,
    val amount: String?,
    val currency: String?,
    val note: String?,
    val merchantCode: String?,
) {
    /** Only the PSP handle, e.g. "@ybl". Safe to store in history. */
    val handle: String? get() = payeeAddress?.substringAfter('@', "")?.takeIf { it.isNotEmpty() }?.let { "@$it" }
}

data class QrAnalysis(
    val type: QrContentType,
    val assessment: RiskAssessment,
    val upi: UpiPayment? = null,
    /** Web URL or UPI URI that may be opened after explicit user confirmation. Null if nothing is openable. */
    val openTarget: String? = null,
    /** Short, non-sensitive description for the history list. */
    val summary: String,
)

object QrGuard {
    private val vpa = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}$""")

    /** Common bank and app UPI handles. An unknown handle is a weak signal only. */
    val knownHandles = setOf(
        "ybl", "ibl", "axl", "okaxis", "okhdfcbank", "okicici", "oksbi", "paytm", "ptyes", "ptaxis", "pthdfc", "ptsbi", "apl",
        "yapl", "rapl", "upi", "sbi", "hdfcbank", "icici", "axisbank", "kotak", "kmbl", "yesbank", "yesbankltd", "ibl", "boi",
        "pnb", "barodampay", "unionbank", "cnrb", "indus", "federal", "idfcbank", "idfcfirst", "aubank", "rbl", "jupiteraxis",
        "fam", "slc", "superyes", "naviaxis", "waicici", "waaxis", "wahdfcbank", "wasbi", "freecharge", "airtel", "jio", "amazonpay",
        "dbs", "citi", "hsbc", "sc", "ikwik", "mahb", "centralbank", "uco", "allbank", "indianbank", "iob", "psb", "kbl", "kvb",
        "tjsb", "cosb", "dlb", "equitas", "ujjivan", "pingpay", "abfspay", "timecosmos", "postbank", "goaxb", "okbizaxis",
    )
    private val impersonationNames = listOf(
        "bank", "rbi", "reserve bank", "npci", "customer care", "customer support", "helpdesk", "help desk", "support team",
        "government", "govt", "income tax", "police", "cyber cell", "electricity", "kyc", "refund desk", "sbi", "hdfc", "icici",
    )
    private val suspiciousNotes = listOf(
        "refund", "prize", "lottery", "cashback", "kyc", "receive", "reward", "winner", "gift", "verify", "verification",
        "unblock", "reactivate", "penalty", "fine", "challan", "रिफंड", "इनाम", "कैशबैक", "बक्षीस", "परतावा",
    )

    fun analyze(raw: String?): QrAnalysis {
        val content = raw?.trim().orEmpty()
        if (content.isEmpty()) {
            return QrAnalysis(QrContentType.INVALID, RiskAssessment.from(listOf(Finding(FindingCode.QR_EMPTY)), ScanKind.QR), summary = "Empty QR")
        }
        val lower = content.lowercase()
        return when {
            lower.startsWith("upi:") -> analyzeUpi(content)
            lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") -> analyzeWeb(content)
            lower.startsWith("tel:") -> other(QrContentType.PHONE, "Phone number")
            lower.startsWith("sms:") || lower.startsWith("smsto:") -> other(QrContentType.SMS, "SMS")
            lower.startsWith("mailto:") || lower.startsWith("matmsg:") -> other(QrContentType.EMAIL, "E-mail")
            lower.startsWith("wifi:") -> other(QrContentType.WIFI, "Wi-Fi network")
            lower.startsWith("begin:vcard") || lower.startsWith("mecard:") -> other(QrContentType.CONTACT, "Contact card")
            lower.startsWith("geo:") -> other(QrContentType.LOCATION, "Location")
            lower.startsWith("market:") -> other(QrContentType.APP_LINK, "Play Store link")
            Regex("^(javascript|data|intent|file|content|vbscript):").containsMatchIn(lower) ->
                QrAnalysis(
                    QrContentType.APP_LINK,
                    RiskAssessment.from(listOf(Finding(FindingCode.URL_DANGEROUS_SCHEME, lower.substringBefore(':') + ":")), ScanKind.QR,
                        recommendations = listOf(Recommendation.DONT_OPEN_LINK)),
                    summary = "Executable link",
                )
            UrlParser.parse(content)?.takeIf { it.host.contains('.') && !content.contains(' ') } != null -> analyzeWeb(content)
            else -> {
                val text = ScamLens.analyze(content, ScanKind.QR)
                val findings = if (text.findings.isEmpty()) listOf(Finding(FindingCode.QR_TEXT_ONLY)) else text.findings
                QrAnalysis(QrContentType.TEXT, text.copy(findings = findings), summary = "Text")
            }
        }
    }

    private fun other(type: QrContentType, summary: String) = QrAnalysis(
        type, RiskAssessment.from(listOf(Finding(FindingCode.QR_OTHER_ACTION, summary)), ScanKind.QR,
            recommendations = listOf(Recommendation.STAY_CAUTIOUS)), summary = summary,
    )

    private fun analyzeWeb(content: String): QrAnalysis {
        val parsed = UrlParser.parse(content)
        val result = LinkGuard.analyze(content).copy(kind = ScanKind.QR)
        val safeToOffer = parsed != null && parsed.scheme in setOf("http", "https") && parsed.host.isNotEmpty()
        return QrAnalysis(
            QrContentType.WEB_URL, result,
            openTarget = if (safeToOffer) (if (parsed!!.schemeWasMissing) "https://" + content.trim() else content.trim()) else null,
            summary = parsed?.host ?: "Web link",
        )
    }

    fun parseUpi(content: String): UpiPayment? {
        val afterScheme = content.substringAfter(':').removePrefix("//")
        val action = afterScheme.substringBefore('?').trimEnd('/').lowercase()
        if (action.isEmpty()) return null
        val params = afterScheme.substringAfter('?', "").split('&').filter { it.contains('=') }.associate { pair ->
            val key = pair.substringBefore('=').lowercase()
            val value = runCatching { URLDecoder.decode(pair.substringAfter('='), "UTF-8") }.getOrDefault(pair.substringAfter('='))
            key to value.trim()
        }
        return UpiPayment(
            action = action,
            payeeAddress = params["pa"]?.takeIf { it.isNotEmpty() },
            payeeName = params["pn"]?.takeIf { it.isNotEmpty() },
            amount = params["am"]?.takeIf { it.isNotEmpty() },
            currency = params["cu"]?.takeIf { it.isNotEmpty() },
            note = params["tn"]?.takeIf { it.isNotEmpty() },
            merchantCode = params["mc"]?.takeIf { it.isNotEmpty() },
        )
    }

    private fun analyzeUpi(content: String): QrAnalysis {
        val upi = parseUpi(content)
            ?: return QrAnalysis(QrContentType.UPI_PAYMENT, RiskAssessment.from(listOf(Finding(FindingCode.UPI_MISSING_PAYEE)), ScanKind.QR), summary = "UPI")
        val f = mutableListOf(Finding(FindingCode.UPI_PAYMENT))
        val payee = upi.payeeAddress
        when {
            payee == null -> f += Finding(FindingCode.UPI_MISSING_PAYEE)
            !vpa.matches(payee) -> f += Finding(FindingCode.UPI_INVALID_VPA, payee.take(60))
            payee.substringAfter('@').lowercase() !in knownHandles -> f += Finding(FindingCode.UPI_UNKNOWN_HANDLE, upi.handle)
        }
        if (upi.action == "collect" || upi.action == "mandate") f += Finding(FindingCode.UPI_COLLECT_REQUEST, upi.action)
        upi.amount?.let { amount ->
            val value = amount.toBigDecimalOrNull()
            if (value != null && value >= 10_000.toBigDecimal()) f += Finding(FindingCode.UPI_LARGE_AMOUNT, "₹$amount")
            else f += Finding(FindingCode.UPI_FIXED_AMOUNT, "₹$amount")
        }
        if (upi.currency != null && !upi.currency.equals("INR", ignoreCase = true)) f += Finding(FindingCode.UPI_NON_INR, upi.currency)
        val nameAndNote = listOfNotNull(upi.payeeName, upi.note).joinToString(" ").lowercase()
        suspiciousNotes.firstOrNull { nameAndNote.contains(it) }?.let { f += Finding(FindingCode.UPI_SUSPICIOUS_NOTE, it) }
        upi.payeeName?.lowercase()?.let { name ->
            impersonationNames.firstOrNull { Regex("(?<![a-z])" + Regex.escape(it) + "(?![a-z])").containsMatchIn(name) }
                ?.let { f += Finding(FindingCode.UPI_IMPERSONATION_NAME, it) }
        }
        val recs = buildList {
            add(Recommendation.CHECK_PAYEE)
            add(Recommendation.PIN_ONLY_TO_PAY)
            if (f.any { it.code == FindingCode.UPI_SUSPICIOUS_NOTE || it.code == FindingCode.UPI_IMPERSONATION_NAME || it.code == FindingCode.UPI_COLLECT_REQUEST }) {
                add(Recommendation.VERIFY_OFFICIAL_CHANNEL)
                add(Recommendation.REPORT_1930)
            }
        }
        val valid = payee != null && vpa.matches(payee)
        return QrAnalysis(
            QrContentType.UPI_PAYMENT,
            RiskAssessment.from(f, ScanKind.QR, recommendations = recs),
            upi = upi,
            openTarget = if (valid && upi.action == "pay") content.trim() else null,
            summary = "UPI " + (upi.handle ?: ""),
        )
    }
}
