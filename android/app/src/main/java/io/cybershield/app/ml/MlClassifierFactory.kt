package io.cybershield.app.ml

import android.content.Context
import android.util.Log

/**
 * Resolves the on-device classifier for this build.
 *
 * Loads the baseline model bundled at `assets/ml/scam_classifier.json` and reproduces it with
 * [LogisticTextClassifier]. If the asset is missing or unreadable, falls back to [MlClassifier.Disabled]
 * so the app behaves exactly like the rules-only engine. The model only takes effect when the user
 * enables ML assist (off by default) and, through [io.cybershield.app.security.MlFusion], can only
 * raise risk, never lower it.
 *
 * Future (plan §4.1): swap [LogisticTextClassifier] for a distilled-MuRIL → LiteRT model behind the
 * same [MlClassifier] interface — only this factory and the wrapper change.
 */
object MlClassifierFactory {
    private const val ASSET = "ml/scam_classifier.json"

    fun create(context: Context): MlClassifier = try {
        val json = context.assets.open(ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        LogisticTextClassifier.fromJson(json)
    } catch (e: Exception) {
        Log.i("CyberShield", "On-device ML model not loaded; using rules only (${e.javaClass.simpleName}).")
        MlClassifier.Disabled
    }
}
