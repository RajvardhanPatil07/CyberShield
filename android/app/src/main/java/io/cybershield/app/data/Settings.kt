package io.cybershield.app.data

import android.content.Context
import io.cybershield.app.BuildConfig
import java.util.UUID

/** User choices. Cloud features are off until the user explicitly enables them. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cybershield_settings", Context.MODE_PRIVATE)

    var cloudConsent: Boolean
        get() = prefs.getBoolean(KEY_CLOUD, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD, value).apply()

    var saveHistory: Boolean
        get() = prefs.getBoolean(KEY_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_HISTORY, value).apply()

    /**
     * Opt-in on-device ML assist for message analysis. Off by default: it only takes effect when a
     * model is bundled AND the user turns it on (see docs/AI_ML_PLAN.md). Runs entirely on-device.
     */
    var mlAssist: Boolean
        get() = prefs.getBoolean(KEY_ML_ASSIST, false)
        set(value) = prefs.edit().putBoolean(KEY_ML_ASSIST, value).apply()

    /**
     * Opt-in consent to donate a redacted copy of a flagged message to improve detection. Off by
     * default; the text is redacted on-device and previewed before anything is sent.
     */
    var donationConsent: Boolean
        get() = prefs.getBoolean(KEY_DONATION, false)
        set(value) = prefs.edit().putBoolean(KEY_DONATION, value).apply()

    /** Optional override of the build-time backend URL, for demos against a self-hosted backend. */
    var backendOverride: String
        get() = prefs.getString(KEY_BACKEND, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_BACKEND, value.trim()).apply()

    val backendUrl: String get() = backendOverride.ifBlank { BuildConfig.BACKEND_URL }.trimEnd('/')

    /**
     * Random identifier used only so the backend can rate-limit per install. It is not linked to
     * any account, phone number or device identifier, and can be reset by clearing app data.
     */
    val installId: String
        get() = prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL, it).apply()
        }

    fun resetAll() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_CLOUD = "cloud_consent"
        const val KEY_HISTORY = "save_history"
        const val KEY_ML_ASSIST = "ml_assist"
        const val KEY_DONATION = "donation_consent"
        const val KEY_BACKEND = "backend_override"
        const val KEY_INSTALL = "install_id"
    }
}
