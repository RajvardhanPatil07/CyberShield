package io.cybershield.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// The CyberShield mark: a white shield on a blue rounded tile, with a check that can be drawn in.
// The same geometry is used by the launch screen (res/drawable/splash_mark.xml), the intro and the
// home header, so the logo is one continuous object from the moment the app icon is tapped.
private val ShieldPath = PathParser().parsePathString("M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z").toPath()
private val CheckPath = Path().apply { moveTo(8.2f, 12.3f); lineTo(10.9f, 15f); lineTo(15.9f, 9.7f) }

/** Draws the brand mark into a square of [side] px at [topLeft]. [check] 0..1 draws the tick in. */
fun DrawScope.drawBrandMark(topLeft: Offset, side: Float, check: Float, alpha: Float = 1f) {
    drawRoundRect(CsColors.Accent, topLeft, Size(side, side), CornerRadius(side * 0.3125f), alpha = alpha)
    val icon = side * 0.5625f
    translate(topLeft.x + (side - icon) / 2, topLeft.y + (side - icon) / 2) {
        scale(icon / 24f, icon / 24f, pivot = Offset.Zero) {
            drawPath(ShieldPath, Color.White, alpha = alpha)
            if (check > 0f) {
                val tick = Path()
                PathMeasure().apply { setPath(CheckPath, false); getSegment(0f, length * check.coerceAtMost(1f), tick, true) }
                drawPath(tick, CsColors.Accent, alpha = alpha, style = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Launch choreography, about 1.3 s, played on a cold start from the launcher:
 *  1. the shield from the launch screen sits centred while a blue ring sweeps round it ("scanning");
 *  2. a tick draws into the shield and the tile pops slightly ("checked");
 *  3. the ring bursts outward, the white veil lifts, and the tile flies into the header logo on a
 *     spring while the home screen rises in behind it, card by card, and the tab bar slides up.
 * Content becomes usable at step 3 (about 0.8 s); nothing blocks touches.
 * It is skipped when the app is opened by a share or shortcut, and when system animations are off.
 */
@Stable
class IntroState(val play: Boolean) {
    /** Home content may start its entrance animations. */
    var revealed by mutableStateOf(!play)
        private set
    /** Intro finished; the real header logo is shown. */
    var done by mutableStateOf(!play)
        private set
    /** Where the header logo sits, in root coordinates. Written on layout, read while drawing. */
    var logoBounds: Rect? = null
    /** The headline glint should play once, only after an intro. */
    var glintPending = play

    internal val ring = Animatable(0f)
    internal val check = Animatable(if (play) 0f else 1f)
    internal val burst = Animatable(0f)
    internal val veil = Animatable(if (play) 1f else 0f)
    internal val fly = Animatable(0f)

    suspend fun run() = coroutineScope {
        if (!play) return@coroutineScope
        launch { ring.animateTo(1f, tween(560, delayMillis = 60, easing = FastOutSlowInEasing)) }
        delay(380)
        check.animateTo(1f, tween(240, easing = LinearOutSlowInEasing))
        launch { burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        delay(90)
        revealed = true
        launch { veil.animateTo(0f, tween(360)) }
        fly.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 190f, visibilityThreshold = 0.001f))
        done = true
    }
}

val LocalIntro = staticCompositionLocalOf { IntroState(play = false) }

/** Suspends until the intro lets content enter (returns at once when there is no intro). */
suspend fun IntroState.awaitReveal() { if (!revealed) snapshotFlow { revealed }.first { it } }

/** Full-screen layer drawn above the app during the intro. Draw-phase only; ignores touches. */
@Composable
fun IntroOverlay(intro: IntroState) {
    LaunchedEffect(intro) { intro.run() }
    if (intro.done) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(CsColors.Canvas, alpha = intro.veil.value)
        val f = intro.fly.value
        val startSide = 96.dp.toPx()
        val start = Offset(center.x - startSide / 2, center.y - startSide / 2)
        val target = intro.logoBounds
        val side = if (target != null) lerp(startSide, target.width, f) else startSide * (1f - 0.3f * f)
        val topLeft = if (target != null) Offset(lerp(start.x, target.left, f), lerp(start.y, target.top, f))
        else Offset(center.x - side / 2, center.y - side / 2)
        val markAlpha = if (target != null) 1f else (1f - f).coerceIn(0f, 1f)
        val mid = topLeft + Offset(side / 2, side / 2)

        // Soft blue glow behind the tile, fading as it leaves.
        val glow = side * 1.5f
        drawCircle(Brush.radialGradient(listOf(CsColors.Accent.copy(alpha = 0.20f * (1f - f)), Color.Transparent), mid, glow), glow, mid)

        // Scanning ring, then a burst outward.
        val b = intro.burst.value
        val ringAlpha = (1f - b).coerceIn(0f, 1f)
        if (ringAlpha > 0f) {
            val r = side / 2 * 1.45f * (1f + 0.6f * b)
            val stroke = 3.5.dp.toPx() * (1f - 0.5f * b)
            drawCircle(CsColors.Track, r, mid, alpha = ringAlpha, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(CsColors.Sky, CsColors.Accent, CsColors.Accent), mid), -90f, 360f * intro.ring.value, false,
                Offset(mid.x - r, mid.y - r), Size(r * 2, r * 2), alpha = ringAlpha, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        // The tile pops a little as the tick completes.
        val pop = 1f + 0.07f * sin(PI.toFloat() * intro.check.value) * (1f - f)
        val s = side * pop
        drawBrandMark(Offset(mid.x - s / 2, mid.y - s / 2), s, intro.check.value, markAlpha)
    }
}

/** One light sweep across text after the intro, like light catching glass. Draw-phase only. */
fun Modifier.glint(): Modifier = composed {
    val intro = LocalIntro.current
    val sweep = remember { Animatable(if (intro.glintPending && motionEnabled()) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (sweep.value < 1f) {
            intro.glintPending = false
            intro.awaitReveal()
            delay(420)
            sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val v = sweep.value
        if (v > 0.001f && v < 0.999f) {
            val w = size.width
            val x = -0.4f * w + v * 1.8f * w
            drawRect(
                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent), Offset(x - w * 0.22f, 0f), Offset(x + w * 0.22f, size.height)),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}
