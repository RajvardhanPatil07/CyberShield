package io.cybershield.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.cybershield.app.data.AppSettings
import io.cybershield.app.data.ScanHistoryRepository
import io.cybershield.app.ml.MlClassifierFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * End-to-end checks of the on-device ML feature on a device or emulator:
 * the bundled model loads, ML is off by default, the Settings toggle turns it on and raises a scam the
 * rules miss, and a donated sample reaches a local backend. The donation test is skipped unless a
 * backend with CONTRIBUTIONS_DIR is reachable at the debug address (http://10.0.2.2:8787).
 */
@RunWith(AndroidJUnit4::class)
class MlFlowsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A holdout scam the rules score LOW but the model scores 0.96. */
    private val missedByRules = "You are the lucky winner of a Tata Safari in our anniversary lucky draw. Pay Rs 8,000 GST to receive your car."

    @Before
    fun reset() {
        AppSettings(context).resetAll()
        ScanHistoryRepository.create(context).clear()
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "ml-e2e").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun analyse(text: String) {
        compose.onNodeWithTag("feature_message").performClick()
        compose.onNodeWithTag("message_input").performTextInput(text)
        compose.onNodeWithTag("analyse_message").performClick()
    }

    @Test
    fun bundledModelLoadsOnDeviceAndIsFast() {
        val clf = MlClassifierFactory.create(context)
        assertTrue("model did not load from APK assets", clf.isAvailable)
        val p = clf.scamProbability(missedByRules)
        assertTrue("expected the model to fire on a known scam, got $p", p != null && p > 0.9)
        repeat(20) { clf.scamProbability(missedByRules) }
        val start = System.nanoTime()
        repeat(200) { clf.scamProbability(missedByRules) }
        val ms = (System.nanoTime() - start) / 1e6 / 200
        Log.i("MlFlowsTest", "on-device ML latency: $ms ms per message")
        assertTrue("too slow on device: $ms ms", ms < 100)
    }

    @Test
    fun mlIsOffByDefaultSoRulesDecide() {
        ActivityScenario.launch(MainActivity::class.java).use {
            analyse(missedByRules)
            compose.onNodeWithText("No obvious red flags").assertIsDisplayed()
            screenshot("1-ml-off")
        }
    }

    @Test
    fun settingsToggleTurnsOnMlAndRaisesRiskWithAReason() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("open_settings").performClick()
            compose.onNodeWithTag("ml_toggle").performScrollTo().performClick()
            screenshot("2-settings-ml-on")
            assertTrue("toggle did not persist", AppSettings(context).mlAssist)
            compose.onNodeWithTag("nav_home").performClick()
            analyse(missedByRules)
            compose.onNodeWithText("High risk signals").assertIsDisplayed()
            screenshot("3-ml-on-result")
            compose.onNodeWithText("Its wording resembles known scams", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("the experimental AI model", substring = true).performScrollTo().assertIsDisplayed()
            screenshot("4-ml-reason")
        }
    }

    @Test
    fun donatedSampleReachesTheBackend() {
        assumeTrue("no local backend with contributions at 10.0.2.2:8787", backendAcceptsContributions())
        AppSettings(context).mlAssist = true
        ActivityScenario.launch(MainActivity::class.java).use {
            analyse(missedByRules)
            compose.onNodeWithTag("donate_sample").performScrollTo().performClick()
            compose.onNodeWithText("Send this redacted message", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Don't ask again before donating", substring = true).assertIsDisplayed()
            screenshot("5-donate-consent")
            compose.onNodeWithText("Send").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasText("Your redacted sample was sent", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("6-donate-sent")
        }
    }

    private fun backendAcceptsContributions(): Boolean = runCatching {
        val c = URL("http://10.0.2.2:8787/health").openConnection() as HttpURLConnection
        c.connectTimeout = 2_000
        c.readTimeout = 2_000
        c.inputStream.bufferedReader().readText().contains("\"contributions\":\"configured\"")
    }.getOrDefault(false)
}
