package io.cybershield.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.cybershield.app.data.ScanHistory
import io.cybershield.app.data.ScanHistoryItem
import io.cybershield.app.security.RiskAssessment
import io.cybershield.app.security.RiskAnalyzer

private enum class Page { HOME, MESSAGE, LINK, QR, PRIVACY }

@Composable
fun CyberShieldApp(sharedText: String? = null, onShareConsumed: () -> Unit = {}) {
    var page by remember { mutableStateOf(Page.HOME) }
    var last by remember { mutableStateOf<RiskAssessment?>(null) }
    val context = LocalContext.current
    var history by remember { mutableStateOf(ScanHistory.read(context)) }
    var sharedInput by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            sharedInput = sharedText
            page = Page.MESSAGE
            onShareConsumed()
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF70E0C1),
        secondary = androidx.compose.ui.graphics.Color(0xFF91A9FF),
        background = androidx.compose.ui.graphics.Color(0xFF0A1020),
        surface = androidx.compose.ui.graphics.Color(0xFF142038)
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(page == Page.HOME, { page = Page.HOME }, icon = { Icon(Icons.Default.Shield, null) }, label = { Text("Home") })
                    NavigationBarItem(page == Page.MESSAGE, { page = Page.MESSAGE }, icon = { Icon(Icons.Default.Message, null) }, label = { Text("Message") })
                    NavigationBarItem(page == Page.LINK, { page = Page.LINK }, icon = { Icon(Icons.Default.Link, null) }, label = { Text("Link") })
                    NavigationBarItem(page == Page.PRIVACY, { page = Page.PRIVACY }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Privacy") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
                when (page) {
                    Page.HOME -> Home(last, history, { ScanHistory.clear(context); history = ScanHistory.read(context) }, { page = Page.MESSAGE }, { page = Page.LINK }, { page = Page.QR }, { page = Page.PRIVACY })
                    Page.MESSAGE -> AnalyzerPage("ScamLens", "Inspect a suspicious SMS, DM or offer.", "Paste message text…", "Analyse message", { RiskAnalyzer.message(it).also { result -> last = result } }, last, sharedInput) { result ->
                        ScanHistory.add(context, "Message", result.score, result.verdict)
                        history = ScanHistory.read(context)
                    }
                    Page.LINK -> AnalyzerPage("LinkGuard", "Inspect a URL before opening it.", "https://example.com", "Check link", { RiskAnalyzer.url(it).also { result -> last = result } }, last, linkInput) { result ->
                        ScanHistory.add(context, "Link", result.score, result.verdict)
                        history = ScanHistory.read(context)
                    }
                    Page.QR -> QrPage { decoded ->
                        linkInput = decoded
                        page = Page.LINK
                    }
                    Page.PRIVACY -> PrivacyPage()
                }
            }
        }
    }
}

@Composable
private fun Home(assessment: RiskAssessment?, history: List<ScanHistoryItem>, clearHistory: () -> Unit, message: () -> Unit, link: () -> Unit, qr: () -> Unit, privacy: () -> Unit) {
    Text("CYBERSHIELD", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(24.dp))
    Text("Your digital safety\nstarts here.", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Check first. Click with confidence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(22.dp))
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Protection status", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Manual checks ready", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            Text("Background monitoring is off. You control every permission.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(22.dp))
    Text("CHECK SOMETHING", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(10.dp))
    Feature("ScamLens", "Analyse suspicious messages", Icons.Default.Message, message)
    Feature("LinkGuard", "Inspect website addresses", Icons.Default.Link, link)
    Feature("QRGuard", "Scan QR codes before opening", Icons.Default.QrCodeScanner, qr)
    Spacer(Modifier.height(18.dp))
    assessment?.let { ResultCard(it) }
    if (history.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("RECENT CHECKS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        history.take(4).forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.type, fontWeight = FontWeight.SemiBold)
                    Text(item.verdict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${item.score}/100", fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = clearHistory) { Text("Clear local history") }
    }
    TextButton(onClick = privacy) { Text("Protection & privacy settings") }
}

@Composable
private fun Feature(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { action() }) {
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
private fun AnalyzerPage(title: String, subtitle: String, placeholder: String, actionLabel: String,
                         analyze: (String) -> RiskAssessment, assessment: RiskAssessment?, initialText: String = "", onScanSaved: (RiskAssessment) -> Unit = {}) {
    var input by remember(initialText) { mutableStateOf(initialText) }
    Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) }, minLines = 4, shape = RoundedCornerShape(16.dp))
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onScanSaved(analyze(input)) }, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Security, null)
        Spacer(Modifier.width(8.dp))
        Text(actionLabel)
    }
    Spacer(Modifier.height(18.dp))
    assessment?.let { ResultCard(it) }
}

@Composable
private fun ResultCard(result: RiskAssessment) {
    val tint = when {
        result.score >= 60 -> MaterialTheme.colorScheme.error
        result.score >= 30 -> androidx.compose.ui.graphics.Color(0xFFFFC36B)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Risk indicators", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${result.score}/100", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(result.verdict, color = tint, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            result.reasons.forEach { reason ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(reason)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(result.disclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QrPage(onDetected: (String) -> Unit) {
    var decoded by remember { mutableStateOf<String?>(null) }
    Text("QRGuard", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Scan a code, inspect its destination, then decide whether to open it.")
    Spacer(Modifier.height(14.dp))
    QrScanner { value -> if (decoded == null) decoded = value }
    decoded?.let { destination ->
        Spacer(Modifier.height(12.dp))
        Text("Decoded destination", fontWeight = FontWeight.Bold)
        SelectionContainer { Text(destination) }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onDetected(destination) }) { Text("Analyse this destination") }
        TextButton(onClick = { decoded = null }) { Text("Scan another code") }
    }
}

@Composable
private fun PrivacyPage() {
    Text("Privacy & protection", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("Message and URL checks run locally using simple heuristics. QR scanning uses the camera only while this screen is open.")
    Spacer(Modifier.height(12.dp))
    Text("CyberShield does not monitor other apps, read private chats, block traffic or upload scan content.")
    Spacer(Modifier.height(12.dp))
    Text("A low risk score does not prove that a message or website is safe.")
}
