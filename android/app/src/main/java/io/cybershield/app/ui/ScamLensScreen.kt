package io.cybershield.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.cybershield.app.R
import io.cybershield.app.cloud.AiAnalysis
import io.cybershield.app.cloud.CloudResult
import io.cybershield.app.security.Redactor
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.ScamLens
import io.cybershield.app.security.ScanKind
import io.cybershield.app.vision.ImageReaders
import kotlinx.coroutines.launch

private enum class OcrState { IDLE, RUNNING, FAILED, EMPTY, DONE }

@Composable
fun ScamLensScreen(
    services: AppServices,
    input: String,
    onInputChange: (String) -> Unit,
    pendingImage: Uri?,
    onImageConsumed: () -> Unit,
    showSharedNotice: Boolean,
    onNoticeShown: () -> Unit,
    onInspectLink: (String) -> Unit,
    autoAnalyse: Boolean = false,
    onAutoAnalysed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<RiskAssessment?>(null) }
    var analysedText by remember { mutableStateOf("") }
    var ocr by remember { mutableStateOf(OcrState.IDLE) }
    var ai by remember { mutableStateOf<CloudResult<AiAnalysis>?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var consentPreview by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf(showSharedNotice) }
    val language = LocalConfiguration.current.locales[0].language.takeIf { it in setOf("en", "hi", "mr") } ?: "en"

    LaunchedEffect(showSharedNotice) { if (showSharedNotice) { notice = true; onNoticeShown() } }

    fun analyse(text: String, kind: ScanKind) {
        val r = ScamLens.analyze(text, kind)
        result = r
        analysedText = text.take(ScamLens.MAX_INPUT)
        ai = null
        services.record { it.add(r, kind, target = null) }
    }

    // Text sent from the home box is analysed once, on this phone only.
    LaunchedEffect(autoAnalyse) {
        if (autoAnalyse) {
            if (input.isNotBlank()) analyse(input, ScanKind.MESSAGE)
            onAutoAnalysed()
        }
    }

    suspend fun readScreenshot(uri: Uri) {
        ocr = OcrState.RUNNING
        result = null
        val text = try {
            ImageReaders.readText(context, uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        ocr = when {
            text == null -> OcrState.FAILED
            text.isBlank() -> OcrState.EMPTY
            else -> { onInputChange(text); analyse(text, ScanKind.SCREENSHOT); OcrState.DONE }
        }
    }

    // Images handed over by the dashboard or the Sharesheet. The pending image is cleared only after
    // OCR finishes: clearing it first would re-key this effect and cancel the OCR that just started.
    LaunchedEffect(pendingImage) {
        val uri = pendingImage ?: return@LaunchedEffect
        readScreenshot(uri)
        onImageConsumed()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { readScreenshot(uri) }
    }

    ScreenTitle(stringResource(R.string.nav_message), stringResource(R.string.scamlens_subtitle))
    if (notice) Notice(stringResource(R.string.shared_loaded), color = CsColors.Accent)
    OutlinedTextField(
        value = input,
        onValueChange = { onInputChange(it.take(ScamLens.MAX_INPUT)); ocr = OcrState.IDLE },
        modifier = Modifier.fillMaxWidth().testTag("message_input"),
        placeholder = { Text(stringResource(R.string.scamlens_hint)) },
        minLines = 5, maxLines = 12, shape = RoundedCornerShape(22.dp), colors = glassFieldColors(),
    )
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        onClick = { notice = false; analyse(input, if (ocr == OcrState.DONE) ScanKind.SCREENSHOT else ScanKind.MESSAGE) },
        enabled = input.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("analyse_message"),
    ) {
        Icon(Icons.Default.Security, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_analyse_message))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SoftButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Image, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_pick_screenshot))
        }
        if (input.isNotEmpty()) SoftButton(onClick = { onInputChange(""); result = null; ai = null; ocr = OcrState.IDLE }) { Text(stringResource(R.string.action_clear)) }
    }
    when (ocr) {
        OcrState.RUNNING -> Notice(stringResource(R.string.ocr_running), progress = true)
        OcrState.FAILED -> Notice(stringResource(R.string.ocr_failed), color = CsColors.RiskText)
        OcrState.EMPTY -> Notice(stringResource(R.string.ocr_empty))
        OcrState.DONE -> Notice(stringResource(R.string.ocr_source), color = CsColors.Muted)
        OcrState.IDLE -> Unit
    }

    val current = result ?: return
    Spacer(Modifier.height(16.dp))
    ResultCard(current, showLanguage = true)
    Spacer(Modifier.height(12.dp))
    HighlightedText(analysedText, current.findings)

    if (current.links.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth().enter(4)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.links_found), fontWeight = FontWeight.Bold)
                current.links.forEach { (url, assessment) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreBadge(assessment.score, assessment.level, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Text(url.take(70), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onInspectLink(url) }) { Text(stringResource(R.string.inspect_link)) }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    SoftButton(
        onClick = {
            if (services.client().isConfigured) consentPreview = Redactor.redact(analysedText) else ai = CloudResult.NotConfigured
        },
        enabled = !aiLoading,
        modifier = Modifier.fillMaxWidth().testTag("explain_ai"),
    ) {
        Icon(Icons.Default.AutoAwesome, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.cloud_explain))
    }
    if (aiLoading) Notice(stringResource(R.string.cloud_loading), progress = true)
    ai?.let { outcome ->
        if (outcome is CloudResult.Ok) { Spacer(Modifier.height(12.dp)); AiCard(outcome.value) } else CloudProblem(outcome)
    }

    consentPreview?.let { preview ->
        CloudConsentDialog(
            body = stringResource(R.string.cloud_consent_ai),
            preview = preview,
            alreadyAllowed = services.cloudConsent,
            onDismiss = { consentPreview = null },
            onSend = { rememberChoice ->
                consentPreview = null
                if (rememberChoice) services.updateCloudConsent(true)
                aiLoading = true
                scope.launch {
                    ai = services.client().explain(preview, if (current.kind == ScanKind.SCREENSHOT) "screenshot" else "message", language,
                        current.findings.map { it.code.name }.distinct())
                    aiLoading = false
                }
            },
        )
    }
}
