package io.cybershield.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var autoAnalyseMessage by remember { mutableStateOf(false) }
    val inspectLink: (String) -> Unit = { linkInput = it; autoAnalyseLink = true; page = Page.LINK }
    // A single link goes to LinkGuard; anything else to ScamLens.
    fun isSingleLink(text: String): String? {
        val links = UrlParser.extract(text)
        return links.singleOrNull()?.first?.takeIf { it.length >= text.length - 2 }
    }
    // The home box: the user typed or pasted and tapped the arrow, so analyse straight away (on this phone only).
    val checkFromHome: (String) -> Unit = { raw ->
        val text = raw.trim()
        val link = isSingleLink(text)
        if (link != null) inspectLink(link) else { messageInput = text; autoAnalyseMessage = true; page = Page.MESSAGE }
    }

    LaunchedEffect(incoming) {
        when (incoming) {
            is Incoming.SharedText -> {
                val text = incoming.text.trim()
                val link = isSingleLink(text)
                if (link != null) {
                    inspectLink(link)
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
        SilkBackground {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = CsColors.Ink,
                bottomBar = { FloatingTabBar(page) { page = it } },
            ) { padding ->
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(280, delayMillis = 60)) + slideInVertically(tween(320)) { it / 30 }) togetherWith fadeOut(tween(140))
                    },
                    label = "page",
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) { shown ->
                    // Each page gets its own scroll position; otherwise a scrolled result hides the next page's header.
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                        when (shown) {
                            Page.HOME -> DashboardScreen(
                                services, onNavigate = { page = it }, onScreenshot = { pendingImage = it; page = Page.MESSAGE },
                                onCheck = checkFromHome,
                            )
                            Page.MESSAGE -> ScamLensScreen(
                                services, messageInput, { messageInput = it }, pendingImage, { pendingImage = null },
                                showSharedNotice = sharedNotice, onNoticeShown = { sharedNotice = false },
                                onInspectLink = inspectLink,
                                autoAnalyse = autoAnalyseMessage, onAutoAnalysed = { autoAnalyseMessage = false },
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
    }
}

private data class Tab(val page: Page, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(Page.HOME, Icons.Default.Home, R.string.nav_home),
    Tab(Page.MESSAGE, Icons.AutoMirrored.Filled.Message, R.string.nav_message),
    Tab(Page.LINK, Icons.Default.Link, R.string.nav_link),
    Tab(Page.QR, Icons.Default.QrCodeScanner, R.string.nav_qr),
    Tab(Page.HISTORY, Icons.Default.History, R.string.nav_history),
)

/** Floating glass pill. The selected tab widens into a blue capsule that shows its label. */
@Composable
private fun FloatingTabBar(current: Page, onSelect: (Page) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(64.dp),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tabs.forEach { tab ->
                val selected = current == tab.page || (tab.page == Page.HOME && current == Page.SETTINGS)
                val weight by animateFloatAsState(if (selected) 2.3f else 1f, spring(dampingRatio = 0.72f, stiffness = 380f), label = "w")
                val bg by animateColorAsState(if (selected) CsColors.Accent else Color.Transparent, tween(250), label = "bg")
                val fg by animateColorAsState(if (selected) Color.White else CsColors.Muted, tween(250), label = "fg")
                val label = stringResource(tab.label)
                Row(
                    Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(50)).background(bg)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.page) })
                        .testTag("nav_${tab.page.name.lowercase()}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, if (selected) null else label, tint = fg, modifier = Modifier.size(22.dp))
                    AnimatedVisibility(selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}
