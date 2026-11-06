package io.cybershield.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.cybershield.app.R
import io.cybershield.app.security.Redactor
import io.cybershield.app.security.RiskAssessment

/** Official reporting channels. Chakshu is for suspected fraud calls and messages; money already lost goes to 1930 / cybercrime.gov.in. */
private const val CHAKSHU_URL = "https://sancharsaathi.gov.in/sfc/"
private const val CYBERCRIME_URL = "https://cybercrime.gov.in/"
private const val HELPLINE = "1930"

/**
 * Prepares a report the user files themselves. Nothing is sent by CyberShield: the user copies or
 * shares the text, or opens an official portal. The draft is editable and keeps the sender's
 * identifiers (number, UPI ID, links) while removing the user's own secrets (see [Redactor.forReport]).
 */
@Composable
fun ReportDialog(result: RiskAssessment, content: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val initial = buildReport(context, result, content)
    var draft by rememberSaveable { mutableStateOf(initial) }
    var copied by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.report_explain))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000); copied = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("report_draft"),
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(14.dp),
                    colors = glassFieldColors(),
                )
                Text(stringResource(R.string.report_redacted), style = MaterialTheme.typography.bodySmall, color = CsColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(onClick = { copyToClipboard(context, draft); copied = true }, modifier = Modifier.weight(1f).testTag("report_copy")) {
                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_copy))
                    }
                    SoftButton(onClick = { openFailed = !share(context, draft) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_share))
                    }
                }
                if (copied) Text(stringResource(R.string.report_copied), color = CsColors.SafeText, fontWeight = FontWeight.SemiBold)
                PrimaryButton(onClick = { openFailed = !openExternally(context, CHAKSHU_URL) }, modifier = Modifier.fillMaxWidth().testTag("report_chakshu")) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_open_chakshu))
                }
                SoftButton(onClick = { openFailed = !openExternally(context, CYBERCRIME_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_open_cybercrime))
                }
                SoftButton(onClick = { openFailed = !dialHelpline(context) }, modifier = Modifier.fillMaxWidth(), contentColor = CsColors.RiskText) {
                    Icon(Icons.Default.Phone, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_call_1930))
                }
                if (openFailed) Text(stringResource(R.string.no_app_to_open), color = CsColors.RiskText)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.report_close)) } },
    )
}

private fun buildReport(context: Context, result: RiskAssessment, content: String): String {
    val kind = context.getString(result.kind.label())
    val signs = result.findings.map { it.code }.distinct().filter { it.weight > 0 }.take(6)
    return buildString {
        appendLine(context.getString(R.string.report_heading))
        appendLine(context.getString(R.string.report_type, kind))
        appendLine(context.getString(R.string.report_risk, context.getString(result.level.label()), result.score.toString()))
        if (signs.isNotEmpty()) {
            appendLine(context.getString(R.string.report_signs))
            signs.forEach { appendLine("• " + context.getString(it.label())) }
        }
        appendLine()
        appendLine(context.getString(R.string.report_content))
        append(Redactor.forReport(content).take(2000).trim())
    }
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("CyberShield report", text))
}

private fun share(context: Context, text: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    return try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false }
}

/** Opens the dialler with 1930 filled in. ACTION_DIAL needs no permission and never places the call itself. */
fun dialHelpline(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$HELPLINE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false }
