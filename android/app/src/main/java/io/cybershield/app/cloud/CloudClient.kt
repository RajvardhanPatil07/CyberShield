package io.cybershield.app.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Overall result of a reputation lookup. Only [MALICIOUS] means a provider confirmed a threat. */
enum class ReputationVerdict { MALICIOUS, SUSPICIOUS, NO_KNOWN_THREAT, UNAVAILABLE }

enum class ProviderStatus { MALICIOUS, SUSPICIOUS, CLEAN, UNKNOWN, ERROR, NOT_CONFIGURED, RATE_LIMITED }

data class ProviderResult(val name: String, val status: ProviderStatus, val threats: List<String>, val detail: String?)

data class ReputationReport(
    val verdict: ReputationVerdict,
    val providers: List<ProviderResult>,
    val expandedUrl: String?,
    val cached: Boolean,
)

data class AiAnalysis(
    val riskLevel: String,
    val scamType: String,
    val summary: String,
    val techniques: List<String>,
    val redFlags: List<String>,
    val safeSteps: List<String>,
    val confidence: String,
    val model: String?,
)

sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    /** Backend URL is empty or not allowed (release builds require HTTPS). */
    data object NotConfigured : CloudResult<Nothing>
    data object Offline : CloudResult<Nothing>
    data object RateLimited : CloudResult<Nothing>
    data class Failed(val code: String, val message: String) : CloudResult<Nothing>
}

/** Abstractions so screens do not depend on the transport or on a specific provider. */
interface ReputationService { suspend fun check(url: String): CloudResult<ReputationReport> }
interface AiExplainer { suspend fun explain(redactedText: String, kind: String, language: String, localFindings: List<String>): CloudResult<AiAnalysis> }

/**
 * Sends an opt-in, already-redacted scam-message sample to the backend's training corpus. The text
 * must be redacted on-device first (see [io.cybershield.app.security.Redactor]); the backend rejects
 * anything that still carries personal data.
 */
interface ContributionService {
    suspend fun contribute(redactedText: String, label: String, language: String, findings: List<String>): CloudResult<Unit>
}

/**
 * Talks to the CyberShield backend (backend/ in this repository). The backend holds every provider
 * API key; this client never sees one. Requests carry only the content the user chose to submit.
 */
class BackendClient(
    private val baseUrl: String,
    private val installId: String,
    private val requireHttps: Boolean,
    private val transport: Transport = HttpTransport(),
) : ReputationService, AiExplainer, ContributionService {

    fun interface Transport {
        /** Returns HTTP status and body. Throws IOException for network failures. */
        fun post(url: String, body: String, headers: Map<String, String>, timeoutMs: Int): Pair<Int, String>
    }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && (baseUrl.startsWith("https://") || !requireHttps && baseUrl.startsWith("http://"))

    override suspend fun check(url: String): CloudResult<ReputationReport> =
        call("/v1/url/check", JSONObject().put("url", url.take(2048)), 15_000) { parseReputation(it) }

    override suspend fun explain(redactedText: String, kind: String, language: String, localFindings: List<String>): CloudResult<AiAnalysis> =
        call(
            "/v1/ai/analyze",
            JSONObject().put("text", redactedText.take(4000)).put("kind", kind).put("language", language)
                .put("localFindings", JSONArray(localFindings.take(20))),
            35_000,
        ) { parseAi(it) }

    override suspend fun contribute(redactedText: String, label: String, language: String, findings: List<String>): CloudResult<Unit> =
        call(
            "/v1/contribute",
            JSONObject().put("text", redactedText.take(4000)).put("label", label).put("language", language)
                .put("findings", JSONArray(findings.take(20))),
            15_000,
        ) { /* success body is just a status + id; nothing to parse */ }

    private suspend fun <T> call(path: String, body: JSONObject, timeoutMs: Int, parse: (JSONObject) -> T): CloudResult<T> {
        if (!isConfigured) return CloudResult.NotConfigured
        return withContext(Dispatchers.IO) {
            try {
                val (status, text) = transport.post(
                    baseUrl.trimEnd('/') + path, body.toString(),
                    mapOf("Content-Type" to "application/json", "Accept" to "application/json", "X-CyberShield-Install" to installId),
                    timeoutMs,
                )
                val json = runCatching { JSONObject(text) }.getOrNull()
                when {
                    status == 429 -> CloudResult.RateLimited
                    status in 200..299 && json != null -> runCatching { CloudResult.Ok(parse(json)) }
                        .getOrElse { CloudResult.Failed("bad_response", "The server response could not be read.") }
                    else -> {
                        val err = json?.optJSONObject("error")
                        val code = err?.optString("code").orEmpty().ifEmpty { "http_$status" }
                        if (code == "not_configured") CloudResult.NotConfigured
                        else CloudResult.Failed(code, err?.optString("message").orEmpty().ifEmpty { "Server error ($status)." })
                    }
                }
            } catch (_: SocketTimeoutException) {
                CloudResult.Failed("timeout", "The server took too long to respond.")
            } catch (_: IOException) {
                CloudResult.Offline
            }
        }
    }

    companion object {
        fun parseReputation(json: JSONObject): ReputationReport {
            val providers = json.optJSONArray("providers") ?: JSONArray()
            return ReputationReport(
                verdict = when (json.getString("verdict")) {
                    "malicious" -> ReputationVerdict.MALICIOUS
                    "suspicious" -> ReputationVerdict.SUSPICIOUS
                    "no_known_threat" -> ReputationVerdict.NO_KNOWN_THREAT
                    else -> ReputationVerdict.UNAVAILABLE
                },
                providers = (0 until providers.length()).map { i ->
                    val p = providers.getJSONObject(i)
                    ProviderResult(
                        name = p.optString("name"),
                        status = ProviderStatus.entries.firstOrNull { it.name.equals(p.optString("status"), true) } ?: ProviderStatus.UNKNOWN,
                        threats = p.optJSONArray("threats").strings(),
                        detail = p.optString("detail").ifEmpty { null },
                    )
                },
                expandedUrl = json.optString("expandedUrl").ifEmpty { null }.takeUnless { json.isNull("expandedUrl") },
                cached = json.optBoolean("cached", false),
            )
        }

        fun parseAi(json: JSONObject): AiAnalysis {
            val a = json.getJSONObject("analysis")
            return AiAnalysis(
                riskLevel = a.getString("riskLevel"),
                scamType = a.optString("scamType"),
                summary = a.getString("summary"),
                techniques = a.optJSONArray("techniques").strings(),
                redFlags = a.optJSONArray("redFlags").strings(),
                safeSteps = a.optJSONArray("safeSteps").strings(),
                confidence = a.optString("confidence", "low"),
                model = json.optString("model").ifEmpty { null },
            )
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.take(10)
    }
}

class HttpTransport : BackendClient.Transport {
    override fun post(url: String, body: String, headers: Map<String, String>, timeoutMs: Int): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            // Responses are small JSON documents; cap the read to protect against a misbehaving server.
            val text = stream?.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (out.size() < MAX_RESPONSE_BYTES) {
                    val n = s.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES - out.size()))
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }.orEmpty()
            return status to text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
