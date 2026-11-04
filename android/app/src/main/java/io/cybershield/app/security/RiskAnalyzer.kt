package io.cybershield.app.security

import java.net.URI

data class RiskAssessment(
    val score: Int,
    val verdict: String,
    val reasons: List<String>,
    val disclaimer: String = "Heuristic result only. This is not proof that content is malicious."
)

object RiskAnalyzer {
    private val urgent = listOf("account will be blocked", "verify immediately", "urgent action", "suspended today", "final warning", "kyc update", "claim now")
    private val lures = listOf("free reward", "prize", "lottery", "refund", "instant loan", "registration fee", "guaranteed return")

    fun message(text: String): RiskAssessment {
        val value = text.lowercase()
        val reasons = buildList {
            if (urgent.any(value::contains)) add("Uses urgency or account-threat language.")
            if (lures.any(value::contains)) add("Contains a common reward, fee or guaranteed-return lure.")
            if (Regex("""https?://|www\.""").containsMatchIn(value)) add("Contains a link; inspect its destination separately.")
            if (Regex("""\b(otp|upi pin|password|cvv)\b""").containsMatchIn(value)) add("Mentions credentials or payment secrets. Never share these.")
            if (isEmpty()) add("No obvious scam phrase was detected by the basic local rules.")
        }
        val score = reasons.sumOf { reason ->
            when {
                reason.contains("credentials") -> 35
                reason.contains("urgency") || reason.contains("lure") -> 25
                reason.contains("link") -> 15
                else -> 5
            }
        }.coerceIn(5, 95)
        return result(score, reasons)
    }

    fun url(raw: String): RiskAssessment {
        val uri = runCatching { URI(raw.trim()) }.getOrNull()
        val host = uri?.host?.lowercase()
        if (host.isNullOrBlank()) return result(55, listOf("Could not parse a complete URL with a host."))
        val reasons = buildList {
            if (!uri.scheme.equals("https", ignoreCase = true)) add("The address does not use HTTPS.")
            if (uri.rawUserInfo != null) add("The URL embeds user-info before the host, which can disguise its destination.")
            if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) add("The host is an IP address rather than a conventional domain.")
            if (host.startsWith("xn--") || host.contains(".xn--")) add("The domain uses punycode characters; verify its spelling carefully.")
            if (listOf("verify", "kyc", "login", "wallet", "claim", "prize").any(host::contains)) add("The hostname contains a term sometimes used in impersonation links.")
            if (host.count { it == '-' } >= 3) add("The hostname contains several hyphens.")
            if (isEmpty()) add("No obvious structural red flags were found. This does not establish that the site is safe.")
        }
        val score = reasons.sumOf {
            when {
                it.contains("user-info") -> 45
                it.contains("IP address") -> 30
                it.contains("HTTPS") -> 20
                it.contains("punycode") -> 25
                else -> 15
            }
        }.coerceIn(5, 95)
        return result(score, reasons)
    }

    private fun result(score: Int, reasons: List<String>) = RiskAssessment(
        score,
        when {
            score >= 60 -> "High risk signals"
            score >= 30 -> "Caution advised"
            else -> "No obvious red flags"
        },
        reasons
    )
}
