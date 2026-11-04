package io.cybershield.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.cybershield.app.security.FindingCode
import io.cybershield.app.security.QrContentType
import io.cybershield.app.security.QrGuard
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScamLens
import io.cybershield.app.security.ScanKind
import io.cybershield.app.vision.ImageReaders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs the bundled ML Kit models on-device against the demo images in androidTest/assets. */
@RunWith(AndroidJUnit4::class)
class ImageReadersTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asset(name: String): Uri {
        val file = File(target.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    @Test
    fun ocrReadsMixedHindiEnglishScreenshotAndFlagsIt() = runBlocking {
        val text = ImageReaders.readText(target, asset("screenshot-hindi-kyc-scam.png"))
        println("OCR_RESULT>>>$text<<<")
        assertTrue("OCR found no Devanagari: $text", text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.DEVANAGARI })
        assertTrue("OCR missed the URL: $text", text.contains("sbi-kyc-update.xyz", ignoreCase = true))
        val result = ScamLens.analyze(text, ScanKind.SCREENSHOT)
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(result.findings.any { it.code == FindingCode.SUSPICIOUS_LINK })
    }

    @Test
    fun qrIsDecodedFromImages() = runBlocking {
        val upi = QrGuard.analyze(ImageReaders.readQr(target, asset("qr-upi-scam.png")))
        assertEquals(QrContentType.UPI_PAYMENT, upi.type)
        assertEquals("refund.desk99@axl", upi.upi?.payeeAddress)
        assertEquals(RiskLevel.HIGH, upi.assessment.level)

        val web = QrGuard.analyze(ImageReaders.readQr(target, asset("qr-safe-link.png")))
        assertEquals(QrContentType.WEB_URL, web.type)
        assertEquals(RiskLevel.LOW, web.assessment.level)
    }
}
