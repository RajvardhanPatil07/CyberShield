package io.cybershield.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScanHistoryItem(
    val type: String,
    val score: Int,
    val verdict: String,
    val checkedAt: Long
)

/**
 * Stores scan metadata only. Message bodies and full URLs are deliberately not retained.
 */
object ScanHistory {
    private const val PREFS = "cybershield_local_history"
    private const val KEY_ITEMS = "items"

    fun add(context: Context, type: String, score: Int, verdict: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }.getOrDefault(JSONArray())
        val updated = JSONArray().put(
            JSONObject()
                .put("type", type)
                .put("score", score)
                .put("verdict", verdict)
                .put("checkedAt", System.currentTimeMillis())
        )
        for (index in 0 until minOf(old.length(), 9)) {
            updated.put(old.getJSONObject(index))
        }
        prefs.edit().putString(KEY_ITEMS, updated.toString()).apply()
    }

    fun read(context: Context): List<ScanHistoryItem> {
        val json = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]"))
        }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                add(
                    ScanHistoryItem(
                        type = item.optString("type", "Scan"),
                        score = item.optInt("score", 0),
                        verdict = item.optString("verdict", "Checked"),
                        checkedAt = item.optLong("checkedAt", 0L)
                    )
                )
            }
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply()
    }
}
