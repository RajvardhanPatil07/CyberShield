package io.cybershield.app.security

import io.cybershield.app.ml.MlClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fusion contract (docs/AI_ML_PLAN.md §3): the model may only RAISE risk, never lower it or remove a
 * rule finding, and any raise is explained by an ML_PATTERN_MATCH finding. Disabled must be a no-op.
 */
class MlFusionTest {
    private fun classifier(prob: Double?) = object : MlClassifier {
        override val isAvailable = prob != null
        override fun scamProbability(text: String): Double? = prob
    }

    @Test fun disabledClassifierLeavesAssessmentUnchanged() {
        val text = "Hi, are we still meeting at 5pm?"
        val rulesOnly = ScamLens.analyze(text)
        val fused = RiskAnalyzer.message(text, classifier = MlClassifier.Disabled)
        assertEquals(rulesOnly.score, fused.score)
        assertEquals(rulesOnly.level, fused.level)
        assertEquals(rulesOnly.findings.map { it.code }, fused.findings.map { it.code })
    }

    @Test fun modelRaisesLowRuleScoreAndExplainsIt() {
        // A benign-looking message the rules score LOW; the model is confident it is a scam.
        val base = ScamLens.analyze("Please review the attached note when you get a moment.")
        assertEquals(RiskLevel.LOW, base.level)
        val fused = MlFusion.fuse(base, mlProb = 0.9)
        assertEquals(90, fused.score)
        assertEquals(RiskLevel.HIGH, fused.level)
        assertTrue(fused.findings.any { it.code == FindingCode.ML_PATTERN_MATCH })
        assertTrue(fused.reasons.any { it == FindingCode.ML_PATTERN_MATCH.english })
    }

    @Test fun modelNeverLowersAStrongRuleScore() {
        val base = ScamLens.analyze("Share your OTP and UPI PIN now or your account will be blocked.")
        assertTrue(base.score >= 60)
        val fused = MlFusion.fuse(base, mlProb = 0.10)
        assertEquals(base.score, fused.score)
        assertEquals(base.level, fused.level)
        assertFalse(fused.findings.any { it.code == FindingCode.ML_PATTERN_MATCH })
    }

    @Test fun equalScoreIsNotTreatedAsARaise() {
        val base = ScamLens.analyze("Lunch tomorrow?")
        val fused = MlFusion.fuse(base, mlProb = base.score / 100.0)
        assertSame(base, fused)
    }

    @Test fun probabilityIsClampedToValidRange() {
        val base = ScamLens.analyze("hello")
        assertEquals(100, MlFusion.fuse(base, mlProb = 2.0).score)
        assertSame(base, MlFusion.fuse(base, mlProb = -1.0))
    }
}
