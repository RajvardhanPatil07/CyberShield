package io.cybershield.app.security

/**
 * Removes personal data before text leaves the device (optional AI analysis).
 * Keeps the wording that matters for scam detection; replaces values that identify the user.
 */
object Redactor {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b""") to "[EMAIL]",
        Regex("""\b[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}\b""") to "[UPI-ID]",
        Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""") to "[PAN]",
        Regex("""\b\d{4}[ \-]?\d{4}[ \-]?\d{4}[ \-]?\d{1,7}\b""") to "[CARD]",
        Regex("""\b\d{4}[ \-]\d{4}[ \-]\d{4}\b""") to "[AADHAAR]",
        Regex("""(?<!\d)(?:\+?91[ \-]?)?[6-9]\d{4}[ \-]?\d{5}(?!\d)""") to "[PHONE]",
        Regex("""(?i)\b(?:a/?c|acct|account)(?:\s*(?:no\.?|number))?\s*[:.\-]?\s*[xX*]*\d{3,18}\b""") to "[ACCOUNT]",
        Regex("""(?<!\d)\d{4,18}(?!\d)""") to "[NUMBER]",
    )

    /** Keeps scheme, host and path of URLs; drops query strings and fragments, which often carry tokens. */
    private fun stripUrlQueries(text: String): String {
        var out = text
        UrlParser.extract(text, limit = 20).sortedByDescending { it.second.first }.forEach { (url, range) ->
            val parsed = UrlParser.parse(url) ?: return@forEach
            val safe = parsed.origin + parsed.path.take(80)
            out = out.replaceRange(range.first, range.last + 1, safe)
        }
        return out
    }

    /** Values that identify the sender, and money amounts. A report needs them, so [forReport] keeps them. */
    private val keptInReports = listOf(
        Regex("""(?i)(?:\brs\.?|\binr|₹)\s?\d[\d,]*(?:\.\d{1,2})?"""),
        Regex("""(?i)\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b"""),
        Regex("""\b[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,63}\b"""),
        Regex("""(?<!\d)(?:\+?91[ \-]?)?[6-9]\d{4}[ \-]?\d{5}(?!\d)"""),
        // Toll-free and landline numbers such as 1800 123 4567 or 020-2555 1234.
        Regex("""(?<!\d)(?:1800|1860)[ \-]?\d{3}[ \-]?\d{4}(?!\d)"""),
        Regex("""(?<!\d)0\d{2,4}[ \-]\d{6,8}(?!\d)"""),
    )
    private val userSecrets = rules.filter { (_, label) -> label in setOf("[PAN]", "[CARD]", "[AADHAAR]", "[ACCOUNT]", "[NUMBER]") }

    /**
     * Prepares text for a fraud report that the user sends themselves (Chakshu, cybercrime.gov.in).
     * Removes the user's own secrets (OTPs and other codes, card, account, Aadhaar and PAN numbers) and
     * link query strings, but keeps the sender's phone numbers, UPI IDs, e-mail addresses and links, and
     * rupee amounts, because those are what the authorities need.
     */
    fun forReport(text: String): String {
        var out = stripUrlQueries(text.take(ScamLens.MAX_INPUT))
        val kept = mutableListOf<String>()
        fun protect(range: IntRange) {
            kept += out.substring(range)
            out = out.replaceRange(range, "\u0000${kept.size - 1}\u0000")
        }
        UrlParser.extract(out, limit = 20).sortedByDescending { it.second.first }.forEach { (_, range) -> protect(range) }
        for (regex in keptInReports) {
            regex.findAll(out).toList().asReversed().forEach { protect(it.range) }
        }
        for ((regex, replacement) in userSecrets) out = regex.replace(out, replacement)
        // Restore in reverse so a placeholder nested inside a later-protected value is filled in too.
        for (index in kept.indices.reversed()) out = out.replace("\u0000$index\u0000", kept[index])
        return out
    }

    fun redact(text: String): String {
        var out = stripUrlQueries(text.take(ScamLens.MAX_INPUT))
        // Protect URLs from number/e-mail rules by swapping them for placeholders first.
        val urls = UrlParser.extract(out, limit = 20)
        val placeholders = mutableListOf<String>()
        urls.sortedByDescending { it.second.first }.forEach { (url, range) ->
            placeholders.add(0, url)
            out = out.replaceRange(range.first, range.last + 1, "\u0000${urls.size - placeholders.size}\u0000")
        }
        for ((regex, replacement) in rules) out = regex.replace(out, replacement)
        placeholders.forEachIndexed { index, url -> out = out.replace("\u0000$index\u0000", url) }
        return out
    }
}
