package io.cybershield.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object CsColors {
    val Mint = Color(0xFF70E0C1)
    val Periwinkle = Color(0xFF91A9FF)
    val Night = Color(0xFF0A1020)
    val Panel = Color(0xFF142038)
    val PanelHigh = Color(0xFF1C2A47)
    val Amber = Color(0xFFFFC36B)
    val Danger = Color(0xFFFF7A7A)
    val Muted = Color(0xFFA9B4CC)
}

/** Dark, high-contrast palette kept from the original design. */
@Composable
fun CyberShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CsColors.Mint,
            onPrimary = Color(0xFF00382B),
            secondary = CsColors.Periwinkle,
            background = CsColors.Night,
            surface = CsColors.Panel,
            surfaceVariant = CsColors.PanelHigh,
            surfaceContainer = CsColors.Panel,
            surfaceContainerHigh = CsColors.PanelHigh,
            surfaceContainerHighest = CsColors.PanelHigh,
            onSurfaceVariant = CsColors.Muted,
            error = CsColors.Danger,
        ),
        content = content,
    )
}
