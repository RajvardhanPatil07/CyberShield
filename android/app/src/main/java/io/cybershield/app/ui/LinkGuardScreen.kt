package io.cybershield.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.cybershield.app.R
import io.cybershield.app.cloud.CloudResult
import io.cybershield.app.cloud.ReputationReport
import io.cybershield.app.cloud.ReputationVerdict
import io.cybershield.app.data.ScanRecord
import io.cybershield.app.security.LinkGuard
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.UrlParser
import kotlinx.coroutines.launch

@Composable
fun LinkGuardScreen(services: AppServices, input: String, onInputChange: (String) -> Unit, autoAnalyse: Boolean, onAutoAnalysed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<RiskAssessment?>(null) }
    var checkedUrl by remember { mutableStateOf("") }
    var record by remember { mutableStateOf<ScanRecord?>(null) }
    var reputation by remember { mutableStateOf<CloudResult<ReputationReport>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var askConsent by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var openFailed by remember { mutableStateOf(false) }

    fun analyse() {
        val url = input.trim()
        result = LinkGuard.analyze(url)
        checkedUrl = url
        reputation = null
        openFailed = false
        record = services.record { it.addUrl(result!!, url) }
    }

    // Links handed over by QRGuard, ScamLens or the Sharesheet are analysed once, locally only.
    LaunchedEffect(autoAnalyse) {
        if (autoAnalyse) {
            if (input.isNotBlank()) analyse()
            onAutoAnalysed()
        }
    }

    fun runReputation() {
        loading = true
        scope.launch {
            val outcome = services.client().check(checkedUrl)
            reputation = outcome
            loading = false
            if (outcome is CloudResult.Ok && outcome.value.verdict == ReputationVerdict.MALICIOUS) {
                record?.let { r -> services.history.markConfirmed(r.id); services.changed() }
            }
        }
    }

    ScreenTitle(stringResource(R.string.nav_link), stringResource(R.string.linkguard_subtitle))
    OutlinedTextField(
        value = input,
        onValueChange = { onInputChange(it.take(4096)); result = null },
        modifier = Modifier.fillMaxWidth().testTag("link_input"),
        placeholder = { Text(stringResource(R.string.linkguard_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = RoundedCornerShape(22.dp),
        colors = glassFieldColors(),
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = { analyse() }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp).testTag("check_link")) {
        Icon(Icons.Default.Security, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_check_link))
    }

    val current = result ?: return
    Spacer(Modifier.height(16.dp))
    ResultCard(current)

    val parsed = UrlParser.parse(checkedUrl)
    val openable = parsed != null && parsed.scheme in setOf("http", "https") && parsed.host.isNotEmpty()
    if (openable) {
        Spacer(Modifier.height(12.dp))
        SoftButton(onClick = {
            when {
                !services.client().isConfigured -> reputation = CloudResult.NotConfigured
                services.cloudConsent -> runReputation()
                else -> askConsent = true
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("cloud_check")) {
            Icon(Icons.Default.CloudQueue, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cloud_check_link))
        }
        if (loading) Notice(stringResource(R.string.cloud_loading), progress = true)
        reputation?.let { outcome ->
            if (outcome is CloudResult.Ok) { Spacer(Modifier.height(12.dp)); ReputationCard(outcome.value) } else CloudProblem(outcome)
        }
        Spacer(Modifier.height(8.dp))
        SoftButton(onClick = { confirmOpen = true }, modifier = Modifier.fillMaxWidth(), contentColor = levelColor(current.level)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.open_link))
        }
        if (openFailed) Notice(stringResource(R.string.no_app_to_open), color = CsColors.RiskText)
    }

    if (askConsent) {
        CloudConsentDialog(
            body = stringResource(R.string.cloud_consent_link), preview = parsed?.let { it.origin + it.path.take(120) },
            alreadyAllowed = false,
            onDismiss = { askConsent = false },
            onSend = { rememberChoice -> askConsent = false; if (rememberChoice) services.updateCloudConsent(true); runReputation() },
        )
    }
    if (confirmOpen && parsed != null) {
        val confirmedBad = (reputation as? CloudResult.Ok)?.value?.verdict == ReputationVerdict.MALICIOUS
        val target = if (parsed.schemeWasMissing) "https://" + checkedUrl else checkedUrl
        ConfirmOpenDialog(
            title = stringResource(R.string.open_confirm_title),
            body = stringResource(R.string.open_confirm_body, parsed.unicodeHost.ifEmpty { parsed.host } + " (" + parsed.host + ")"),
            highRisk = current.level == RiskLevel.HIGH || confirmedBad,
            onDismiss = { confirmOpen = false },
            onConfirm = { confirmOpen = false; openFailed = !openExternally(context, target) },
        )
    }
}
