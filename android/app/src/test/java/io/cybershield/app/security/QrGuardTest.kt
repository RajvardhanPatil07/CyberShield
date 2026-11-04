package io.cybershield.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrGuardTest {
    private fun QrAnalysis.has(code: FindingCode) = assessment.findings.any { it.code == code }

    @Test
    fun normalWebsiteQr() {
        val result = QrGuard.analyze("https://www.irctc.co.in/nget/train-search")
        assertEquals(QrContentType.WEB_URL, result.type)
        assertEquals(RiskLevel.LOW, result.assessment.level)
        assertEquals("https://www.irctc.co.in/nget/train-search", result.openTarget)
    }

    @Test
    fun suspiciousUrlQr() {
        val result = QrGuard.analyze("http://paytm-cashback-claim.top/receive?amt=5000")
        assertEquals(QrContentType.WEB_URL, result.type)
        assertEquals(RiskLevel.HIGH, result.assessment.level)
        assertTrue(result.has(FindingCode.URL_BRAND_IMPERSONATION))
    }

    @Test
    fun normalUpiPaymentUri() {
        val result = QrGuard.analyze("upi://pay?pa=sharmastores@okicici&pn=Sharma%20General%20Stores&cu=INR")
        assertEquals(QrContentType.UPI_PAYMENT, result.type)
        val upi = assertNotNull(result.upi).let { result.upi!! }
        assertEquals("sharmastores@okicici", upi.payeeAddress)
        assertEquals("Sharma General Stores", upi.payeeName)
        assertEquals("@okicici", upi.handle)
        assertTrue(result.has(FindingCode.UPI_PAYMENT))
        assertEquals(RiskLevel.LOW, result.assessment.level)
        assertNotNull(result.openTarget)
    }

    @Test
    fun suspiciousUpiPaymentUri() {
        val result = QrGuard.analyze("upi://pay?pa=refund.desk99@axl&pn=SBI%20Customer%20Care&am=24999&tn=Receive%20your%20KYC%20refund&cu=INR")
        assertTrue(result.has(FindingCode.UPI_SUSPICIOUS_NOTE))
        assertTrue(result.has(FindingCode.UPI_IMPERSONATION_NAME))
        assertTrue(result.has(FindingCode.UPI_LARGE_AMOUNT))
        assertEquals(RiskLevel.HIGH, result.assessment.level)
        assertTrue(Recommendation.PIN_ONLY_TO_PAY in result.assessment.recommendations)
    }

    @Test
    fun upiCollectAndBadPayee() {
        assertTrue(QrGuard.analyze("upi://collect?pa=x@ybl&am=100").has(FindingCode.UPI_COLLECT_REQUEST))
        val missing = QrGuard.analyze("upi://pay?pn=Someone&am=10")
        assertTrue(missing.has(FindingCode.UPI_MISSING_PAYEE))
        assertNull(missing.openTarget)
        assertTrue(QrGuard.analyze("upi://pay?pa=not a vpa").has(FindingCode.UPI_INVALID_VPA))
        assertTrue(QrGuard.analyze("upi://pay?pa=abc@weirdpsp&cu=USD").has(FindingCode.UPI_NON_INR))
    }

    @Test
    fun invalidQrContent() {
        assertEquals(QrContentType.INVALID, QrGuard.analyze("").type)
        assertEquals(QrContentType.INVALID, QrGuard.analyze(null).type)
        val text = QrGuard.analyze("Table 12 - Wi-Fi password at counter")
        assertEquals(QrContentType.TEXT, text.type)
        assertNull(text.openTarget)
    }

    @Test
    fun otherPayloadsAreClassified() {
        assertEquals(QrContentType.WIFI, QrGuard.analyze("WIFI:T:WPA;S:Cafe;P:secret;;").type)
        assertEquals(QrContentType.PHONE, QrGuard.analyze("tel:+911234567890").type)
        val intent = QrGuard.analyze("intent://scan/#Intent;scheme=upi;end")
        assertEquals(RiskLevel.HIGH, intent.assessment.level)
        assertNull(intent.openTarget)
    }

    @Test
    fun scamTextInQrIsAnalysed() {
        val result = QrGuard.analyze("Scan complete. Enter UPI PIN to receive Rs 2000 cashback")
        assertEquals(QrContentType.TEXT, result.type)
        assertTrue(result.has(FindingCode.RECEIVE_MONEY_TRICK))
    }
}
