package io.cybershield.app.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun pct(v: Double) = "%.1f%%".format(java.util.Locale.ROOT, v * 100)

/**
 * Measures the local engines on the labelled sets in src/test/resources/eval.
 *
 * A sample counts as flagged when its level is MEDIUM or HIGH (score >= 30), which is when the app
 * shows a warning. "dev" files may be used to tune rules; "holdout" files must not be, so their
 * numbers are the honest estimate. Both sets were written for this project (see docs/EVALUATION.md),
 * so the figures describe these patterns, not real-world accuracy.
 *
 * The floors below stop regressions. Raise them when the engines improve; never lower them to make a change pass.
 */
class EvaluationTest {
    private data class Sample(val id: String, val scam: Boolean, val lang: String, val input: String)

    private data class Metrics(val name: String, val tp: Int, val fp: Int, val tn: Int, val fn: Int, val misses: List<String>) {
        val precision get() = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
        val recall get() = if (tp + fn == 0) 1.0 else tp.toDouble() / (tp + fn)
        val falsePositiveRate get() = if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn)
        val f1 get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        fun row() = "| $name | ${tp + fn} | ${fp + tn} | ${pct(precision)} | ${pct(recall)} | ${pct(falsePositiveRate)} | ${pct(f1)} |"
    }

    private fun load(name: String, hasLanguage: Boolean): List<Sample> {
        val stream = javaClass.classLoader!!.getResourceAsStream("eval/$name") ?: error("missing eval/$name")
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("id\t") }
            .map { line ->
                val cols = line.split('\t')
                val expected = if (hasLanguage) 4 else 3
                require(cols.size == expected) { "$name: expected $expected columns in: $line" }
                require(cols[1] == "scam" || cols[1] == "legit") { "$name: bad label in: $line" }
                Sample(cols[0], cols[1] == "scam", if (hasLanguage) cols[2] else "-", cols.last().replace("\\n", "\n"))
            }
            .also { samples -> require(samples.map { it.id }.toSet().size == samples.size) { "$name: duplicate ids" } }
    }

    private fun measure(name: String, samples: List<Sample>, assess: (String) -> RiskAssessment): Metrics {
        var tp = 0; var fp = 0; var tn = 0; var fn = 0
        val misses = mutableListOf<String>()
        for (s in samples) {
            val r = assess(s.input)
            val flagged = r.level != RiskLevel.LOW
            when {
                s.scam && flagged -> tp++
                s.scam -> { fn++; misses += "missed ${s.id} (${s.lang}, score ${r.score}): ${s.input.take(90)}" }
                flagged -> { fp++; misses += "false alarm ${s.id} (${s.lang}, score ${r.score}, ${r.findings.map { it.code }.distinct()}): ${s.input.take(90)}" }
                else -> tn++
            }
        }
        return Metrics(name, tp, fp, tn, fn, misses)
    }

    private val message = { text: String -> ScamLens.analyze(text) }
    private val url = { text: String -> LinkGuard.analyze(text) }
    private val qr = { text: String -> QrGuard.analyze(text).assessment }

    private val results by lazy {
        listOf(
            measure("Messages (dev)", load("messages-dev.tsv", true), message),
            measure("Messages (holdout)", load("messages-holdout.tsv", true), message),
            measure("Links (dev)", load("urls-dev.tsv", false), url),
            measure("Links (holdout)", load("urls-holdout.tsv", false), url),
            measure("QR codes (dev)", load("qr-dev.tsv", false), qr),
            measure("QR codes (holdout)", load("qr-holdout.tsv", false), qr),
        ).also { writeReport(it) }
    }

    private fun writeReport(metrics: List<Metrics>) {
        val text = buildString {
            appendLine("| Set | Scams | Legit | Precision | Recall | False-positive rate | F1 |")
            appendLine("|---|---|---|---|---|---|---|")
            metrics.forEach { appendLine(it.row()) }
            metrics.filter { it.misses.isNotEmpty() }.forEach { m ->
                appendLine()
                appendLine("${m.name}:")
                m.misses.forEach { appendLine("- $it") }
            }
        }
        println(text)
        File("build/reports/eval").apply { mkdirs() }.resolve("summary.md").writeText(text)
    }

    private fun floor(name: String, minRecall: Double, maxFalsePositiveRate: Double) {
        val m = results.first { it.name == name }
        assertTrue("$name recall ${pct(m.recall)} < ${pct(minRecall)}\n${m.misses.joinToString("\n")}", m.recall >= minRecall)
        assertTrue("$name false-positive rate ${pct(m.falsePositiveRate)} > ${pct(maxFalsePositiveRate)}\n${m.misses.joinToString("\n")}",
            m.falsePositiveRate <= maxFalsePositiveRate)
    }

    // Floors match the results recorded in docs/EVALUATION.md (5 Nov 2026).
    @Test fun messagesDev() = floor("Messages (dev)", minRecall = 1.0, maxFalsePositiveRate = 0.0)
    @Test fun messagesHoldout() = floor("Messages (holdout)", minRecall = 0.80, maxFalsePositiveRate = 0.0)
    @Test fun linksDev() = floor("Links (dev)", minRecall = 1.0, maxFalsePositiveRate = 0.0)
    @Test fun linksHoldout() = floor("Links (holdout)", minRecall = 0.86, maxFalsePositiveRate = 0.0)
    @Test fun qrDev() = floor("QR codes (dev)", minRecall = 1.0, maxFalsePositiveRate = 0.0)
    @Test fun qrHoldout() = floor("QR codes (holdout)", minRecall = 1.0, maxFalsePositiveRate = 0.0)
}
