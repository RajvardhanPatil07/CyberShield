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
