package io.cybershield.app.security

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes the Kotlin engine's results for the eval sets and web/test/fixtures/extra-cases.tsv to
 * web/test/fixtures/kotlin-reference.json. The web demo's test (web/test/parity.test.js) checks that its
 * JavaScript port of the engine gives exactly the same results.
 *
 * Skipped unless CYBERSHIELD_WRITE_WEB_FIXTURE=1. Regenerate after changing any rule:
 *   CYBERSHIELD_WRITE_WEB_FIXTURE=1 ./gradlew :app:testDebugUnitTest --tests '*WebReferenceTest*'
 */
class WebReferenceTest {
    private val fixtures = File("../../web/test/fixtures")

    private fun lines(text: String) = text.lines().filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("id\t") }

    private fun evalCases(name: String, kind: String): List<Triple<String, String, String>> {
        val text = javaClass.classLoader!!.getResourceAsStream("eval/$name")!!.bufferedReader().readText()
        return lines(text).filter { it.isNotBlank() }.map { line ->
            val cols = line.split('\t')
            Triple(cols[0], kind, cols.last().replace("\\n", "\n"))
        }
    }

    private fun assessmentJson(r: RiskAssessment): JSONObject = JSONObject()
        .put("score", r.score)
        .put("level", r.level.name)
        .put("language", r.language.name)
        .put("findings", JSONArray(r.findings.map { f ->
            JSONObject().put("code", f.code.name).put("evidence", f.evidence ?: JSONObject.NULL)
                .put("start", f.range?.first ?: JSONObject.NULL).put("end", f.range?.let { it.last + 1 } ?: JSONObject.NULL)
                .put("extraWeight", f.extraWeight)
        }))
        .put("recommendations", JSONArray(r.recommendations.map { it.name }))
        .put("links", JSONArray(r.links.map { (url, a) -> JSONObject().put("url", url).put("score", a.score) }))

    @Test
    fun writeReference() {
        assumeTrue(System.getenv("CYBERSHIELD_WRITE_WEB_FIXTURE") == "1")
        val cases = mutableListOf<Triple<String, String, String>>()
        listOf("messages-dev.tsv", "messages-holdout.tsv").forEach { cases += evalCases(it, "message") }
        listOf("urls-dev.tsv", "urls-holdout.tsv").forEach { cases += evalCases(it, "url") }
        listOf("qr-dev.tsv", "qr-holdout.tsv").forEach { cases += evalCases(it, "qr") }
        lines(File(fixtures, "extra-cases.tsv").readText()).forEachIndexed { i, line ->
            val kind = line.substringBefore('\t')
            cases += Triple("x-%03d".format(i + 1), kind, line.substringAfter('\t').replace("\\n", "\n"))
        }
        val out = JSONArray()
        for ((id, kind, input) in cases) {
            val item = JSONObject().put("id", id).put("kind", kind).put("input", input)
            when (kind) {
                "message" -> item.put("result", assessmentJson(ScamLens.analyze(input)))
                "url" -> item.put("result", assessmentJson(LinkGuard.analyze(input)))
                "qr" -> QrGuard.analyze(input).let { q ->
                    item.put("result", assessmentJson(q.assessment)).put("qrType", q.type.name)
                        .put("openTarget", q.openTarget ?: JSONObject.NULL).put("summary", q.summary)
                }
                else -> error("unknown kind $kind")
            }
            if (kind == "message") item.put("redacted", Redactor.redact(input)).put("report", Redactor.forReport(input))
            out.put(item)
        }
        File(fixtures, "kotlin-reference.json").writeText(out.toString(1) + "\n")
    }
}
