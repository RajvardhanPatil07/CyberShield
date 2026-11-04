package io.cybershield.app.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BackendClientTest {
    private fun client(base: String = "https://api.example", requireHttps: Boolean = true, transport: BackendClient.Transport) =
        BackendClient(base, "install-1", requireHttps, transport)

    @Test
    fun parsesConfirmedMaliciousReport() = runBlocking {
        var sentHeaders: Map<String, String> = emptyMap()
        val c = client(transport = { _, _, headers, _ ->
            sentHeaders = headers
            200 to """{"verdict":"malicious","expandedUrl":null,"cached":false,"providers":[
                {"name":"google_safe_browsing","status":"malicious","threats":["SOCIAL_ENGINEERING"]},
                {"name":"virustotal","status":"not_configured","threats":[]}]}"""
        })
        val result = c.check("http://phish.example") as CloudResult.Ok
        assertEquals(ReputationVerdict.MALICIOUS, result.value.verdict)
        assertEquals(ProviderStatus.MALICIOUS, result.value.providers[0].status)
        assertEquals(listOf("SOCIAL_ENGINEERING"), result.value.providers[0].threats)
        assertEquals(ProviderStatus.NOT_CONFIGURED, result.value.providers[1].status)
        assertEquals("install-1", sentHeaders["X-CyberShield-Install"])
        assertTrue(sentHeaders.keys.none { it.contains("key", ignoreCase = true) })
    }

    @Test
    fun releaseRefusesPlainHttpBackend() = runBlocking {
        val c = client(base = "http://10.0.2.2:8787", requireHttps = true, transport = { _, _, _, _ -> error("must not be called") })
        assertEquals(CloudResult.NotConfigured, c.check("https://x.example"))
        assertEquals(CloudResult.NotConfigured, client(base = "", transport = { _, _, _, _ -> error("no") }).check("x"))
    }

    @Test
    fun mapsFailures() = runBlocking {
        assertEquals(CloudResult.Offline, client(transport = { _, _, _, _ -> throw IOException("no route") }).check("x"))
        assertEquals(CloudResult.RateLimited, client(transport = { _, _, _, _ -> 429 to """{"error":{"code":"rate_limited"}}""" }).check("x"))
        assertEquals(CloudResult.NotConfigured, client(transport = { _, _, _, _ -> 503 to """{"error":{"code":"not_configured","message":"x"}}""" })
            .explain("t", "message", "en", emptyList()))
        val failed = client(transport = { _, _, _, _ -> 200 to "not json" }).check("x")
        assertTrue(failed is CloudResult.Failed)
    }

    @Test
    fun parsesAiAnalysis() = runBlocking {
        val c = client(transport = { _, body, _, _ ->
            assertTrue(body.contains("\"kind\":\"message\""))
            200 to """{"model":"m","analysis":{"riskLevel":"high","scamType":"Fake KYC","summary":"s","techniques":["urgency"],
                "redFlags":["link"],"safeSteps":["ignore"],"confidence":"medium"}}"""
        })
        val result = c.explain("[NUMBER] is your code", "message", "hi", listOf("OTP_REQUEST")) as CloudResult.Ok
        assertEquals("high", result.value.riskLevel)
        assertEquals(listOf("ignore"), result.value.safeSteps)
    }
}
