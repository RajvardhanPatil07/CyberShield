package io.cybershield.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    @Test
    fun removesPersonalDataButKeepsScamWording() {
        val input = "Dear Rahul, OTP 482913 for a/c XX4521. Card 4111 1111 1111 1111. Call 98765 43210 or mail rahul.k@gmail.com. " +
            "Pay to rahul@okaxis. PAN ABCDE1234F. Update KYC at https://sbi-kyc.xyz/verify?token=abc123&phone=9876543210"
        val out = Redactor.redact(input)
        listOf("482913", "4521", "4111", "98765", "rahul.k@gmail.com", "rahul@okaxis", "ABCDE1234F", "token=abc123", "9876543210")
            .forEach { assertFalse("leaked $it in: $out", out.contains(it)) }
        assertTrue(out.contains("Update KYC"))
        assertTrue(out.contains("https://sbi-kyc.xyz/verify"))
    }
}
