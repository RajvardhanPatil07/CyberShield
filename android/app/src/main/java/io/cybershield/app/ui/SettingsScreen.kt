package io.cybershield.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.cybershield.app.BuildConfig
import io.cybershield.app.R

@Composable
fun SettingsScreen(services: AppServices) {
    var backend by remember { mutableStateOf(services.settings.backendOverride) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-')

    ScreenTitle(stringResource(R.string.settings_title), stringResource(R.string.about_version, BuildConfig.VERSION_NAME))

    SettingsCard(stringResource(R.string.language_title)) {
        listOf("" to stringResource(R.string.lang_system), "en" to stringResource(R.string.lang_en),
            "hi" to stringResource(R.string.lang_hi), "mr" to stringResource(R.string.lang_mr)).forEach { (tag, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = currentTag == tag, role = Role.RadioButton, onClick = {
                    // AppCompat persists the choice and recreates the activity in the new language.
                    AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                }).padding(vertical = 4.dp).testTag("lang_${tag.ifEmpty { "system" }}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = currentTag == tag, onClick = null)
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(stringResource(R.string.language_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SettingsCard(stringResource(R.string.cloud_title)) {
        ToggleRow(stringResource(R.string.cloud_toggle), stringResource(R.string.cloud_toggle_detail), services.cloudConsent, "cloud_toggle") {
            services.updateCloudConsent(it)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = backend,
            onValueChange = { backend = it.take(200); services.settings.backendOverride = backend },
            label = { Text(stringResource(R.string.backend_label)) },
            placeholder = { Text(stringResource(R.string.backend_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = glassFieldColors(),
        )
        val effective = services.settings.backendUrl
        Text(
            if (effective.isBlank()) stringResource(R.string.backend_none) else stringResource(R.string.backend_current, effective),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!BuildConfig.DEBUG && effective.startsWith("http://")) {
            Text(stringResource(R.string.backend_https_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    SettingsCard(stringResource(R.string.ml_title)) {
        ToggleRow(stringResource(R.string.ml_toggle), stringResource(R.string.ml_toggle_detail), services.mlAssist, "ml_toggle") {
            services.updateMlAssist(it)
        }
        if (!services.mlClassifier.isAvailable) {
            Text(stringResource(R.string.ml_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SettingsCard(stringResource(R.string.data_title)) {
        ToggleRow(stringResource(R.string.history_toggle), null, services.saveHistory, "history_toggle") { services.updateSaveHistory(it) }
        SoftButton(onClick = { confirmDelete = true }, contentColor = CsColors.RiskText, modifier = Modifier.fillMaxWidth().testTag("delete_all")) { Text(stringResource(R.string.delete_all_data)) }
        if (deleted) Text(stringResource(R.string.data_deleted), color = MaterialTheme.colorScheme.primary)
    }

    SettingsCard(stringResource(R.string.privacy_title)) {
        listOf(R.string.privacy_1, R.string.privacy_2, R.string.privacy_3, R.string.privacy_4, R.string.privacy_5).forEach {
            Text("• " + stringResource(it), modifier = Modifier.padding(vertical = 3.dp))
        }
        Text(stringResource(R.string.privacy_policy_link), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = CsColors.GlassStrong) {
        Text(stringResource(R.string.helpline), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_all_data)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    services.history.clear()
                    services.settings.resetAll()
                    services.reload()
                    backend = ""
                    deleted = true
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
