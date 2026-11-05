package io.cybershield.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Cobalt Silk": a blue-and-white app. Red, amber and green are reserved for verdicts.
 * Fill colours are used for rings and dots; the *Text variants are darker so labels keep
 * at least 4.5:1 contrast on the white glass cards.
 */
object CsColors {
    // Brand
    val Canvas = Color(0xFFF7F8FA)
    val Sky = Color(0xFF8FD3FF)
    val Cobalt = Color(0xFF0A5CFF)
    val Accent = Color(0xFF2448F5)
    val AccentSoft = Color(0xFFE6ECFF)
    val Ink = Color(0xFF0B1A3F)
    val Ink2 = Color(0xFF2A3B66)
    val Muted = Color(0xFF55658A)
    val Track = Color(0x140B1A3F)
    val Glass = Color(0xD9FFFFFF)
    val GlassStrong = Color(0xF2FFFFFF)
    val GlassEdge = Color(0xE6FFFFFF)

    // Verdicts (fill)
    val Safe = Color(0xFF12A87A)
    val Warn = Color(0xFFE59A00)
    val Risk = Color(0xFFE5383B)

    // Verdicts (text on white)
    val SafeText = Color(0xFF0A7F5B)
    val WarnText = Color(0xFF9A5B00)
    val RiskText = Color(0xFFC8232B)

    // Names kept from the previous palette so existing call sites stay meaningful.
    val Mint = SafeText
    val Amber = WarnText
    val Danger = RiskText
    val Periwinkle = Accent
    val Night = Canvas
    val Panel = Glass
    val PanelHigh = AccentSoft
}

@Composable
fun CyberShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CsColors.Accent,
            onPrimary = Color.White,
            primaryContainer = CsColors.AccentSoft,
            onPrimaryContainer = CsColors.Accent,
            secondary = CsColors.Cobalt,
            onSecondary = Color.White,
            secondaryContainer = CsColors.AccentSoft,
            onSecondaryContainer = CsColors.Ink,
            background = CsColors.Canvas,
            onBackground = CsColors.Ink,
            surface = Color.White,
            onSurface = CsColors.Ink,
            surfaceVariant = CsColors.AccentSoft,
            onSurfaceVariant = CsColors.Muted,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = CsColors.Glass,
            outline = Color(0xFFB9C5E0),
            outlineVariant = Color(0x1F0B1A3F),
            error = CsColors.RiskText,
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) {
        // MaterialTheme installs a ripple as the default indication; replace it with the spring press
        // for everything built on clickable / selectable. Material buttons use PrimaryButton / SoftButton.
        CompositionLocalProvider(LocalIndication provides SpringPress, content = content)
    }
}
