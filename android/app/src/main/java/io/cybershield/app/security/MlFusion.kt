package io.cybershield.app.security

import kotlin.math.roundToInt

/**
 * Combines the rule-based [RiskAssessment] with a calibrated ML scam probability.
 *
 * Fusion rule (docs/AI_ML_PLAN.md §3), intentionally conservative in v1:
 * the model may only **raise** the score, never lower it and never remove a rule finding. This closes
 * the recall gap on novel wording while making it structurally impossible for the model to hide a scam
 * the rules already caught. When the model raises the level, an explainable [FindingCode.ML_PATTERN_MATCH]
 * finding is attached so the user never sees an unexplained neural verdict.
 */
object MlFusion {
    /**
     * @param base the rules-only assessment from [ScamLens].
     * @param mlProb calibrated P(scam) in 0.0..1.0.
     * @return [base] unchanged when the model does not exceed the rule score, otherwise a copy whose
     *   score, level and verdict come from the model and whose findings/reasons gain the ML signal.
     */
    fun fuse(base: RiskAssessment, mlProb: Double): RiskAssessment {
        val mlScore = (mlProb.coerceIn(0.0, 1.0) * 100).roundToInt()
        if (mlScore <= base.score) return base

        val level = RiskLevel.of(mlScore)
        val findings = (base.findings + Finding(FindingCode.ML_PATTERN_MATCH))
            .distinctBy { it.code to it.evidence }
        // Reasons mirror RiskAssessment.from: one line per distinct finding code, plus the ML line.
        val reasons = base.findings.map { it.code }.distinct().map { it.english } + FindingCode.ML_PATTERN_MATCH.english

        // "No strong red flags were found" would contradict a raised verdict, so replace it with real advice.
        val recommendations = (base.recommendations - Recommendation.STAY_CAUTIOUS)
            .ifEmpty { listOf(Recommendation.VERIFY_OFFICIAL_CHANNEL, Recommendation.REPORT_CHAKSHU) }

        return base.copy(
            score = mlScore,
            level = level,
            verdict = RiskAssessment.verdictFor(level),
            findings = findings,
            reasons = reasons,
            recommendations = recommendations,
        )
    }
}
