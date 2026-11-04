package io.cybershield.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cybershield.app.R
import io.cybershield.app.cloud.AiAnalysis
import io.cybershield.app.cloud.CloudResult
import io.cybershield.app.cloud.ProviderStatus
import io.cybershield.app.cloud.ReputationReport
import io.cybershield.app.cloud.ReputationVerdict
import io.cybershield.app.security.DetectedLanguage
import io.cybershield.app.security.Finding
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.RiskLevel

fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.HIGH -> CsColors.Danger
    RiskLevel.MEDIUM -> CsColors.Amber
    RiskLevel.LOW -> CsColors.Mint
}

@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ScoreBadge(score: Int, level: RiskLevel, size: Int = 64) {
    val tint = levelColor(level)
    // %s with a plain string keeps Western digits in every locale, matching the badge number.
    val label = stringResource(R.string.score_of, score.toString())
    Box(
        Modifier.size(size.dp).border(4.dp, tint, CircleShape).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(score.toString(), color = tint, fontWeight = FontWeight.Bold, fontSize = (size / 3).sp)
    }
}

@Composable
fun ResultCard(result: RiskAssessment, showLanguage: Boolean = false) {
    val tint = levelColor(result.level)
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (result.level) { RiskLevel.HIGH -> Icons.Default.Error; RiskLevel.MEDIUM -> Icons.Default.Warning; RiskLevel.LOW -> Icons.Default.CheckCircle },
                    null, tint = tint,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.result_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(result.level.label()), color = tint, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                ScoreBadge(result.score, result.level)
            }
            if (showLanguage && result.language != DetectedLanguage.UNKNOWN) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.detected_language, stringResource(result.language.label())), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shown = result.findings.distinctBy { it.code }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.why_title), fontWeight = FontWeight.Bold)
                shown.forEach { finding -> FindingRow(finding) }
            }
            if (result.recommendations.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.what_to_do), fontWeight = FontWeight.Bold)
                result.recommendations.forEach { rec ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(rec.label()))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val tint = when {
        finding.code.weight >= 35 -> CsColors.Danger
        finding.code.weight >= 15 -> CsColors.Amber
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Info, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(finding.code.label()))
            finding.evidence?.takeIf { it.isNotBlank() }?.let {
                Text("“${it.take(80)}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shows [text] with every finding range highlighted, so users see exactly which words triggered a warning. */
@Composable
fun HighlightedText(text: String, findings: List<Finding>) {
    val ranges = findings.mapNotNull { f -> f.range?.takeIf { it.first >= 0 && it.last < text.length }?.let { it to f.code.weight } }
        .sortedBy { it.first.first }
    if (ranges.isEmpty()) return
    val annotated = buildAnnotatedString {
        var cursor = 0
        for ((range, weight) in ranges) {
            if (range.first < cursor) continue // overlapping match already highlighted
            append(text.substring(cursor, range.first))
            val color = if (weight >= 35) CsColors.Danger else CsColors.Amber
            withStyle(SpanStyle(background = color.copy(alpha = 0.30f), color = Color.White, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.highlighted_title), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Text(annotated, modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) }
        }
    }
}

/** Explicit confirmation before anything leaves CyberShield. High-risk targets need an extra acknowledgement. */
@Composable
fun ConfirmOpenDialog(title: String, body: String, highRisk: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var acknowledged by remember { mutableStateOf(!highRisk) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, fontFamily = FontFamily.Default)
                if (highRisk) {
                    Text(stringResource(R.string.open_high_risk), color = CsColors.Danger, fontWeight = FontWeight.SemiBold)
                    CheckboxRow(acknowledged, stringResource(R.string.open_ack)) { acknowledged = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = acknowledged) { Text(stringResource(R.string.open_anyway)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun CloudConsentDialog(body: String, preview: String?, alreadyAllowed: Boolean, onSend: (remember: Boolean) -> Unit, onDismiss: () -> Unit) {
    var rememberChoice by remember { mutableStateOf(alreadyAllowed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_consent_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                preview?.let {
                    Text(stringResource(R.string.cloud_preview), fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        Text(it.take(1200), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!alreadyAllowed) CheckboxRow(rememberChoice, stringResource(R.string.cloud_remember), small = true) { rememberChoice = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(rememberChoice) }) { Text(stringResource(R.string.cloud_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Checkbox whose whole row, including the label, is one tappable target. */
@Composable
private fun CheckboxRow(checked: Boolean, label: String, small: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
    }
}

/** Renders non-success cloud outcomes. Returns without drawing for Ok. */
@Composable
fun CloudProblem(result: CloudResult<*>) {
    val text = when (result) {
        is CloudResult.Ok -> return
        CloudResult.NotConfigured -> stringResource(R.string.cloud_not_configured)
        CloudResult.Offline -> stringResource(R.string.cloud_offline)
        CloudResult.RateLimited -> stringResource(R.string.cloud_rate_limited)
        is CloudResult.Failed -> stringResource(R.string.cloud_failed, result.message)
    }
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun ReputationCard(report: ReputationReport) {
    val hits = report.providers.filter { it.status == ProviderStatus.MALICIOUS || it.status == ProviderStatus.SUSPICIOUS }
    val (tint, headline) = when (report.verdict) {
        ReputationVerdict.MALICIOUS -> CsColors.Danger to stringResource(R.string.rep_malicious,
            hits.filter { it.status == ProviderStatus.MALICIOUS }.joinToString { providerName(it.name) })
        ReputationVerdict.SUSPICIOUS -> CsColors.Amber to stringResource(R.string.rep_suspicious, hits.joinToString { providerName(it.name) })
        ReputationVerdict.NO_KNOWN_THREAT -> CsColors.Mint to stringResource(R.string.rep_clean)
        ReputationVerdict.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(
            if (report.providers.all { it.status == ProviderStatus.NOT_CONFIGURED }) R.string.rep_not_configured else R.string.rep_unavailable,
        )
    }
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.rep_title), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(headline, color = tint, fontWeight = FontWeight.SemiBold)
            report.expandedUrl?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.rep_expanded, it), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            report.providers.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(providerName(p.name), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(p.status.label()) + if (p.threats.isNotEmpty()) " · " + p.threats.joinToString() else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.status == ProviderStatus.MALICIOUS) CsColors.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (report.cached) Text(stringResource(R.string.rep_cached), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AiCard(analysis: AiAnalysis) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ai_title), fontWeight = FontWeight.Bold)
            if (analysis.scamType.isNotBlank()) {
                Text(stringResource(R.string.ai_scam_type) + ": " + analysis.scamType, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Text(analysis.summary)
            AiList(stringResource(R.string.ai_techniques), analysis.techniques)
            AiList(stringResource(R.string.ai_red_flags), analysis.redFlags)
            AiList(stringResource(R.string.ai_steps), analysis.safeSteps)
            Text(stringResource(R.string.ai_confidence, analysis.confidence), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.ai_disclaimer) + (analysis.model?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    items.forEach { Text("• $it") }
}

/**
 * Opens an http(s) or upi URI in another app, only after the caller has shown a confirmation dialog.
 * Any other scheme is refused, so a crafted QR or shared text cannot launch arbitrary intents.
 */
fun openExternally(context: Context, target: String, chooserTitle: String? = null): Boolean {
    val uri = runCatching { Uri.parse(target.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("http", "https", "upi") || (scheme != "upi" && uri.host.isNullOrBlank())) return false
    val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    val intent = if (scheme == "upi") Intent.createChooser(view, chooserTitle) else view
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
