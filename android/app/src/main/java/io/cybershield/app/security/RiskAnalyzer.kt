package io.cybershield.app.security

import io.cybershield.app.ml.MlClassifier

/**
 * Stable entry point kept from the original MVP. Delegates to the dedicated engines:
 * [ScamLens] for messages, [LinkGuard] for URLs and [QrGuard] for QR payloads.
 *
 * Messages optionally pass through [MlFusion] when an on-device [MlClassifier] is supplied and
 * available. The default [MlClassifier.Disabled] keeps the rules-only behaviour, so existing callers
 * and the web/parity engines are unaffected.
 */
object RiskAnalyzer {
    fun message(
        text: String,
        kind: ScanKind = ScanKind.MESSAGE,
        classifier: MlClassifier = MlClassifier.Disabled,
    ): RiskAssessment {
        val base = ScamLens.analyze(text, kind)
        val prob = classifier.scamProbability(text) ?: return base
        return MlFusion.fuse(base, prob)
    }

    fun url(raw: String): RiskAssessment = LinkGuard.analyze(raw)

    fun qr(raw: String?): QrAnalysis = QrGuard.analyze(raw)
}
