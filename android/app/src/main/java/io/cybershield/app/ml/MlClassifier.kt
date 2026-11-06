package io.cybershield.app.ml

/**
 * On-device scam-message classifier (Phase 1 scaffolding, see docs/AI_ML_PLAN.md).
 *
 * The contract is deliberately tiny so screens and [io.cybershield.app.security.RiskAnalyzer] never
 * depend on the model's implementation: today it is [Disabled]; later it is a LiteRT model loaded
 * from assets. The classifier returns a **calibrated** probability that the text is a scam, which the
 * fusion layer may use only to RAISE risk, never to lower it or silence a rule finding.
 */
interface MlClassifier {
    /** True when a usable model is loaded. When false, callers must behave exactly as the rules-only path. */
    val isAvailable: Boolean

    /**
     * Calibrated P(scam) in 0.0..1.0 for [text], or null when the model is unavailable or declines
     * to score this input. Must be pure and side-effect free; it runs on the analysis path.
     */
    fun scamProbability(text: String): Double?

    /** The inert default: no model, no effect. The app uses this until a model is bundled and enabled. */
    object Disabled : MlClassifier {
        override val isAvailable: Boolean = false
        override fun scamProbability(text: String): Double? = null
    }
}
