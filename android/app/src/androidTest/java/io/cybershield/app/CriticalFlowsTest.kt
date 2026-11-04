package io.cybershield.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cybershield.app.data.ScanHistoryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end UI checks on a device or emulator. Run: ./gradlew :app:connectedDebugAndroidTest */
@RunWith(AndroidJUnit4::class)
class CriticalFlowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearHistory() {
        ScanHistoryRepository.create(context).clear()
    }

    @Test
    fun scamMessageIsFlaggedHighRiskAndSavedWithoutContent() {
        compose.onNodeWithTag("feature_message").performClick()
        compose.onNodeWithTag("message_input").performTextInput(
            "Your SBI account will be blocked today. Share OTP immediately and update KYC at http://sbi-kyc-update.xyz/login"
        )
        compose.onNodeWithTag("analyse_message").performClick()
        compose.onNodeWithText("High risk signals").assertIsDisplayed()
        compose.onNodeWithText("Suspicious phrases").performScrollTo().assertIsDisplayed()

        val records = ScanHistoryRepository.create(context).all()
        assertEquals(1, records.size)
        val stored = context.getSharedPreferences("cybershield_local_history", Context.MODE_PRIVATE).all.toString()
        assertFalse("history must not contain message text", stored.contains("blocked today") || stored.contains("sbi-kyc-update"))
    }

    @Test
    fun linkGuardFlagsImpersonationAndAsksBeforeOpening() {
        compose.onNodeWithTag("feature_link").performClick()
        compose.onNodeWithTag("link_input").performTextInput("https://hdfcbank-kyc-update.xyz/verify")
        compose.onNodeWithTag("check_link").performClick()
        compose.onNodeWithText("High risk signals").assertIsDisplayed()
        compose.onNodeWithText("Open link").performScrollTo().performClick()
        compose.onNodeWithText("Open this link?").assertIsDisplayed()
        compose.onNodeWithText("I understand the risk").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun genuineBankOtpMessageIsLowRisk() {
        compose.onNodeWithTag("feature_message").performClick()
        compose.onNodeWithTag("message_input").performTextInput("482913 is your OTP for Rs 1,250 at AMAZON. Do not share it with anyone. -HDFC Bank")
        compose.onNodeWithTag("analyse_message").performClick()
        compose.onNodeWithText("No obvious red flags").assertIsDisplayed()
    }

    @Test
    fun historyCanBeCleared() {
        compose.onNodeWithTag("feature_link").performClick()
        compose.onNodeWithTag("link_input").performTextInput("http://192.168.1.5/login")
        compose.onNodeWithTag("check_link").performClick()
        compose.onNodeWithTag("nav_history").performClick()
        compose.onAllNodesWithText("Link", substring = true).fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithTag("clear_history").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { ScanHistoryRepository.create(context).all().isEmpty() }
    }
}

/** Share-to-scan: launches MainActivity the way the Android Sharesheet does. */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @get:Rule
    val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    @Test
    fun sharedTextOpensScamLens() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Congratulations! You have won a lottery. Pay registration fee to claim your prize.")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithText("Shared content loaded. Review it, then analyse.").assertIsDisplayed()
            compose.onNodeWithTag("analyse_message").performClick()
            compose.onNodeWithText("High risk signals").assertIsDisplayed()
        }
    }

    @Test
    fun sharedLinkOpensLinkGuardAndIsAnalysed() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "http://paytm-cashback-claim.top/receive")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("High risk signals")).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun processTextActionOpensScamLens() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "Install AnyDesk so our executive can process your refund")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithTag("analyse_message").performClick()
            compose.onNodeWithText("Caution advised", substring = true).assertExists()
        }
    }
}
