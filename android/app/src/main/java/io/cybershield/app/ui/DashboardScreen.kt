package io.cybershield.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cybershield.app.R
import io.cybershield.app.data.ScanRecord
import io.cybershield.app.security.RiskLevel

private val tips = listOf(R.string.tip_1, R.string.tip_2, R.string.tip_3, R.string.tip_4, R.string.tip_5, R.string.tip_6)

@Composable
fun DashboardScreen(services: AppServices, onNavigate: (Page) -> Unit, onScreenshot: (android.net.Uri) -> Unit) {
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("CYBERSHIELD", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { onNavigate(Page.SETTINGS) }, modifier = Modifier.testTag("open_settings")) {
            Icon(Icons.Default.Settings, stringResource(R.string.settings))
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.home_title), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.home_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))

    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val attention = recentHigh > 0
                Icon(if (attention) Icons.Default.Warning else Icons.Default.VerifiedUser, null,
                    tint = if (attention) CsColors.Amber else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.status_title), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.status_local_ready), fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            Text(stringResource(R.string.status_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(if (services.cloudConsent) R.string.status_cloud_on else R.string.status_cloud_off), style = MaterialTheme.typography.bodySmall)
            if (recentHigh > 0) Text(pluralStringResource(R.plurals.status_attention, recentHigh, recentHigh), color = CsColors.Amber, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(stats.total, stringResource(R.string.stat_total), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Stat(stats.suspicious, stringResource(R.string.stat_suspicious), CsColors.Amber, Modifier.weight(1f))
                Stat(stats.highRisk, stringResource(R.string.stat_high), CsColors.Danger, Modifier.weight(1f))
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.section_check))
    Spacer(Modifier.height(8.dp))
    Feature(stringResource(R.string.nav_message), stringResource(R.string.feature_message_detail), Icons.AutoMirrored.Filled.Message, "feature_message") { onNavigate(Page.MESSAGE) }
    Feature(stringResource(R.string.nav_link), stringResource(R.string.feature_link_detail), Icons.Default.Link, "feature_link") { onNavigate(Page.LINK) }
    Feature(stringResource(R.string.nav_qr), stringResource(R.string.feature_qr_detail), Icons.Default.QrCodeScanner, "feature_qr") { onNavigate(Page.QR) }
    Feature(stringResource(R.string.feature_screenshot), stringResource(R.string.feature_screenshot_detail), Icons.Default.Image, "feature_screenshot") {
        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.section_recent), Modifier.weight(1f))
        if (recent.isNotEmpty()) TextButton(onClick = { onNavigate(Page.HISTORY) }) { Text(stringResource(R.string.see_all)) }
    }
    if (recent.isEmpty()) {
        Text(stringResource(R.string.recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    } else {
        recent.forEach { RecordRow(it) }
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel(stringResource(R.string.section_tips))
    Spacer(Modifier.height(8.dp))
    val tipIndex = remember { (System.currentTimeMillis() / DateUtils.DAY_IN_MILLIS).toInt() }
    (0 until 3).map { tips[(tipIndex + it) % tips.size] }.forEach { tip ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(stringResource(tip), modifier = Modifier.padding(14.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Phone, null, tint = CsColors.Danger)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.helpline), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stat(value: Int, label: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Feature(title: String, detail: String, icon: ImageVector, tag: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).testTag(tag).clickable { action() }) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun RecordRow(record: ScanRecord, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        ScoreBadge(record.score, record.level, size = 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(record.kind.label()), fontWeight = FontWeight.SemiBold)
                record.target?.let { Text(" · $it", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            Text(
                if (record.confirmedMalicious) stringResource(R.string.confirmed_badge) else stringResource(record.level.label()),
                style = MaterialTheme.typography.bodySmall,
                color = if (record.confirmedMalicious) CsColors.Danger else levelColor(record.level),
            )
            Text(
                DateUtils.getRelativeTimeSpanString(record.checkedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
