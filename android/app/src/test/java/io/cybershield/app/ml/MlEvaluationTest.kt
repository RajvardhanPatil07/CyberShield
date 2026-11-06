package io.cybershield.app.ml

import io.cybershield.app.security.RiskAnalyzer
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScamLens
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Measures what the bundled on-device model actually changes when ML assist is on: the same labelled
 * message sets as EvaluationTest, scored rules-only and rules+ML (MlFusion). The dev set was used to
 * train the model, so only the holdout figures are a fair estimate. Writes build/reports/eval/ml.md.
 */
class MlEvaluationTest {
    private data class Row(val id: String, val scam: Boolean, val lang: String, val text: String)

    private fun load(name: String): List<Row> =
        javaClass.classLoader!!.getResourceAsStream("eval/$name")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("id\t") }
            .map { it.split('\t') }
            .map { Row(it[0], it[1] == "scam", it[2], it[3].replace("\\n", "\n")) }

    private val classifier = LogisticTextClassifier.fromJson(File("src/main/assets/ml/scam_classifier.json").readText())

    private data class Counts(var tp: Int = 0, var fp: Int = 0, var tn: Int = 0, var fn: Int = 0) {
        fun recall() = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
        fun fpr() = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
    }

    private fun pct(v: Double) = "%.1f%%".format(java.util.Locale.ROOT, v * 100)

    @Test fun fusedVersusRulesOnly() {
        val report = StringBuilder("| Set | Rules recall | Rules FPR | Rules+ML recall | Rules+ML FPR |\n|---|---|---|---|---|\n")
        val details = StringBuilder()
        for (set in listOf("messages-dev.tsv", "messages-holdout.tsv")) {
            val rules = Counts()
            val fused = Counts()
            for (r in load(set)) {
                val ruleFlag = ScamLens.analyze(r.text).level != RiskLevel.LOW
                val f = RiskAnalyzer.message(r.text, classifier = classifier)
                val fusedFlag = f.level != RiskLevel.LOW
                listOf(rules to ruleFlag, fused to fusedFlag).forEach { (c, flagged) ->
                    when {
                        r.scam && flagged -> c.tp++
                        r.scam -> c.fn++
                        flagged -> c.fp++
                        else -> c.tn++
                    }
                }
                if (ruleFlag != fusedFlag) {
                    val kind = if (r.scam) "CAUGHT by ML" else "FALSE ALARM by ML"
                    details.append("- $set $kind ${r.id} (${r.lang}, p=${"%.2f".format(java.util.Locale.ROOT, classifier.rawProbability(r.text))}): ${r.text.take(90)}\n")
                }
                // Fusion may only raise: anything the rules flag must stay flagged.
                assertTrue("ML lowered a rule hit: ${r.id}", !ruleFlag || fusedFlag)
            }
            report.append("| $set | ${pct(rules.recall())} | ${pct(rules.fpr())} | ${pct(fused.recall())} | ${pct(fused.fpr())} |\n")
        }
        val text = report.toString() + "\nChanges caused by ML:\n" + details.ifEmpty { "- none\n" }
        println(text)
        File("build/reports/eval").apply { mkdirs() }.resolve("ml.md").writeText(text)
    }

    @Test fun latencyIsWithinBudget() {
        val texts = load("messages-holdout.tsv").map { it.text }
        texts.forEach { classifier.rawProbability(it) } // warm-up
        val start = System.nanoTime()
        repeat(5) { texts.forEach { classifier.rawProbability(it) } }
        val perMessageMs = (System.nanoTime() - start) / 1e6 / (5 * texts.size)
        println("ML latency on JVM: ${"%.3f".format(java.util.Locale.ROOT, perMessageMs)} ms per message")
        assertTrue("too slow: $perMessageMs ms", perMessageMs < 100)
    }
}
