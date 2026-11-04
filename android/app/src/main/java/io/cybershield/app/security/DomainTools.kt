package io.cybershield.app.security

import java.net.IDN
import java.net.URLDecoder

/** A URL split into parts without java.net.URI, which rejects many real-world (and malicious) inputs. */
data class ParsedUrl(
    val original: String,
    val scheme: String,
    val userInfo: String?,
    /** Lower-case ASCII (punycode) host, without brackets or trailing dot. */
    val host: String,
    /** Host converted back to Unicode for display and homograph checks. */
    val unicodeHost: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val schemeWasMissing: Boolean,
    val hostWasPercentEncoded: Boolean,
) {
    /** scheme://host — safe to show and to store. Drops credentials, path, query and fragment. */
    val origin: String get() = "$scheme://$host" + (port?.let { ":$it" } ?: "")
}

object UrlParser {
    private val schemeRegex = Regex("""^([a-zA-Z][a-zA-Z0-9+.\-]*):""")
    private val bareDomain = Regex("""^[\p{L}\p{N}][\p{L}\p{N}\-]*(\.[\p{L}\p{N}\-]+)+(:\d+)?([/?#].*)?$""")

    fun parse(raw: String): ParsedUrl? {
        var text = raw.trim().trim('<', '>', '"', '\'').trimEnd('.', ',', ';', ')', ']', '}', '!')
        if (text.isEmpty() || text.length > 4096 || text.any { it.isWhitespace() || it.code < 0x20 }) return null
        var schemeMissing = false
        val schemeMatch = schemeRegex.find(text)
        // "example.com:8080/x" looks like a scheme "example.com"; treat it as a bare domain.
        val looksLikeHostPort = schemeMatch != null && text.drop(schemeMatch.value.length).takeWhile { it.isDigit() }.isNotEmpty() &&
            schemeMatch.groupValues[1].contains('.')
        if (schemeMatch == null || looksLikeHostPort) {
            if (!bareDomain.matches(text)) return null
            text = "https://$text"
            schemeMissing = true
        }
        val scheme = schemeRegex.find(text)!!.groupValues[1].lowercase()
        val rest = text.substring(scheme.length + 1)
        if (!rest.startsWith("//")) {
            // Opaque URIs such as javascript:, data:, mailto:, upi:. They have no host to analyse.
            return ParsedUrl(raw, scheme, null, "", "", null, rest, null, schemeMissing, false)
        }
        val afterSlashes = rest.substring(2)
        val authorityEnd = afterSlashes.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) afterSlashes.length else it }
        val authority = afterSlashes.substring(0, authorityEnd)
        val tail = afterSlashes.substring(authorityEnd)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else null
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority
        var host: String
        var port: Int? = null
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            host = hostPort.substring(1, close)
            val after = hostPort.substring(close + 1)
            if (after.startsWith(":")) port = after.drop(1).toIntOrNull() ?: return null
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) {
                port = hostPort.substring(colon + 1).takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
                host = hostPort.substring(0, colon)
            } else host = hostPort
        }
        var encoded = false
        if (host.contains('%')) {
            encoded = true
            host = runCatching { URLDecoder.decode(host, "UTF-8") }.getOrNull() ?: return null
        }
        host = host.trimEnd('.').lowercase()
        if (host.isEmpty() || port != null && port !in 1..65535) return null
        val ascii = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase() ?: return null
        if (ascii.isEmpty() || !ascii.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' || it == '_' }) return null
        val unicode = runCatching { IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host)
        val queryStart = tail.indexOf('?')
        val fragmentStart = tail.indexOf('#').let { if (it < 0) tail.length else it }
        val path = if (queryStart in 0 until fragmentStart) tail.substring(0, queryStart) else tail.substring(0, fragmentStart)
        val query = if (queryStart in 0 until fragmentStart) tail.substring(queryStart + 1, fragmentStart) else null
        return ParsedUrl(raw, scheme, userInfo, ascii, unicode, port, path, query, schemeMissing, encoded)
    }

    private val inText = Regex(
        """(?i)\b(?:https?://|www\.)[^\s<>"'`]+|\b[a-z0-9][a-z0-9\-]{0,62}(?:\.[a-z0-9\-]{1,63})*\.(?:com|in|net|org|info|biz|xyz|top|click|link|online|site|club|shop|live|app|io|me|ly|gl|co|cc|tk|ml|ga|cf|gq|icu|buzz|cyou|sbs|cfd|bond|rest|work|support|win|loan)(?::\d{2,5})?(?:/[^\s<>"'`]*)?"""
    )

    /** Finds up to [limit] links in free text, with their positions. */
    fun extract(text: String, limit: Int = 10): List<Pair<String, IntRange>> =
        inText.findAll(text).mapNotNull { m ->
            val trimmed = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
            val partOfEmail = m.range.first > 0 && text[m.range.first - 1] == '@' ||
                trimmed.contains('@') && !trimmed.contains("://")
            if (trimmed.isEmpty() || partOfEmail) null
            else trimmed to (m.range.first until m.range.first + trimmed.length)
        }.take(limit).toList()
}

object DomainTools {
    /** Multi-label public suffixes we care about. A full Public Suffix List is not bundled. */
    private val multiPartSuffixes = setOf(
        "co.in", "net.in", "org.in", "gov.in", "nic.in", "ac.in", "edu.in", "res.in", "firm.in", "gen.in", "ind.in", "mil.in", "bank.in",
        "co.uk", "org.uk", "gov.uk", "ac.uk", "com.au", "net.au", "org.au", "co.jp", "com.br", "com.sg", "com.my", "co.za",
        "com.cn", "com.pk", "com.bd", "com.np", "com.lk", "co.nz", "com.mx", "com.tr", "com.hk",
    )

    /** Free hosting / tunnelling services where anyone can create a subdomain. Treated as public suffixes. */
    val freeHosting = setOf(
        "web.app", "firebaseapp.com", "github.io", "netlify.app", "vercel.app", "pages.dev", "workers.dev", "herokuapp.com",
        "blogspot.com", "weebly.com", "wixsite.com", "000webhostapp.com", "glitch.me", "repl.co", "replit.app", "ngrok.io",
        "ngrok-free.app", "ngrok.app", "trycloudflare.com", "onrender.com", "azurewebsites.net", "appspot.com", "square.site",
        "webflow.io", "framer.website", "godaddysites.com", "mystrikingly.com", "carrd.co", "surge.sh", "loca.lt",
    )

    /** Registrable endings that only vetted organisations can obtain in India. */
    private val restrictedSuffixes = setOf("gov.in", "nic.in", "bank.in", "mil.in", "ac.in", "edu.in", "res.in")

    val shorteners = setOf(
        "bit.ly", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc", "rebrand.ly",
        "bl.ink", "t.ly", "v.gd", "s.id", "shorte.st", "adf.ly", "buff.ly", "surl.li", "short.gy", "tiny.one", "shrtco.de",
        "t.co", "lnkd.in", "wa.link", "qr.ae", "u.to", "clck.ru", "bitly.ws", "shorturl.asia", "rebrand.ly", "y2u.be",
    )

    val suspiciousTlds = setOf(
        "xyz", "top", "click", "link", "zip", "mov", "icu", "buzz", "tk", "ml", "ga", "cf", "gq", "rest", "cyou", "sbs", "cfd",
        "bond", "lol", "monster", "quest", "support", "win", "loan", "work", "country", "kim", "date", "racing", "review",
        "stream", "download", "men", "party", "trade", "accountant", "science", "faith", "cricket", "best", "beauty", "hair",
    )

    /**
     * Brand keyword → domains it legitimately owns. [exact] brands only match whole hostname parts
     * (so "axis" does not match "praxis"); others also match as a substring of a part.
     */
    data class Brand(val keyword: String, val official: Set<String>, val exact: Boolean = false)

    val brands = listOf(
        Brand("sbi", setOf("sbi.co.in", "onlinesbi.sbi", "sbicard.com", "sbilife.co.in", "sbimf.com", "sbi.bank.in"), exact = true),
        Brand("onlinesbi", setOf("onlinesbi.sbi", "onlinesbi.com")),
        Brand("yono", setOf("onlinesbi.sbi", "sbi.co.in", "yonobusiness.sbi"), exact = true),
        Brand("hdfc", setOf("hdfcbank.com", "hdfc.com", "hdfclife.com", "hdfcergo.com", "hdfcsec.com", "hdfcbank.bank.in")),
        Brand("icici", setOf("icicibank.com", "icicidirect.com", "iciciprulife.com", "icicilombard.com", "icicibank.bank.in")),
        Brand("axis", setOf("axisbank.com", "axisbank.bank.in"), exact = true),
        Brand("axisbank", setOf("axisbank.com", "axisbank.bank.in")),
        Brand("kotak", setOf("kotak.com", "kotaksecurities.com", "kotak.bank.in")),
        Brand("paytm", setOf("paytm.com", "paytmbank.com", "paytmmoney.com", "paytmmall.com", "paytm.in")),
        Brand("phonepe", setOf("phonepe.com")),
        Brand("gpay", setOf("google.com"), exact = true),
        Brand("googlepay", setOf("google.com")),
        Brand("bhim", setOf("bhimupi.org.in", "npci.org.in"), exact = true),
        Brand("npci", setOf("npci.org.in", "bhimupi.org.in")),
        Brand("uidai", setOf("uidai.gov.in")),
        Brand("aadhaar", setOf("uidai.gov.in")),
        Brand("aadhar", setOf("uidai.gov.in")),
        Brand("incometax", setOf("incometax.gov.in", "incometaxindia.gov.in")),
        Brand("irctc", setOf("irctc.co.in", "irctc.com")),
        Brand("amazon", setOf("amazon.in", "amazon.com", "amazon.co.uk", "amazonpay.in", "amazon.jobs")),
        Brand("flipkart", setOf("flipkart.com")),
        Brand("indiapost", setOf("indiapost.gov.in", "ippbonline.com")),
        Brand("epfo", setOf("epfindia.gov.in")),
        Brand("rbi", setOf("rbi.org.in"), exact = true),
        Brand("lic", setOf("licindia.in"), exact = true),
        Brand("whatsapp", setOf("whatsapp.com", "whatsapp.net", "wa.me")),
        Brand("airtel", setOf("airtel.in", "airtel.com")),
        Brand("jio", setOf("jio.com", "jiomart.com", "jiocinema.com"), exact = true),
        Brand("bsnl", setOf("bsnl.co.in", "bsnl.in")),
        Brand("mahadiscom", setOf("mahadiscom.in")),
        Brand("msedcl", setOf("mahadiscom.in")),
        Brand("fedex", setOf("fedex.com")),
        Brand("bluedart", setOf("bluedart.com")),
        Brand("dtdc", setOf("dtdc.in", "dtdc.com")),
        Brand("netflix", setOf("netflix.com")),
        Brand("google", setOf("google.com", "google.co.in", "youtube.com", "goo.gl", "g.co")),
        Brand("microsoft", setOf("microsoft.com", "live.com", "office.com", "microsoftonline.com")),
        Brand("apple", setOf("apple.com", "icloud.com")),
        Brand("paypal", setOf("paypal.com", "paypal.me")),
    )

    /** First label of each official domain, used for misspelling checks (hdfcbnk.com ≈ hdfcbank.com). */
    private val officialLabels: Map<String, String> = brands.flatMap { b -> b.official.map { it to b.keyword } }
        .associate { (domain, brand) -> domain.substringBefore('.') to brand }
        .filterKeys { it.length >= 5 }

    fun isIpAddress(host: String): Boolean =
        host.contains(':') || // IPv6 literal (brackets already removed)
            Regex("""^(0x[0-9a-f]+|\d+)(\.(0x[0-9a-f]+|\d+)){0,3}$""").matches(host)

    /** Best-effort registrable domain ("eTLD+1"), e.g. login.sbi.co.in.evil.xyz → evil.xyz. */
    fun registrableDomain(host: String): String {
        if (isIpAddress(host)) return host
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val suffixes = multiPartSuffixes + freeHosting
        for (take in 3 downTo 2) {
            if (labels.size > take) {
                val candidate = labels.takeLast(take).joinToString(".")
                if (candidate in suffixes) return labels.takeLast(take + 1).joinToString(".")
            }
            if (labels.size >= take && labels.takeLast(take).joinToString(".") in suffixes) {
                return labels.takeLast(take).joinToString(".")
            }
        }
        return labels.takeLast(2).joinToString(".")
    }

    fun tld(host: String) = host.substringAfterLast('.')

    fun isRestricted(registrable: String) = restrictedSuffixes.any { registrable.endsWith(".$it") || registrable == it } ||
        tld(registrable) == "sbi"

    fun isOfficial(registrable: String) = isRestricted(registrable) || brands.any { registrable in it.official }

    fun freeHostingSuffix(host: String): String? = freeHosting.firstOrNull { host.endsWith(".$it") }

    fun isShortener(host: String) = host.removePrefix("www.") in shorteners

    /** Hostname parts split on dots and hyphens, used for brand keyword matching. */
    private fun parts(host: String) = host.split('.', '-').filter { it.isNotEmpty() }

    /** Brands named in the hostname whose official domains do not include the actual registrable domain. */
    fun impersonatedBrands(host: String): List<String> {
        val registrable = registrableDomain(host)
        if (isOfficial(registrable)) return emptyList()
        val hostParts = parts(skeleton(host))
        return brands.filter { brand ->
            hostParts.any { part -> part == brand.keyword || !brand.exact && brand.keyword.length >= 4 && part.contains(brand.keyword) }
        }.filter { brand -> registrable !in brand.official }.map { it.keyword }.distinct()
    }

    /** A brand whose official label is one or two edits away from this domain's label. */
    fun lookalikeOf(host: String): String? {
        val registrable = registrableDomain(host)
        if (isOfficial(registrable)) return null
        val label = skeleton(registrable.substringBefore('.'))
        val normalized = confusableDigits(label)
        for ((official, brand) in officialLabels) {
            if (label == official) {
                // Same label, different suffix (hdfcbank.xyz) or homograph skeleton match (һdfcbank.com).
                if (registrable.substringBefore('.') != official || brands.none { registrable in it.official }) return brand
                continue
            }
            if (normalized == official) return brand
            val maxEdits = if (official.length >= 8) 2 else 1
            if (kotlin.math.abs(label.length - official.length) <= maxEdits && levenshtein(label, official) <= maxEdits) return brand
        }
        return null
    }

    private fun confusableDigits(s: String) = s
        .replace("rn", "m").replace("vv", "w").replace('0', 'o').replace('1', 'l').replace('3', 'e')
        .replace('5', 's').replace('4', 'a').replace('7', 't').replace('8', 'b')

    /** Maps common Cyrillic/Greek look-alike letters to Latin, so "раytm" (Cyrillic р, а) becomes "paytm". */
    fun skeleton(s: String): String = buildString {
        for (c in s) append(confusables[c] ?: c)
    }

    private val confusables = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y', 'і' to 'i', 'ѕ' to 's',
        'ԁ' to 'd', 'ј' to 'j', 'ӏ' to 'l', 'һ' to 'h', 'ԛ' to 'q', 'ԝ' to 'w', 'к' to 'k', 'м' to 'm', 'т' to 't',
        'в' to 'b', 'н' to 'h', 'ο' to 'o', 'α' to 'a', 'ν' to 'v', 'ρ' to 'p', 'τ' to 't', 'ι' to 'i', 'κ' to 'k',
        'ε' to 'e', 'μ' to 'u', 'ɡ' to 'g', 'ı' to 'i',
    )

    /** True when a single hostname label mixes Latin with Cyrillic or Greek letters. */
    fun hasMixedScript(unicodeHost: String): Boolean = unicodeHost.split('.').any { label ->
        var latin = false
        var other = false
        for (c in label) {
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.LATIN -> latin = true
                Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK -> other = true
                else -> Unit
            }
        }
        latin && other
    }

    fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }
}
