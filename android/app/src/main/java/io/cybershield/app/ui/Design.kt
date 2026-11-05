package io.cybershield.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cybershield.app.security.RiskLevel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Respects the system "Remove animations" setting (animator duration scale 0). */
fun motionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

private val EaseOut = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)

/** Ring/dot colour for a verdict. Text uses [levelColor], which is darker for contrast. */
fun levelFill(level: RiskLevel): Color = when (level) {
    RiskLevel.HIGH -> CsColors.Risk
    RiskLevel.MEDIUM -> CsColors.Warn
    RiskLevel.LOW -> CsColors.Safe
}

/**
 * The moving "silk" background: a white top that folds into sky blue and cobalt lower down.
 * Large soft colour fields drift slowly; when animations are off it is drawn still.
 */
@Composable
fun SilkBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val animate = remember { motionEnabled() }
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "silk")
        transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "t").value
    } else 0.6f
    Box(
        modifier.fillMaxSize().drawBehind {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(
                0f to CsColors.Canvas, 0.38f to Color(0xFFF1F6FF), 0.62f to Color(0xFFBFE3FF), 0.85f to Color(0xFF6FB2FF), 1f to Color(0xFF3A86FF),
            ))
            fun blob(cx: Float, cy: Float, r: Float, c: Color) =
                drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
            blob(w * (0.95f + 0.08f * sin(t)), h * (0.92f + 0.04f * cos(t)), w * 1.05f, CsColors.Cobalt.copy(alpha = 0.55f))
            blob(w * (0.15f + 0.10f * cos(t * 2)), h * (0.70f + 0.05f * sin(t)), w * 0.85f, CsColors.Sky.copy(alpha = 0.55f))
            blob(w * (0.25f + 0.12f * sin(t)), h * (0.10f + 0.03f * cos(t * 2)), w * 0.9f, Color.White.copy(alpha = 0.9f))
            blob(w * (0.85f + 0.06f * cos(t)), h * (0.38f + 0.05f * sin(t * 2)), w * 0.6f, Color(0xFFD7ECFF).copy(alpha = 0.7f))
        },
        content = content,
    )
}

/** Milky white "liquid glass" card with a bright hairline edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    border: Color = CsColors.GlassEdge,
    borderWidth: Dp = 1.dp,
    color: Color = CsColors.Glass,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        contentColor = CsColors.Ink,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(borderWidth, border),
    ) { Column(content = content) }
}

/** Text field colours for the glass look: white fill, blue focus ring. */
@Composable
fun glassFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CsColors.GlassStrong,
    unfocusedContainerColor = CsColors.GlassStrong,
    disabledContainerColor = CsColors.Glass,
    focusedBorderColor = CsColors.Accent,
    unfocusedBorderColor = Color.White,
    focusedTextColor = CsColors.Ink,
    unfocusedTextColor = CsColors.Ink,
    focusedPlaceholderColor = CsColors.Muted,
    unfocusedPlaceholderColor = CsColors.Muted,
    cursorColor = CsColors.Accent,
)

/** Fades and lifts content in on first composition, staggered by [index]. */
fun Modifier.enter(index: Int = 0): Modifier = composed {
    if (!remember { motionEnabled() }) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(650, delayMillis = 60 * index.coerceAtMost(8), easing = EaseOut)) }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/**
 * Apple-Fitness-style progress ring that fills on first show. [rings] are drawn outside-in;
 * each pair is (colour, fraction 0..1).
 */
@Composable
fun ActivityRings(
    rings: List<Pair<Color, Float>>,
    size: Dp,
    stroke: Dp,
    gap: Dp = 3.dp,
    modifier: Modifier = Modifier,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val animate = remember { motionEnabled() }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(rings) { if (animate) { progress.snapTo(0f); progress.animateTo(1f, tween(1400, delayMillis = 200, easing = EaseOut)) } }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx(); val g = gap.toPx()
            rings.forEachIndexed { i, (color, fraction) ->
                val inset = sw / 2 + i * (sw + g)
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(CsColors.Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
                val sweep = 360f * fraction.coerceIn(0f, 1f) * progress.value
                if (sweep > 0.5f) {
                    drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.75f), color, color)), -90f, sweep, false, topLeft, arcSize,
                        style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
        center()
    }
}

/** A number that counts up from zero when first shown. */
@Composable
fun CountUpText(value: Int, fontSize: TextUnit, color: Color = CsColors.Ink, weight: FontWeight = FontWeight.Bold, modifier: Modifier = Modifier) {
    val animate = remember { motionEnabled() }
    val shown = remember { Animatable(if (animate) 0f else value.toFloat()) }
    LaunchedEffect(value) { if (animate) shown.animateTo(value.toFloat(), tween(1200, delayMillis = 200, easing = EaseOut)) else shown.snapTo(value.toFloat()) }
    Text(shown.value.roundToInt().toString(), fontSize = fontSize, color = color, fontWeight = weight, letterSpacing = (-0.5).sp, modifier = modifier)
}

/** Small coloured dot with a soft halo, used next to verdict labels and reasons. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size + 6.dp).padding(3.dp)) {
        drawCircle(color.copy(alpha = 0.22f), radius = this.size.minDimension / 2 + 3.dp.toPx())
        drawCircle(color)
    }
}

/** Thin outline used where a verdict colour should frame a card. */
fun Modifier.verdictBorder(level: RiskLevel, shape: Shape = RoundedCornerShape(24.dp)) =
    border(1.5.dp, levelFill(level).copy(alpha = 0.45f), shape)

/** Secondary action: a white glass pill, readable over every part of the blue background. */
@Composable
fun SoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = CsColors.Ink,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = CsColors.GlassStrong, contentColor = contentColor,
            disabledContainerColor = CsColors.Glass.copy(alpha = 0.6f), disabledContentColor = CsColors.Muted,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = content,
    )
}

/** Status or error line shown on a small glass card so it stays legible over the gradient. */
@Composable
fun Notice(text: String, color: Color = CsColors.Ink2, modifier: Modifier = Modifier, progress: Boolean = false) {
    GlassCard(modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = CsColors.GlassStrong) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text, color = color, fontWeight = FontWeight.Medium)
            if (progress) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    Modifier.fillMaxWidth(), color = CsColors.Accent, trackColor = CsColors.AccentSoft,
                )
            }
        }
    }
}

