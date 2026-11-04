package io.cybershield.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cybershield.app.BuildConfig
import io.cybershield.app.R
import io.cybershield.app.cloud.BackendClient
import io.cybershield.app.data.AppSettings
import io.cybershield.app.data.ScanHistoryRepository
import io.cybershield.app.security.UrlParser

enum class Page { HOME, MESSAGE, LINK, QR, HISTORY, SETTINGS }

/** Content handed to the app from outside: Sharesheet, text-selection menu or launcher shortcuts. */
sealed interface Incoming {
    data class SharedText(val text: String) : Incoming
    data class SharedImage(val uri: Uri) : Incoming
    data class OpenPage(val page: Page) : Incoming
}

/** Long-lived dependencies plus a version counter that screens observe to refresh history. */
class AppServices(context: Context) {
    val settings = AppSettings(context)
    val history = ScanHistoryRepository.create(context)
    var historyVersion by mutableIntStateOf(0)
        private set
    var cloudConsent by mutableStateOf(settings.cloudConsent)
        private set
    var saveHistory by mutableStateOf(settings.saveHistory)
        private set

    fun updateCloudConsent(value: Boolean) { settings.cloudConsent = value; cloudConsent = value }
    fun updateSaveHistory(value: Boolean) { settings.saveHistory = value; saveHistory = value }

    /** Runs [block] against history only when the user allows history; always refreshes observers. */
    fun <T> record(block: (ScanHistoryRepository) -> T): T? = (if (saveHistory) block(history) else null).also { historyVersion++ }

    fun changed() { historyVersion++ }

    fun client() = BackendClient(settings.backendUrl, settings.installId, requireHttps = !BuildConfig.DEBUG)

    fun reload() {
        cloudConsent = settings.cloudConsent
        saveHistory = settings.saveHistory
        historyVersion++
    }
}

@Composable
fun CyberShieldApp(incoming: Incoming? = null, onIncomingConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val services = remember { AppServices(context.applicationContext) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var messageInput by rememberSaveable { mutableStateOf("") }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var sharedNotice by remember { mutableStateOf(false) }
    var autoAnalyseLink by remember { mutableStateOf(false) }
    val inspectLink: (String) -> Unit = { linkInput = it; autoAnalyseLink = true; page = Page.LINK }

    LaunchedEffect(incoming) {
        when (incoming) {
            is Incoming.SharedText -> {
                val text = incoming.text.trim()
                // A share that is just one link goes to LinkGuard; anything else to ScamLens.
                val links = UrlParser.extract(text)
                if (links.size == 1 && links[0].first.length >= text.length - 2) {
                    inspectLink(links[0].first)
                } else {
                    messageInput = text
                    page = Page.MESSAGE
                    sharedNotice = true
                }
            }
            is Incoming.SharedImage -> {
                pendingImage = incoming.uri
                page = Page.MESSAGE
            }
            is Incoming.OpenPage -> page = incoming.page
            null -> return@LaunchedEffect
        }
        onIncomingConsumed()
    }

    BackHandler(enabled = page != Page.HOME) { page = Page.HOME }

    CyberShieldTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    NavItem(page, Page.HOME, { page = it }, Icons.Default.Shield, R.string.nav_home)
                    NavItem(page, Page.MESSAGE, { page = it }, Icons.AutoMirrored.Filled.Message, R.string.nav_message)
                    NavItem(page, Page.LINK, { page = it }, Icons.Default.Link, R.string.nav_link)
                    NavItem(page, Page.QR, { page = it }, Icons.Default.QrCodeScanner, R.string.nav_qr)
                    NavItem(page, Page.HISTORY, { page = it }, Icons.Default.History, R.string.nav_history)
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                when (page) {
                    Page.HOME -> DashboardScreen(services, onNavigate = { page = it }, onScreenshot = { pendingImage = it; page = Page.MESSAGE })
                    Page.MESSAGE -> ScamLensScreen(
                        services, messageInput, { messageInput = it }, pendingImage, { pendingImage = null },
                        showSharedNotice = sharedNotice, onNoticeShown = { sharedNotice = false },
                        onInspectLink = inspectLink,
                    )
                    Page.LINK -> LinkGuardScreen(services, linkInput, { linkInput = it }, autoAnalyseLink, { autoAnalyseLink = false })
                    Page.QR -> QrGuardScreen(services, onInspectLink = inspectLink)
                    Page.HISTORY -> HistoryScreen(services)
                    Page.SETTINGS -> SettingsScreen(services)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    current: Page, target: Page, onSelect: (Page) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int,
) {
    NavigationBarItem(
        selected = current == target || (target == Page.HOME && current == Page.SETTINGS),
        onClick = { onSelect(target) },
        icon = { Icon(icon, null) },
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.testTag("nav_${target.name.lowercase()}"),
    )
}
