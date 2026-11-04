package io.cybershield.app.security

import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAnalyzerTest {
    @Test
    fun flagsUrgentAccountThreat() {
        val result = RiskAnalyzer.message("Your account will be blocked today. Verify immediately.")
        assertTrue(result.score >= 25)
        assertTrue(result.reasons.any { it.contains("urgency") })
    }

    @Test
    fun flagsCredentialRequests() {
        val result = RiskAnalyzer.message("Send your OTP and UPI PIN to claim refund")
        assertTrue(result.reasons.any { it.contains("payment secrets") })
    }

    @Test
    fun flagsHttpAndSuspiciousHostTerms() {
        val result = RiskAnalyzer.url("http://kyc-verify-example.com/login")
        assertTrue(result.reasons.any { it.contains("HTTPS") })
        assertTrue(result.reasons.any { it.contains("impersonation") })
    }

    @Test
    fun flagsUserInfoHostConfusion() {
        val result = RiskAnalyzer.url("https://trusted.example@evil.example/path")
        assertTrue(result.reasons.any { it.contains("user-info") })
    }

    @Test
    fun rejectsAddressWithoutHost() {
        val result = RiskAnalyzer.url("not-a-valid-url")
        assertTrue(result.reasons.any { it.contains("Could not parse") })
    }
}
