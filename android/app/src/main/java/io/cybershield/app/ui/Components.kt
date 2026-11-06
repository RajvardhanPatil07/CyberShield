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
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.ui.platform.testTag
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
    Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp,
        color = CsColors.Ink, modifier = Modifier.enter(0))
    Spacer(Modifier.height(6.dp))
    Text(subtitle, color = CsColors.Muted, modifier = Modifier.enter(1))
    Spacer(Modifier.height(16.dp))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontSize = 13.sp, color = CsColors.Muted)
}

@Composable
fun ScoreBadge(score: Int, level: RiskLevel, size: Int = 64) {
    // %s with a plain string keeps Western digits in every locale, matching the badge number.
    val label = stringResource(R.string.score_of, score.toString())
    ActivityRings(
        rings = listOf(levelFill(level) to (score.coerceAtLeast(4) / 100f)),
        size = size.dp, stroke = (size / 10f).coerceAtLeast(4f).dp,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        CountUpText(score, fontSize = (size / 3.4f).sp, color = CsColors.Ink, weight = FontWeight.ExtraBold)
    }
}

/** Last result that produced a haptic, so returning to a page does not buzz again for the same verdict. */
private var lastBuzzedResult: RiskAssessment? = null

/**
 * Verdict card. The most important next step is shown right under the verdict, before the reasons,
 * so it is visible without scrolling. [onReport] adds a "Report this" action for risky results.
 */
@Composable
fun ResultCard(result: RiskAssessment, showLanguage: Boolean = false, onReport: (() -> Unit)? = null) {
    val tint = levelColor(result.level)
    // A different buzz for a risky verdict than a safe one, once per new result.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(result) {
        if (lastBuzzedResult !== result) {
            lastBuzzedResult = result
            view.buzz(if (result.level == RiskLevel.LOW) Buzz.CONFIRM else Buzz.REJECT)
        }
    }
    GlassCard(Modifier.fillMaxWidth().enter(2), border = levelFill(result.level).copy(alpha = 0.5f), borderWidth = 1.5.dp, color = CsColors.GlassStrong) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (result.level) { RiskLevel.HIGH -> Icons.Default.Error; RiskLevel.MEDIUM -> Icons.Default.Warning; RiskLevel.LOW -> Icons.Default.CheckCircle },
                            null, tint = levelFill(result.level), modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.result_title).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = CsColors.Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(result.level.label()), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)
                }
                Spacer(Modifier.width(12.dp))
                ScoreBadge(result.score, result.level, size = 84)
            }
            val mainStep = result.recommendations.firstOrNull()
            if (mainStep != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().background(levelFill(result.level).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp).testTag("main_step"),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(mainStep.label()), fontWeight = FontWeight.Bold, color = CsColors.Ink)
                }
            }
            if (showLanguage && result.language != DetectedLanguage.UNKNOWN) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.detected_language, stringResource(result.language.label())), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shown = result.findings.distinctBy { it.code }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.why_title), fontWeight = FontWeight.Bold)
                shown.forEach { finding -> FindingRow(finding) }
            }
            val otherSteps = result.recommendations.drop(1)
            if (otherSteps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.what_to_do), fontWeight = FontWeight.Bold)
                otherSteps.forEach { rec ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lightbulb, null, tint = CsColors.Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(rec.label()))
                    }
                }
            }
            if (onReport != null && result.level != RiskLevel.LOW) {
                Spacer(Modifier.height(12.dp))
                SoftButton(onClick = onReport, modifier = Modifier.fillMaxWidth().testTag("report_open"), contentColor = CsColors.RiskText) {
                    Icon(Icons.Default.Flag, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_action))
                }
            }
            Spacer(Modifier.height(10.dp))
            val usedMl = result.findings.any { it.code == io.cybershield.app.security.FindingCode.ML_PATTERN_MATCH }
            Text(stringResource(if (usedMl) R.string.disclaimer_ml else R.string.disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val tint = when {
        finding.code.weight >= 35 -> CsColors.Risk
        finding.code.weight >= 15 -> CsColors.Warn
        else -> CsColors.Accent
    }
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Dot(tint, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(finding.code.label()), fontWeight = FontWeight.SemiBold)
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
            val color = if (weight >= 35) CsColors.Risk else CsColors.Warn
            withStyle(SpanStyle(background = color.copy(alpha = 0.26f), color = CsColors.Ink, fontWeight = FontWeight.SemiBold)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    GlassCard(Modifier.fillMaxWidth().enter(3)) {
        Column(Modifier.padding(18.dp)) {
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
fun CloudConsentDialog(
    body: String,
    preview: String?,
    alreadyAllowed: Boolean,
    onSend: (remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
    rememberLabel: String = stringResource(R.string.cloud_remember),
) {
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
                if (!alreadyAllowed) CheckboxRow(rememberChoice, rememberLabel, small = true) { rememberChoice = it }
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
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(text, color = CsColors.Ink2, modifier = Modifier.padding(14.dp)) }
}

@Composable
fun ReputationCard(report: ReputationReport) {
    val hits = report.providers.filter { it.status == ProviderStatus.MALICIOUS || it.status == ProviderStatus.SUSPICIOUS }
    val (tint, headline) = when (report.verdict) {
        ReputationVerdict.MALICIOUS -> CsColors.Danger to stringResource(R.string.rep_malicious,
            hits.filter { it.status == ProviderStatus.MALICIOUS }.joinToString { providerName(it.name) })
        ReputationVerdict.SUSPICIOUS -> CsColors.Amber to stringResource(R.string.rep_suspicious, hits.joinToString { providerName(it.name) })
        ReputationVerdict.NO_KNOWN_THREAT -> CsColors.Mint to stringResource(R.string.rep_clean)
        ReputationVerdict.UNAVAILABLE -> CsColors.Muted to stringResource(
            if (report.providers.all { it.status == ProviderStatus.NOT_CONFIGURED }) R.string.rep_not_configured else R.string.rep_unavailable,
        )
    }
    GlassCard(Modifier.fillMaxWidth().enter(1)) {
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
    GlassCard(Modifier.fillMaxWidth().enter(1)) {
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
