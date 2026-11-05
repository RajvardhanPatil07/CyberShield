package io.cybershield.app.security

import java.net.URLDecoder

/**
 * Local, offline URL analysis. It never fetches the URL: contacting a phishing server can confirm
 * to the attacker that the victim clicked. Redirect expansion is done only by the optional backend.
 */
object LinkGuard {
    private val dangerousSchemes = setOf("javascript", "data", "vbscript", "file", "intent", "content", "blob")
    private val webSchemes = setOf("http", "https")
    private val sensitiveWords = listOf(
        "login", "signin", "verify", "verification", "kyc", "ekyc", "update", "secure", "account", "wallet", "claim",
        "reward", "refund", "prize", "bonus", "banking", "netbanking", "otp", "password", "unlock", "suspend", "blocked",
        "confirm", "cashback", "lottery", "aadhaar", "aadhar", "redeem", "gift", "challan", "penalty", "parcel", "electricity",
    )
    /** Short sensitive words that only count as whole hostname/path parts ("pan" must not match "company"). */
    private val sensitiveExact = setOf("pan", "upi", "bank", "pay", "free", "win", "kyc", "bill")
    private val redirectParams = setOf("url", "u", "r", "redirect", "redirect_uri", "redirect_url", "next", "link", "goto", "dest", "destination", "target", "continue", "return", "returnurl")

    fun analyze(raw: String): RiskAssessment {
        val parsed = UrlParser.parse(raw)
            ?: return RiskAssessment.from(listOf(Finding(FindingCode.URL_PARSE_FAILED)), ScanKind.URL, recommendations = listOf(Recommendation.DONT_OPEN_LINK))
        return analyze(parsed)
    }

    fun analyze(url: ParsedUrl): RiskAssessment = analyze(url, depth = 0)

    private fun analyze(url: ParsedUrl, depth: Int): RiskAssessment {
        val f = mutableListOf<Finding>()
        if (url.scheme in dangerousSchemes) {
            f += Finding(FindingCode.URL_DANGEROUS_SCHEME, url.scheme + ":")
            return RiskAssessment.from(f, ScanKind.URL, recommendations = listOf(Recommendation.DONT_OPEN_LINK))
        }
        if (url.host.isEmpty() || url.scheme !in webSchemes && url.host.isEmpty()) {
            return RiskAssessment.from(listOf(Finding(FindingCode.URL_PARSE_FAILED)), ScanKind.URL)
        }
        val host = url.host
        val unicodeHost = url.unicodeHost
        val registrable = DomainTools.registrableDomain(unicodeHost)
        val official = DomainTools.isOfficial(registrable)

        if (url.schemeWasMissing) f += Finding(FindingCode.URL_NO_SCHEME)
        if (url.scheme == "http") f += Finding(FindingCode.URL_NO_HTTPS)
        if (url.userInfo != null) f += Finding(FindingCode.URL_USER_INFO, url.userInfo.take(40))
        if (url.hostWasPercentEncoded) f += Finding(FindingCode.URL_ENCODED_HOST)
        val isIp = DomainTools.isIpAddress(host)
        if (isIp) f += Finding(FindingCode.URL_IP_HOST, host)
        if (url.port != null && url.port != 80 && url.port != 443) f += Finding(FindingCode.URL_NONSTANDARD_PORT, url.port.toString())

        if (!isIp) {
            if (host.split('.').any { it.startsWith("xn--") }) f += Finding(FindingCode.URL_INTERNATIONALIZED, unicodeHost)
            if (DomainTools.hasMixedScript(unicodeHost)) f += Finding(FindingCode.URL_MIXED_SCRIPT, unicodeHost)
            val brands = DomainTools.impersonatedBrands(unicodeHost)
            brands.forEach { f += Finding(FindingCode.URL_BRAND_IMPERSONATION, it) }
            if (brands.isEmpty()) {
                (DomainTools.lookalikeOf(unicodeHost) ?: DomainTools.impersonatedBrands(unicodeHost, digitSwaps = true).firstOrNull())
                    ?.let { f += Finding(FindingCode.URL_LOOKALIKE_DOMAIN, it) }
            }
            if (DomainTools.isShortener(host)) f += Finding(FindingCode.URL_SHORTENER, host)
            DomainTools.freeHostingSuffix(host)?.let { f += Finding(FindingCode.URL_FREE_HOSTING, it) }
            if (DomainTools.tld(host) in DomainTools.suspiciousTlds) f += Finding(FindingCode.URL_SUSPICIOUS_TLD, "." + DomainTools.tld(host))
            val subdomainLabels = unicodeHost.removeSuffix(registrable).trimEnd('.').split('.').filter { it.isNotEmpty() && it != "www" }
            if (subdomainLabels.size >= 3) f += Finding(FindingCode.URL_MANY_SUBDOMAINS, subdomainLabels.size.toString())
            if (host.count { it == '-' } >= 3) f += Finding(FindingCode.URL_MANY_HYPHENS)
        }

        // Up to two distinct keywords count; several of them in a non-official hostname (kyc-update-axis) is typical of phishing.
        val keywords = if (official) emptyList() else sensitiveKeywords(host, url.path)
        keywords.take(2).forEach { f += Finding(FindingCode.URL_SENSITIVE_KEYWORD, it) }
        val keywordsInHost = if (official || isIp) 0 else sensitiveKeywords(host, "").size

        val path = url.path.lowercase()
        if (path.endsWith(".apk") || path.contains(".apk/")) f += Finding(FindingCode.URL_APK_DOWNLOAD)
        // A redirect target counts with part of its own score, like links inside a message.
        embeddedRedirect(url)?.let { target ->
            val extra = if (depth == 0) analyze(target, depth + 1).score * 6 / 10 else 0
            f += Finding(FindingCode.URL_EMBEDDED_REDIRECT, target.host, extraWeight = extra)
        }
        if (url.original.length > 150) f += Finding(FindingCode.URL_EXCESSIVE_LENGTH)

        val bonus = if (f.any { it.code == FindingCode.URL_BRAND_IMPERSONATION || it.code == FindingCode.URL_LOOKALIKE_DOMAIN } &&
            f.any { it.code == FindingCode.URL_SENSITIVE_KEYWORD || it.code == FindingCode.URL_SUSPICIOUS_TLD || it.code == FindingCode.URL_FREE_HOSTING }
        ) 10 else 0
        val keywordBonus = if (keywordsInHost >= 2) 10 else 0
        val recs = buildList {
            val score = f.sumOf { it.code.weight + it.extraWeight } + bonus + keywordBonus
            if (score >= 30) add(Recommendation.DONT_OPEN_LINK)
            if (f.any { it.code == FindingCode.URL_BRAND_IMPERSONATION || it.code == FindingCode.URL_LOOKALIKE_DOMAIN }) add(Recommendation.VERIFY_OFFICIAL_CHANNEL)
            if (f.any { it.code == FindingCode.URL_APK_DOWNLOAD }) add(Recommendation.NO_REMOTE_APPS)
            if (isEmpty()) add(Recommendation.STAY_CAUTIOUS)
        }
        return RiskAssessment.from(f, ScanKind.URL, bonus = bonus + keywordBonus, recommendations = recs)
    }

    private fun sensitiveKeywords(host: String, path: String): List<String> {
        val parts = (host.split('.', '-', '_') + path.lowercase().split('/', '-', '_', '.', '?', '=', '&'))
            .filter { it.isNotEmpty() }
        return parts.mapNotNull { part -> if (part in sensitiveExact) part else sensitiveWords.firstOrNull { part.contains(it) } }.distinct()
    }

    private fun embeddedRedirect(url: ParsedUrl): ParsedUrl? {
        val query = url.query ?: return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=').lowercase()
            if (key !in redirectParams) continue
            val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: continue
            if (!value.startsWith("http://", true) && !value.startsWith("https://", true) && !value.startsWith("//")) continue
            val target = UrlParser.parse(if (value.startsWith("//")) "https:$value" else value) ?: continue
            if (DomainTools.registrableDomain(target.host) != DomainTools.registrableDomain(url.host)) return target
        }
        return null
    }
}
