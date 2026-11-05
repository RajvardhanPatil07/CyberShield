package io.cybershield.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cybershield.app.R
import io.cybershield.app.security.RiskLevel
import io.cybershield.app.security.ScanKind

@Composable
fun HistoryScreen(services: AppServices) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<ScanKind?>(null) }
    var riskyOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val version = services.historyVersion
    val items = remember(version, query, kind, riskyOnly) {
        services.history.search(query, kind, if (riskyOnly) RiskLevel.MEDIUM else null)
    }

    ScreenTitle(stringResource(R.string.history_title), stringResource(R.string.history_subtitle))
    if (!services.saveHistory) Notice(stringResource(R.string.history_disabled), color = CsColors.WarnText)
    OutlinedTextField(
        value = query, onValueChange = { query = it.take(80) }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(stringResource(R.string.history_search)) },
        modifier = Modifier.fillMaxWidth().testTag("history_search"), shape = RoundedCornerShape(22.dp), colors = glassFieldColors(),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(stringResource(R.string.filter_all)) })
        ScanKind.entries.forEach { k ->
            FilterChip(selected = kind == k, onClick = { kind = if (kind == k) null else k }, label = { Text(stringResource(k.label())) })
        }
        FilterChip(selected = riskyOnly, onClick = { riskyOnly = !riskyOnly }, label = { Text(stringResource(R.string.filter_risky)) })
    }
    GlassCard(Modifier.fillMaxWidth().enter(2)) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = CsColors.Muted, modifier = Modifier.padding(16.dp))
        } else {
            items.forEachIndexed { i, record ->
                if (i > 0) HorizontalDivider(color = CsColors.Track)
                androidx.compose.foundation.layout.Box(Modifier.padding(start = 14.dp, end = 4.dp)) {
                    RecordRow(record) {
                        IconButton(onClick = { services.history.delete(record.id); services.changed() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = CsColors.Muted)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SoftButton(onClick = { confirmClear = true }, enabled = services.history.all().isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("clear_history")) {
        Text(stringResource(R.string.clear_history))
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_body)) },
            confirmButton = { TextButton(onClick = { services.history.clear(); services.changed(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
