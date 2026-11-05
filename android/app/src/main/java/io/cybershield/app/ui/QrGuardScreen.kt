package io.cybershield.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.cybershield.app.R
import io.cybershield.app.security.QrAnalysis
import io.cybershield.app.security.QrContentType
import io.cybershield.app.security.QrGuard
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.vision.ImageReaders
import kotlinx.coroutines.launch

@Composable
fun QrGuardScreen(services: AppServices, onInspectLink: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<QrAnalysis?>(null) }
    var noCodeInImage by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }

    fun accept(value: String) {
        raw = value.take(4096)
        val result = QrGuard.analyze(raw)
        analysis = result
        noCodeInImage = false
        openFailed = false
        services.record { it.addQr(result) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val value = runCatching { ImageReaders.readQr(context, uri) }.getOrNull()
            if (value == null) noCodeInImage = true else accept(value)
        }
    }

    ScreenTitle(stringResource(R.string.nav_qr), stringResource(R.string.qr_subtitle))
    val current = analysis
    if (current == null) {
        QrScanner { value -> accept(value) }
        Spacer(Modifier.height(12.dp))
        SoftButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.qr_from_image))
        }
        if (noCodeInImage) Notice(stringResource(R.string.qr_no_code), color = CsColors.RiskText)
        return
    }

    GlassCard(Modifier.fillMaxWidth().enter(2), color = CsColors.GlassStrong) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.qr_decoded), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.qr_type, stringResource(current.type.label())), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            current.upi?.let { upi ->
                Field(stringResource(R.string.upi_payee), upi.payeeAddress)
                Field(stringResource(R.string.upi_name), upi.payeeName)
                Field(stringResource(R.string.upi_amount), upi.amount?.let { "₹$it" + (upi.currency?.takeIf { c -> !c.equals("INR", true) }?.let { c -> " ($c)" } ?: "") })
                Field(stringResource(R.string.upi_note), upi.note)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.upi_warning), color = CsColors.Amber, fontWeight = FontWeight.SemiBold)
            } ?: SelectionContainer {
                Text(raw.orEmpty(), modifier = Modifier.fillMaxWidth().background(CsColors.AccentSoft, RoundedCornerShape(12.dp)).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ResultCard(current.assessment)
    Spacer(Modifier.height(12.dp))

    val target = current.openTarget
    when {
        target != null && current.type == QrContentType.WEB_URL -> {
            PrimaryButton(onClick = { onInspectLink(target) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(stringResource(R.string.analyse_in_linkguard)) }
            Spacer(Modifier.height(8.dp))
            SoftButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(), contentColor = levelColor(current.assessment.level)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_link))
            }
        }
        target != null && current.type == QrContentType.UPI_PAYMENT -> {
            SoftButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(), contentColor = levelColor(current.assessment.level)) {
                Icon(Icons.Default.Payments, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_in_upi))
            }
        }
    }
    if (openFailed) Notice(stringResource(R.string.no_app_to_open), color = CsColors.RiskText)
    Spacer(Modifier.height(8.dp))
    SoftButton(onClick = { analysis = null; raw = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_another)) }

    if (confirm && target != null) {
        val isUpi = current.type == QrContentType.UPI_PAYMENT
        ConfirmOpenDialog(
            title = stringResource(if (isUpi) R.string.open_upi_title else R.string.open_confirm_title),
            body = if (isUpi) stringResource(R.string.open_upi_body, current.upi?.payeeName ?: current.upi?.payeeAddress.orEmpty())
            else stringResource(R.string.open_confirm_body, current.summary),
            highRisk = current.assessment.level == RiskLevel.HIGH,
            onDismiss = { confirm = false },
            onConfirm = { confirm = false; openFailed = !openExternally(context, target, context.getString(R.string.open_in_upi)) },
        )
    }
}

@Composable
private fun Field(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(0.4f), color = CsColors.Muted)
        Text(value ?: stringResource(R.string.not_provided), modifier = Modifier.weight(0.6f), fontWeight = FontWeight.SemiBold)
    }
}
