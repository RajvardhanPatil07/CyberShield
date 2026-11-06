package io.cybershield.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import kotlin.math.abs
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
import io.cybershield.app.ml.MlClassifier
import io.cybershield.app.ml.MlClassifierFactory
import io.cybershield.app.security.RiskAnalyzer
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.ScanKind
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
    var mlAssist by mutableStateOf(settings.mlAssist)
        private set
    var donationConsent by mutableStateOf(settings.donationConsent)
        private set

    /** The on-device classifier for this build. Phase 1 resolves to [MlClassifier.Disabled]. */
    val mlClassifier: MlClassifier = MlClassifierFactory.create(context)

    /** True only when the user enabled ML assist AND a model is actually bundled in this build. */
    val mlActive: Boolean get() = mlAssist && mlClassifier.isAvailable

    fun updateCloudConsent(value: Boolean) { settings.cloudConsent = value; cloudConsent = value }
    fun updateSaveHistory(value: Boolean) { settings.saveHistory = value; saveHistory = value }
    fun updateMlAssist(value: Boolean) { settings.mlAssist = value; mlAssist = value }
    fun updateDonationConsent(value: Boolean) { settings.donationConsent = value; donationConsent = value }

    /**
     * Analyses a message through the rules, fusing the on-device model only when ML assist is active.
     * Centralised here so every screen shares one, consistent path.
     */
    fun analyseMessage(text: String, kind: ScanKind): RiskAssessment =
        RiskAnalyzer.message(text, kind, if (mlActive) mlClassifier else MlClassifier.Disabled)

    /** Runs [block] against history only when the user allows history; always refreshes observers. */
    fun <T> record(block: (ScanHistoryRepository) -> T): T? = (if (saveHistory) block(history) else null).also { historyVersion++ }

    fun changed() { historyVersion++ }

    fun client() = BackendClient(settings.backendUrl, settings.installId, requireHttps = !BuildConfig.DEBUG)

    fun reload() {
        cloudConsent = settings.cloudConsent
        saveHistory = settings.saveHistory
        mlAssist = settings.mlAssist
        donationConsent = settings.donationConsent
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

    val view = LocalView.current
    val go: (Page) -> Unit = { target -> if (target != page) { view.buzz(Buzz.TICK); page = target } }

    // Launch intro: only when opened normally on Home (not from a share or shortcut) and only once per launch.
    var introPending by rememberSaveable { mutableStateOf(incoming == null && motionEnabled()) }
    val intro = remember { IntroState(play = introPending && page == Page.HOME) }
    LaunchedEffect(intro.done) { if (intro.done) introPending = false }
    // The tab bar slides up from below as the home screen is revealed.
    val barIn = remember { Animatable(if (intro.play) 0f else 1f) }
    LaunchedEffect(intro.revealed) {
        if (intro.revealed && barIn.value < 1f) { delay(160); barIn.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 280f)) }
    }

    CyberShieldTheme { CompositionLocalProvider(LocalIntro provides intro) {
        // Wallpaper and pages are the blur source; the frosted bars float above them and blur whatever scrolls behind.
        val haze = remember { HazeState() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                SilkBackdrop(page = if (page == Page.SETTINGS) 0 else page.ordinal)
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        // Pages glide in from the side they are on in the tab bar and settle with a soft spring.
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        val settle = spring(dampingRatio = 0.86f, stiffness = 420f, visibilityThreshold = IntOffset.VisibilityThreshold)
                        (slideInHorizontally(settle) { dir * it / 8 } + fadeIn(tween(190)) + scaleIn(spring(0.86f, 420f), initialScale = 0.985f)) togetherWith
                            (slideOutHorizontally(tween(200)) { -dir * it / 14 } + fadeOut(tween(130))) using SizeTransform(clip = false)
                    },
                    label = "page",
                    modifier = Modifier.fillMaxSize(),
                ) { shown ->
                    // Each page gets its own scroll position; otherwise a scrolled result hides the next page's header.
                    // Content runs edge to edge and scrolls under the status bar and tab bar, which frost it.
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        when (shown) {
                            Page.HOME -> DashboardScreen(
                                services, onNavigate = go, onScreenshot = { pendingImage = it; page = Page.MESSAGE },
                                onCheck = { view.buzz(Buzz.TICK); checkFromHome(it) },
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
                        // Room to scroll the last card clear of the floating tab bar.
                        // The bar takes TabBarHeight plus 20 dp of padding; the rest is breathing room.
                        Spacer(Modifier.navigationBarsPadding().height(TabBarHeight + 40.dp))
                    }
                }
            }
            StatusFrost(haze, Modifier.align(Alignment.TopCenter))
            FloatingTabBar(
                page, haze, onSelect = go,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    translationY = (1f - barIn.value) * 140.dp.toPx()
                    alpha = barIn.value.coerceIn(0f, 1f)
                },
            )
            IntroOverlay(intro)
        }
    } }
}

private val TabBarHeight = 66.dp

/** Frosted strip behind the status bar so scrolled content blurs out instead of colliding with the clock. */
@Composable
private fun StatusFrost(haze: HazeState, modifier: Modifier) {
    Spacer(
        modifier.fillMaxWidth()
            .hazeEffect(haze, BarGlass) {
                mask = Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
            }
            .windowInsetsPadding(WindowInsets.statusBars).height(14.dp),
    )
}

private data class Tab(val page: Page, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(Page.HOME, Icons.Default.Home, R.string.nav_home),
    Tab(Page.MESSAGE, Icons.AutoMirrored.Filled.Message, R.string.nav_message),
    Tab(Page.LINK, Icons.Default.Link, R.string.nav_link),
    Tab(Page.QR, Icons.Default.QrCodeScanner, R.string.nav_qr),
    Tab(Page.HISTORY, Icons.Default.History, R.string.nav_history),
)

/** Bright rim on top, a faint dark edge underneath so the bar separates from the blue wallpaper. */
private val BarRim = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.5f), CsColors.Ink.copy(alpha = 0.10f)))

/**
 * Translucent floating bar (OxygenOS 16 / liquid-glass style): it blurs the page scrolling behind it.
 * A blue capsule glides to the selected tab on a spring and stretches slightly while it travels.
 * Only the capsule's draw layer animates, so switching tabs does not re-measure the bar.
 */
@Composable
private fun FloatingTabBar(current: Page, haze: HazeState, onSelect: (Page) -> Unit, modifier: Modifier = Modifier) {
    val selectedIndex = tabs.indexOfFirst { it.page == current }.coerceAtLeast(0) // Settings belongs to Home.
    val shape = RoundedCornerShape(50)
    Box(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().height(TabBarHeight)
            .clip(shape).hazeEffect(haze, BarGlass).border(1.dp, BarRim, shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val slot = maxWidth / tabs.size
            val animate = remember { motionEnabled() }
            val position = remember { Animatable(selectedIndex.toFloat()) }
            LaunchedEffect(selectedIndex) {
                if (animate) position.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.66f, stiffness = 430f))
                else position.snapTo(selectedIndex.toFloat())
            }
            Box(
                Modifier.width(slot).fillMaxHeight()
                    .graphicsLayer {
                        translationX = position.value * slot.toPx()
                        val stretch = (abs(position.velocity) * 0.035f).coerceAtMost(0.22f)
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.3f
                    }
                    .clip(shape).background(CsColors.Accent),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, tab ->
                    val selected = i == selectedIndex
                    val fg by animateColorAsState(if (selected) Color.White else CsColors.Ink2, tween(180), label = "fg")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(shape)
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.page) })
                            .testTag("nav_${tab.page.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(tab.icon, null, tint = fg, modifier = Modifier.size(22.dp))
                        Text(stringResource(tab.label), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, lineHeight = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}
