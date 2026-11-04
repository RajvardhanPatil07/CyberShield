package io.cybershield.app.data

import android.content.Context
import io.cybershield.app.security.FindingCode
import io.cybershield.app.security.QrAnalysis
import io.cybershield.app.security.QrContentType
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScanKind
import io.cybershield.app.security.UrlParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One history entry. Deliberately contains no message text, no full URL, no UPI ID and no numbers
 * from the scanned content. [target] is at most a hostname or a UPI handle suffix such as "@ybl".
 */
data class ScanRecord(
    val id: String,
    val kind: ScanKind,
    val score: Int,
    val level: RiskLevel,
    val codes: List<FindingCode>,
    val target: String?,
    val confirmedMalicious: Boolean,
    val checkedAt: Long,
)

/** Kept for source compatibility with the original dashboard code. */
typealias ScanHistoryItem = ScanRecord

interface HistoryStore {
    fun read(): String?
    fun write(value: String?)
}

class SharedPrefsHistoryStore(context: Context) : HistoryStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY_ITEMS, null)
    override fun write(value: String?) {
        prefs.edit().apply { if (value == null) remove(KEY_ITEMS) else putString(KEY_ITEMS, value) }.apply()
    }

    companion object {
        // Same file/key as the original MVP, so existing history survives the upgrade.
        const val PREFS = "cybershield_local_history"
        const val KEY_ITEMS = "items"
    }
}

class InMemoryHistoryStore(private var value: String? = null) : HistoryStore {
    override fun read() = value
    override fun write(value: String?) { this.value = value }
}

data class HistoryStats(val total: Int, val suspicious: Int, val highRisk: Int, val confirmed: Int)

class ScanHistoryRepository(private val store: HistoryStore, private val clock: () -> Long = System::currentTimeMillis) {
    fun all(): List<ScanRecord> {
        val json = runCatching { JSONArray(store.read() ?: "[]") }.getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { json.optJSONObject(it)?.let(::decode) }
    }

    fun add(assessment: RiskAssessment, kind: ScanKind, target: String?, confirmedMalicious: Boolean = false): ScanRecord {
        val record = ScanRecord(
            id = UUID.randomUUID().toString(),
            kind = kind,
            score = assessment.score,
            level = if (confirmedMalicious) RiskLevel.HIGH else assessment.level,
            codes = assessment.findings.map { it.code }.distinct().filter { it.weight > 0 }.take(MAX_CODES),
            target = target?.let(::sanitizeTarget),
            confirmedMalicious = confirmedMalicious,
            checkedAt = clock(),
        )
        save(listOf(record) + all())
        return record
    }

    /** Records a URL scan, keeping only the hostname. */
    fun addUrl(assessment: RiskAssessment, rawUrl: String, confirmedMalicious: Boolean = false) =
        add(assessment, ScanKind.URL, hostOnly(rawUrl), confirmedMalicious)

    /** Records a QR scan: hostname for links, handle suffix for UPI, type name otherwise. */
    fun addQr(analysis: QrAnalysis, confirmedMalicious: Boolean = false) = add(
        analysis.assessment, ScanKind.QR,
        when (analysis.type) {
            QrContentType.WEB_URL -> analysis.openTarget?.let(::hostOnly) ?: "link"
            QrContentType.UPI_PAYMENT -> "UPI " + (analysis.upi?.handle ?: "")
            else -> analysis.type.name.lowercase()
        },
        confirmedMalicious,
    )

    fun markConfirmed(id: String) = save(all().map { if (it.id == id) it.copy(confirmedMalicious = true, level = RiskLevel.HIGH) else it })

    fun delete(id: String) = save(all().filterNot { it.id == id })

    fun clear() = store.write(null)

    fun search(query: String = "", kind: ScanKind? = null, minLevel: RiskLevel? = null): List<ScanRecord> {
        val q = query.trim().lowercase()
        return all().filter { r ->
            (kind == null || r.kind == kind) &&
                (minLevel == null || r.level.ordinal >= minLevel.ordinal) &&
                (q.isEmpty() || r.target?.lowercase()?.contains(q) == true || r.codes.any { it.name.lowercase().replace('_', ' ').contains(q) })
        }
    }

    fun stats(): HistoryStats {
        val items = all()
        return HistoryStats(
            total = items.size,
            suspicious = items.count { it.level != RiskLevel.LOW },
            highRisk = items.count { it.level == RiskLevel.HIGH },
            confirmed = items.count { it.confirmedMalicious },
        )
    }

    private fun save(records: List<ScanRecord>) {
        val array = JSONArray()
        records.take(MAX_ITEMS).forEach { array.put(encode(it)) }
        store.write(array.toString())
    }

    private fun encode(r: ScanRecord) = JSONObject()
        .put("id", r.id)
        .put("kind", r.kind.name)
        .put("score", r.score)
        .put("level", r.level.name)
        .put("codes", JSONArray(r.codes.map { it.name }))
        .put("target", r.target ?: JSONObject.NULL)
        .put("confirmed", r.confirmedMalicious)
        .put("checkedAt", r.checkedAt)

    private fun decode(o: JSONObject): ScanRecord? {
        val score = o.optInt("score", 0).coerceIn(0, 100)
        // Records written by v0.2 only had type/score/verdict/checkedAt.
        val kind = o.optString("kind").let { k -> ScanKind.entries.firstOrNull { it.name == k } }
            ?: when (o.optString("type")) { "Link" -> ScanKind.URL; "QR" -> ScanKind.QR; else -> ScanKind.MESSAGE }
        val codes = o.optJSONArray("codes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> FindingCode.entries.firstOrNull { it.name == arr.optString(i) } }
        }.orEmpty()
        return ScanRecord(
            id = o.optString("id").ifEmpty { "legacy-" + o.optLong("checkedAt", 0L) },
            kind = kind,
            score = score,
            level = o.optString("level").let { l -> RiskLevel.entries.firstOrNull { it.name == l } } ?: RiskLevel.of(score),
            codes = codes,
            target = if (o.isNull("target")) null else o.optString("target").ifEmpty { null },
            confirmedMalicious = o.optBoolean("confirmed", false),
            checkedAt = o.optLong("checkedAt", 0L),
        )
    }

    companion object {
        const val MAX_ITEMS = 200
        private const val MAX_CODES = 6

        fun hostOnly(rawUrl: String): String? = UrlParser.parse(rawUrl)?.host?.takeIf { it.isNotEmpty() }

        /** Defence in depth: whatever the caller passes, keep only a short host-like or handle-like token. */
        internal fun sanitizeTarget(value: String): String? {
            val cleaned = value.trim().take(80)
            if (cleaned.isEmpty()) return null
            if (cleaned.contains("/") || cleaned.contains("?") || cleaned.contains("=")) return hostOnly(cleaned)
            // Never keep digit runs (phone numbers, OTPs, account numbers).
            return cleaned.replace(Regex("\\d{3,}"), "•••")
        }

        fun create(context: Context) = ScanHistoryRepository(SharedPrefsHistoryStore(context))
    }
}
