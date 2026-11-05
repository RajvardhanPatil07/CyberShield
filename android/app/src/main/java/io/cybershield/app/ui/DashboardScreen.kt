package io.cybershield.app.ui

import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import io.cybershield.app.R
import io.cybershield.app.data.ScanRecord
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScamLens

private val tips = listOf(R.string.tip_1, R.string.tip_2, R.string.tip_3, R.string.tip_4, R.string.tip_5, R.string.tip_6)

@Composable
fun DashboardScreen(
    services: AppServices,
    onNavigate: (Page) -> Unit,
    onScreenshot: (android.net.Uri) -> Unit,
    onCheck: (String) -> Unit = {},
) {
    // The Android photo picker needs no storage permission and exposes only the chosen image.
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onScreenshot) }
    val version = services.historyVersion
    val stats = remember(version) { services.history.stats() }
    val recent = remember(version) { services.history.all().take(4) }
    val recentHigh = remember(version) {
        val weekAgo = System.currentTimeMillis() - 7 * DateUtils.DAY_IN_MILLIS
        services.history.all().count { it.level == RiskLevel.HIGH && it.checkedAt >= weekAgo }
    }
    var draft by rememberSaveable { mutableStateOf("") }

    // Header: brand, language switch, settings.
    // The logo does not fade in with the rest: on launch the intro's shield flies into this spot.
    val intro = LocalIntro.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.size(32.dp)
                .onGloballyPositioned { intro.logoBounds = it.boundsInRoot() }
                .graphicsLayer { alpha = if (intro.done) 1f else 0f },
        ) { drawBrandMark(Offset.Zero, size.width, check = 1f) }
        Spacer(Modifier.width(9.dp))
        Text("CyberShield", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.4).sp, color = CsColors.Ink,
            modifier = Modifier.weight(1f).enter(0))
        Box(Modifier.enter(1)) { LanguageSwitch() }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = { onNavigate(Page.SETTINGS) },
            modifier = Modifier.enter(1).size(40.dp).clip(CircleShape).background(CsColors.GlassStrong).testTag("open_settings"),
        ) { Icon(Icons.Default.Tune, stringResource(R.string.settings), tint = CsColors.Ink) }
    }

    Spacer(Modifier.height(20.dp))
    Hero(stringResource(R.string.home_tagline))
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.home_title).replace('\n', ' '), color = CsColors.Ink2, modifier = Modifier.enter(1))
    Spacer(Modifier.height(16.dp))

    // Main action: paste a message or link, tap the arrow.
    GlassCard(Modifier.fillMaxWidth().enter(2), shape = RoundedCornerShape(30.dp), color = CsColors.GlassStrong) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft,
                onValueChange = { draft = it.take(ScamLens.MAX_INPUT) },
                placeholder = { Text(stringResource(R.string.home_paste_hint)) },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = CsColors.Accent, focusedPlaceholderColor = CsColors.Muted, unfocusedPlaceholderColor = CsColors.Muted,
                    focusedTextColor = CsColors.Ink, unfocusedTextColor = CsColors.Ink,
                ),
                modifier = Modifier.weight(1f).testTag("home_input"),
            )
            CheckButton(enabled = draft.isNotBlank()) { onCheck(draft); draft = "" }
        }
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel(stringResource(R.string.section_check))
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Feature(stringResource(R.string.nav_message), stringResource(R.string.feature_message_detail), Icons.AutoMirrored.Filled.Message,
            "feature_message", 3, Modifier.weight(1f)) { onNavigate(Page.MESSAGE) }
        Feature(stringResource(R.string.nav_link), stringResource(R.string.feature_link_detail), Icons.Default.Link,
            "feature_link", 3, Modifier.weight(1f)) { onNavigate(Page.LINK) }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Feature(stringResource(R.string.nav_qr), stringResource(R.string.feature_qr_detail), Icons.Default.QrCodeScanner,
            "feature_qr", 4, Modifier.weight(1f)) { onNavigate(Page.QR) }
        Feature(stringResource(R.string.feature_screenshot), stringResource(R.string.feature_screenshot_detail), Icons.Default.Image,
            "feature_screenshot", 4, Modifier.weight(1f)) {
            picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // Protection status with Fitness-style rings: safe, suspicious, high risk.
    Spacer(Modifier.height(14.dp))
    StatusCard(services, stats.total, stats.suspicious, stats.highRisk, recentHigh)

    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.section_recent), Modifier.weight(1f))
        if (recent.isNotEmpty()) TextButton(onClick = { onNavigate(Page.HISTORY) }) { Text(stringResource(R.string.see_all), color = CsColors.Accent) }
    }
    Spacer(Modifier.height(6.dp))
    GlassCard(Modifier.fillMaxWidth().enter(6)) {
        if (recent.isEmpty()) {
            Text(stringResource(R.string.recent_empty), color = CsColors.Muted, modifier = Modifier.padding(16.dp))
        } else {
            recent.forEachIndexed { i, record ->
                if (i > 0) HorizontalDivider(color = CsColors.Track)
                Box(Modifier.padding(horizontal = 14.dp)) { RecordRow(record) }
            }
        }
    }

    Spacer(Modifier.height(22.dp))
    SectionLabel(stringResource(R.string.section_tips))
    Spacer(Modifier.height(8.dp))
    val tipIndex = remember { (System.currentTimeMillis() / DateUtils.DAY_IN_MILLIS).toInt() }
    GlassCard(Modifier.fillMaxWidth().enter(7)) {
        (0 until 3).map { tips[(tipIndex + it) % tips.size] }.forEachIndexed { i, tip ->
            if (i > 0) HorizontalDivider(color = CsColors.Track)
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lightbulb, null, tint = CsColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(tip), color = CsColors.Ink)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassCard(Modifier.fillMaxWidth().enter(8), color = CsColors.GlassStrong) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Phone, null, tint = CsColors.Risk)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.helpline), fontWeight = FontWeight.SemiBold, color = CsColors.Ink)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** "Check first." in ink, "Click with confidence." in blue. Splits on the first sentence break in any language. */
@Composable
private fun Hero(tagline: String) {
    val cut = tagline.indexOfFirst { it == '.' || it == ',' || it == '।' }
    val (first, second) = if (cut in 1 until tagline.length - 1) tagline.substring(0, cut + 1) to tagline.substring(cut + 1).trim() else tagline to ""
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = CsColors.Ink)) { append(first) }
            if (second.isNotEmpty()) { append("\n"); withStyle(SpanStyle(color = CsColors.Accent)) { append(second) } }
        },
        fontSize = 40.sp, lineHeight = 43.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp,
        modifier = Modifier.enter(1).glint(),
    )
}

/** Compact EN / हि / म switch. Uses AppCompat per-app locales, like Settings. */
@Composable
private fun LanguageSwitch() {
    val chosen = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')
    val system = LocalConfiguration.current.locales[0].language
    val active = chosen.ifEmpty { system }.takeIf { it in setOf("en", "hi", "mr") } ?: "en"
    Row(Modifier.clip(RoundedCornerShape(50)).background(CsColors.GlassStrong).padding(3.dp)) {
        listOf(Triple("en", "EN", R.string.lang_en), Triple("hi", "हि", R.string.lang_hi), Triple("mr", "म", R.string.lang_mr)).forEach { (tag, short, full) ->
            val selected = active == tag
            val name = stringResource(full)
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (selected) CsColors.Accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) {
                        if (!selected) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }
                    .semantics { contentDescription = name }
                    .padding(horizontal = 10.dp, vertical = 6.dp).testTag("home_lang_$tag"),
            ) { Text(short, color = if (selected) Color.White else CsColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Round blue arrow with a gentle "breathing" halo while there is something to check. */
@Composable
private fun CheckButton(enabled: Boolean, onClick: () -> Unit) {
    val animate = remember { motionEnabled() }
    // The halo runs only while there is text to check, and is read while drawing so it never recomposes.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(enabled, animate) {
        if (enabled && animate) pulse.animateTo(1f, infiniteRepeatable(tween(2200, easing = LinearEasing))) else pulse.snapTo(0f)
    }
    val label = stringResource(R.string.home_check)
    Box(
        Modifier.size(52.dp)
            .drawBehind {
                val p = pulse.value
                if (p > 0f) drawCircle(CsColors.Accent.copy(alpha = 0.35f * (1f - p)), radius = size.minDimension / 2 * (0.82f + 0.18f * p))
            }
            .padding(4.dp).clip(CircleShape)
            .background(if (enabled) CsColors.Accent else CsColors.Accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.testTag("home_check"),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White) }
}

@Composable
private fun Feature(title: String, detail: String, icon: ImageVector, tag: String, order: Int, modifier: Modifier, action: () -> Unit) {
    // Press feedback comes from the app-wide spring indication.
    GlassCard(modifier.enter(order).testTag(tag), shape = RoundedCornerShape(22.dp), onClick = action) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(CsColors.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = CsColors.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(detail, fontSize = 12.sp, lineHeight = 15.sp, color = CsColors.Muted, maxLines = 2, minLines = 2)
        }
    }
}

@Composable
private fun StatusCard(services: AppServices, total: Int, suspicious: Int, high: Int, recentHigh: Int) {
    val safe = (total - suspicious).coerceAtLeast(0)
    val medium = (suspicious - high).coerceAtLeast(0)
    fun frac(n: Int) = if (total == 0) 0f else n / total.toFloat()
    val attention = recentHigh > 0
    GlassCard(Modifier.fillMaxWidth().enter(5)) {
        Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (attention) Icons.Default.Warning else Icons.Default.VerifiedUser, null,
                        tint = if (attention) CsColors.Warn else CsColors.Safe, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.status_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CsColors.Muted)
                }
                CountUpText(total, fontSize = 50.sp, weight = FontWeight.ExtraBold)
                Text(stringResource(R.string.stat_total), fontSize = 13.sp, color = CsColors.Muted)
                Spacer(Modifier.height(10.dp))
                Key(CsColors.Safe, safe, stringResource(R.string.stat_safe))
                Key(CsColors.Warn, medium, stringResource(R.string.stat_suspicious))
                Key(CsColors.Risk, high, stringResource(R.string.stat_high))
            }
            ActivityRings(listOf(CsColors.Safe to frac(safe), CsColors.Warn to frac(medium), CsColors.Risk to frac(high)), size = 124.dp, stroke = 13.dp)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Surface(shape = RoundedCornerShape(50), color = CsColors.Safe.copy(alpha = 0.12f), contentColor = CsColors.SafeText) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(CsColors.Safe, size = 6.dp)
                    Text(stringResource(R.string.status_local_ready), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.status_detail), fontSize = 13.sp, color = CsColors.Ink2)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(if (services.cloudConsent) R.string.status_cloud_on else R.string.status_cloud_off), fontSize = 12.sp, color = CsColors.Muted)
            if (attention) Text(pluralStringResource(R.plurals.status_attention, recentHigh, recentHigh.toString()),
                fontSize = 12.sp, color = CsColors.WarnText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Key(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Dot(color, size = 7.dp)
        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CsColors.Ink)
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 13.sp, color = CsColors.Muted, maxLines = 1)
    }
}

@Composable
fun RecordRow(record: ScanRecord, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(record.score, record.level, size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(record.kind.label()), fontWeight = FontWeight.SemiBold, color = CsColors.Ink)
                record.target?.let { Text(" · $it", color = CsColors.Muted, maxLines = 1) }
            }
            Text(
                if (record.confirmedMalicious) stringResource(R.string.confirmed_badge) else stringResource(record.level.label()),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (record.confirmedMalicious) CsColors.RiskText else levelColor(record.level),
            )
            Text(
                DateUtils.getRelativeTimeSpanString(record.checkedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                fontSize = 11.sp, color = CsColors.Muted,
            )
        }
        trailing?.invoke()
    }
}
