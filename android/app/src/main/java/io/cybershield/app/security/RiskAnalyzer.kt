package io.cybershield.app.security

/**
 * Stable entry point kept from the original MVP. Delegates to the dedicated engines:
 * [ScamLens] for messages, [LinkGuard] for URLs and [QrGuard] for QR payloads.
 */
object RiskAnalyzer {
    fun message(text: String): RiskAssessment = ScamLens.analyze(text)

    fun url(raw: String): RiskAssessment = LinkGuard.analyze(raw)

    fun qr(raw: String?): QrAnalysis = QrGuard.analyze(raw)
}
