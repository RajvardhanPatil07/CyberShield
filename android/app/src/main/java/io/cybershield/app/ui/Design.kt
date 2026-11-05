package io.cybershield.app.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
 * The "silk" wallpaper: a white top that folds into sky blue and cobalt lower down.
 *
 * It sits in its own graphics layer and reads animation values only while drawing, so it never
 * recomposes or redraws the screens above it. It is still at rest (no per-frame work, which also
 * keeps the blurred bars cheap) and glides with a spring when [page] changes, like a wallpaper
 * shifting behind the home screen.
 */
@Composable
fun SilkBackdrop(page: Int, modifier: Modifier = Modifier) {
    val animate = remember { motionEnabled() }
    val shift = remember { Animatable(if (animate) -1.2f else page.toFloat()) }
    val intro = LocalIntro.current
    LaunchedEffect(page) {
        intro.awaitReveal() // On launch the wallpaper glides into place as the veil lifts.
        if (animate) shift.animateTo(page.toFloat(), spring(dampingRatio = 0.82f, stiffness = 60f)) else shift.snapTo(page.toFloat())
    }
    androidx.compose.foundation.layout.Spacer(
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val w = size.width; val h = size.height
            val base = Brush.verticalGradient(
                0f to CsColors.Canvas, 0.38f to Color(0xFFF1F6FF), 0.62f to Color(0xFFBFE3FF), 0.85f to Color(0xFF6FB2FF), 1f to Color(0xFF3A86FF),
            )
            onDrawBehind {
                val t = 0.6f + shift.value * 0.55f
                drawRect(base)
                fun blob(cx: Float, cy: Float, r: Float, c: Color) =
                    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
                blob(w * (0.95f + 0.08f * sin(t)), h * (0.92f + 0.04f * cos(t)), w * 1.05f, CsColors.Cobalt.copy(alpha = 0.55f))
                blob(w * (0.15f + 0.10f * cos(t * 2)), h * (0.70f + 0.05f * sin(t)), w * 0.85f, CsColors.Sky.copy(alpha = 0.55f))
                blob(w * (0.25f + 0.12f * sin(t)), h * (0.10f + 0.03f * cos(t * 2)), w * 0.9f, Color.White.copy(alpha = 0.9f))
                blob(w * (0.85f + 0.06f * cos(t)), h * (0.38f + 0.05f * sin(t * 2)), w * 0.6f, Color(0xFFD7ECFF).copy(alpha = 0.7f))
            }
        },
    )
}

/** Frosted bar material: blur of what scrolls behind, a milky tint, and light grain (OxygenOS 16 style). */
val BarGlass = HazeStyle(
    backgroundColor = CsColors.Canvas,
    tint = HazeTint(Color.White.copy(alpha = 0.58f)),
    blurRadius = 26.dp,
    noiseFactor = 0.06f,
    // Android 11 and older have no blur; use a nearly opaque white so text behind never shows through.
    fallbackTint = HazeTint(Color.White.copy(alpha = 0.94f)),
)

/** Bright top edge fading out lower down, like light catching the rim of a glass pane. */
val GlassRim = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f)))

/**
 * Milky white "liquid glass" card. The fill is a little clearer towards the bottom and the rim is
 * brighter at the top, which reads as glass over the blue wallpaper without a costly live blur.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    border: Color? = null,
    borderWidth: Dp = 1.dp,
    color: Color = CsColors.Glass,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = remember(color) { Brush.verticalGradient(listOf(color, color.copy(alpha = color.alpha * 0.86f))) }
    val rim = if (border != null) Modifier.border(borderWidth, border, shape) else Modifier.border(borderWidth, GlassRim, shape)
    Column(
        modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(fill)
            .then(rim),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides CsColors.Ink) { content() }
        },
    )
}

/**
 * Press feedback used by every clickable in the app (installed as `LocalIndication`): the element
 * dips slightly while touched and springs back with a small overshoot when released. Large cards
 * dip less than small buttons so the movement looks the same size everywhere. Runs entirely in the
 * draw phase, so it never causes recomposition.
 */
object SpringPress : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SpringPressNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 7
}

private class SpringPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val press = Animatable(0f)
    private val motion = motionEnabled()

    override fun onAttach() {
        coroutineScope.launch {
            var held = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> held++
                    is PressInteraction.Release, is PressInteraction.Cancel -> held = (held - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val down = held > 0
                launch {
                    if (!motion) press.snapTo(if (down) 1f else 0f)
                    else press.animateTo(if (down) 1f else 0f, if (down) spring(stiffness = 1500f) else spring(dampingRatio = 0.42f, stiffness = 520f))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = press.value
        if (p == 0f) { drawContent(); return }
        val dip = (9.dp.toPx() / maxOf(size.width, size.height, 1f)).coerceIn(0.012f, 0.06f)
        val s = 1f - dip * p
        scale(s) {
            this@draw.drawContent()
            if (p > 0f) drawRect(CsColors.Ink.copy(alpha = 0.035f * p.coerceIn(0f, 1f)))
        }
    }
}

/** Light haptics in the style of OxygenOS: a tick for navigation, distinct patterns for verdicts. */
enum class Buzz { TICK, CONFIRM, REJECT }

fun View.buzz(kind: Buzz) {
    val constant = when (kind) {
        Buzz.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Buzz.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Buzz.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** Primary blue action with the spring press. Same parameters as Material's Button. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.indication(source, SpringPress),
        enabled = enabled,
        interactionSource = source,
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = content,
    )
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
    val intro = LocalIntro.current
    val progress = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        intro.awaitReveal()
        // Rise on a soft spring after a stagger; cards settle rather than stop.
        delay(60L * index.coerceAtMost(8))
        launch { progress.animateTo(1f, tween(520, easing = EaseOut)) }
        lift.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 260f))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = lift.value * 22.dp.toPx()
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
    val intro = LocalIntro.current
    LaunchedEffect(rings) { if (animate) { progress.snapTo(0f); intro.awaitReveal(); progress.animateTo(1f, tween(1400, delayMillis = 200, easing = EaseOut)) } }
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
    val intro = LocalIntro.current
    LaunchedEffect(value) { if (animate) intro.awaitReveal(); if (animate) shown.animateTo(value.toFloat(), tween(1200, delayMillis = 200, easing = EaseOut)) else shown.snapTo(value.toFloat()) }
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
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.indication(source, SpringPress),
        enabled = enabled,
        interactionSource = source,
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

