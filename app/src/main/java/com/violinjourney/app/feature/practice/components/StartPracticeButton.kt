package com.violinjourney.app.feature.practice.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.art.SceneMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

private val Pill = RoundedCornerShape(percent = 50)
private val GlowSigma = 12.dp
private val GlowDrop = 4.dp
private val GlowPressGrow = 6.dp

/** The glow reaches this many sigmas past the button: beyond that there is nothing left to see. */
private const val GLOW_REACH = 3f

/** A blur loses nothing drawn at a quarter of the pixels each way, and takes a sixteenth of the memory. */
private const val GLOW_RESOLUTION = 0.25f

/** A mask blur is given a radius, which Skia turns into a sigma as 0.57735 · r + 0.5. */
private const val SKIA_SIGMA_PER_RADIUS = 0.57735f
private const val SKIA_SIGMA_BIAS = 0.5f

/** A mask blur needs a radius above zero. */
private const val MIN_BLUR_RADIUS = 1f
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * «Начать занятие» (spec 3.16): lights drift inside the button as light plays on mother-of-pearl,
 * and a soft glow lies under it — the one thing on the screen that asks to be pressed. A plain
 * Material button over a drawing of its own, so the ripple, the role and the touch target are the
 * button's. What moves is read in the draw phase and recomposes nothing. The lights rest while the
 * button is off the screen, while [calm] says so (a sheet is open, the streak flame sways) and with
 * «убрать анимации»; they slow to a stop and gather speed again rather than freeze.
 */
@Composable
fun StartPracticeButton(onClick: () -> Unit, calm: () -> Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = ViolinTheme.practiceColors
    val look = remember(scheme.primary, colors) { PearlLook(scheme.primary, colors.startLights, colors.startGlow) }
    val watch = remember { ButtonWatch() }
    val density = LocalDensity.current.density
    val seconds = rememberLightSeconds(watch, calm)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) PracticeMotion.START_PRESS_IN_MS else PracticeMotion.START_PRESS_OUT_MS),
        label = "startPress",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .onLayoutRectChanged { watch.seen = it.fractionVisibleInWindow() > 0f }
            .onSizeChanged { watch.widthDp = it.width / density }
            // the glow lies around the pill, so it is drawn before the clip
            .drawBehind { look.drawGlow(this, press.value) }
            .clip(Pill)
            .drawBehind { look.drawFill(this, seconds?.floatValue ?: StartButtonMath.REST_SECONDS) },
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = scheme.onPrimary),
        interactionSource = interaction,
    ) {
        // The stopwatch of the tab: a practice is time. The one filled button that carries an icon.
        IconLabel(
            icon = AppIcons.Timer,
            text = stringResource(R.string.practice_start),
            iconSize = IconSizes.InFilledButton,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** What the clock of the lights needs to know of the button: whether any of it is on the screen, and how wide it is. */
@Stable
private class ButtonWatch {
    /** True until the layout says otherwise: the first frames come before the first word of it. */
    var seen by mutableStateOf(true)
    var widthDp = StartButtonMath.REFERENCE_WIDTH_DP
}

/**
 * Seconds of the lights, stepped thirty times a second on the frames the postcards step on — they
 * share the screen — while the button is seen and not [calm]. The pace eases in and out over
 * [StartButtonMath.EASE_S]; off the screen it stops at once, since nobody sees it. Asleep while
 * there is nothing to move. Null with «убрать анимации»: the button shows its moment of rest.
 */
@Composable
private fun rememberLightSeconds(watch: ButtonWatch, calm: () -> Boolean): FloatState? {
    val reduce = LocalReduceMotion.current
    val seconds = remember { mutableFloatStateOf(StartButtonMath.REST_SECONDS) }
    val calmNow by rememberUpdatedState(calm)
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        var clock = seconds.floatValue
        var pace = 0f
        while (true) {
            snapshotFlow { watch.seen && !calmNow() }.first { it }
            var last = withFrameNanos { it }
            var shown = last
            while (watch.seen && (pace > 0f || !calmNow())) {
                val now = withFrameNanos { it }
                val dt = (now - last) / NANOS_PER_SECOND
                last = now
                pace = StartButtonMath.eased(pace, if (calmNow()) 0f else 1f, dt)
                clock += dt * pace * StartButtonMath.paceFor(watch.widthDp)
                if (SceneMotion.frameDue(now, shown)) {
                    shown = now
                    seconds.floatValue = clock
                }
            }
            seconds.floatValue = clock
            if (!watch.seen) pace = 0f
        }
    }
    return if (reduce) null else seconds
}

/**
 * What the button draws: the glow under it and the lit fill. The brushes and the glow are made once
 * for a size; a frame only moves the lights. The glow is the pill blurred in software, once, at a
 * quarter of the pixels — a mask blur works there on every Android version — and a frame only lays
 * the picture down, larger and brighter under a finger.
 */
@Stable
private class PearlLook(private val base: Color, private val lights: List<Color>, private val glowColor: Color) {
    private var madeFor = Size.Unspecified
    private var madeAt = 0f
    private var brushes: List<Brush> = emptyList()
    private var glow: ImageBitmap? = null
    private var glowReach = 0f

    fun drawGlow(scope: DrawScope, press: Float) = with(scope) {
        prepare()
        val image = glow ?: return@with
        val reach = glowReach + GlowPressGrow.toPx() * press
        drawImage(
            image = image,
            dstOffset = IntOffset((-reach).roundToInt(), (GlowDrop.toPx() - reach).roundToInt()),
            dstSize = IntSize((size.width + reach * 2).roundToInt(), (size.height + reach * 2).roundToInt()),
            alpha = PracticeMotion.START_GLOW_ALPHA + (PracticeMotion.START_GLOW_PRESSED_ALPHA - PracticeMotion.START_GLOW_ALPHA) * press,
            filterQuality = FilterQuality.Low,
        )
    }

    fun drawFill(scope: DrawScope, seconds: Float) = with(scope) {
        prepare()
        drawRect(base)
        brushes.forEachIndexed { index, brush ->
            val light = StartButtonMath.LIGHTS[index]
            val centre = StartButtonMath.centreAt(index, seconds)
            val radiusY = light.radiusY * size.height
            // an oval: a round light stretched sideways, its gradient stretched with it
            withTransform({
                translate(centre.x * size.width, centre.y * size.height)
                scale(light.radiusX * size.width / radiusY, 1f, pivot = Offset.Zero)
            }) {
                drawCircle(brush, radius = radiusY, center = Offset.Zero)
            }
        }
    }

    private fun DrawScope.prepare() {
        if (size == madeFor && density == madeAt) return
        madeFor = size
        madeAt = density
        brushes = lights.mapIndexed { index, color ->
            val light = StartButtonMath.LIGHTS[index]
            val stops = Array(StartButtonMath.EDGE_AT.size) { i ->
                StartButtonMath.EDGE_AT[i] to color.copy(alpha = light.alpha * StartButtonMath.EDGE_ALPHA[i])
            }
            Brush.radialGradient(*stops, center = Offset.Zero, radius = light.radiusY * size.height)
        }
        val sigma = GlowSigma.toPx()
        glowReach = sigma * GLOW_REACH
        glow = glowOf(size, sigma, glowReach)
    }

    private fun glowOf(size: Size, sigma: Float, reach: Float): ImageBitmap? {
        if (size.width <= 0f || size.height <= 0f) return null
        val k = GLOW_RESOLUTION
        val image = ImageBitmap(((size.width + reach * 2) * k).roundToInt(), ((size.height + reach * 2) * k).roundToInt())
        val paint = Paint().apply { color = glowColor }
        val radius = ((sigma * k - SKIA_SIGMA_BIAS) / SKIA_SIGMA_PER_RADIUS).coerceAtLeast(MIN_BLUR_RADIUS)
        paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        val corner = size.height / 2 * k
        Canvas(image).drawRoundRect(reach * k, reach * k, (reach + size.width) * k, (reach + size.height) * k, corner, corner, paint)
        return image
    }
}
