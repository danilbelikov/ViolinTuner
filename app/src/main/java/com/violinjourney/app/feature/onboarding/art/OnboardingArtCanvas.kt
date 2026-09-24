package com.violinjourney.app.feature.onboarding.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.runtime.withFrameNanos
import com.violinjourney.app.core.ui.theme.ZoneColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** How the 412 × 480 field is cut to the box: to its bottom in portrait, to its middle in landscape (handoff `dev`). */
internal enum class ArtFit { BOTTOM, CENTER }

/**
 * The pictures of «Знакомство» (spec 3.33): one sky for all the pages, and over it the land of the page
 * at [position] and of its neighbour while a swipe runs between them. The sky moves at 0.3 of the page,
 * the far land at 0.6, the near at 1 — the handoff's depth. Everything that changes every frame — the
 * swipe, the clock, the height of the box — is read in the draw phase: the picture redraws, nothing recomposes.
 *
 * @param height the height of the picture in px, from the top; null — the whole box.
 * @param shownPage the page in view: its motions that happen once start again when it comes back.
 * @param foregroundAlpha the land and its life, not the sky — «Пропустить» dissolves them (36h2).
 * @param fadeEndPx a band at the right edge melting into [surface] — landscape, where the text stands on the right.
 */
@Composable
internal fun OnboardingArtCanvas(
    scenes: List<ArtScene>,
    position: () -> Float,
    shownPage: Int,
    fit: ArtFit,
    still: Boolean,
    zoneColors: ZoneColors,
    surface: Color,
    modifier: Modifier = Modifier,
    height: (() -> Float)? = null,
    foregroundAlpha: () -> Float = { 1f },
    fadeEndPx: Float = 0f,
) {
    val prepared = remember(scenes) { scenes.map(::PreparedArt) }
    val clock = rememberArtClock(still)
    val starts = remember { mutableStateMapOf<Int, Float>() }
    LaunchedEffect(shownPage) { starts[shownPage] = clock.value }

    Canvas(modifier) {
        val h = height?.invoke()?.coerceAtMost(size.height) ?: size.height
        if (h <= 0f) return@Canvas
        val w = size.width
        val s = max(w / OnboardingArtData.WIDTH, h / OnboardingArtData.HEIGHT)
        val ox = (w - OnboardingArtData.WIDTH * s) / 2f
        val oy = if (fit == ArtFit.BOTTOM) h - OnboardingArtData.HEIGHT * s else (h - OnboardingArtData.HEIGHT * s) / 2f
        val pageWidth = w / s
        val pos = position()
        val now = clock.value
        val alpha = foregroundAlpha()

        clipRect(0f, 0f, w, h) {
            drawRect(surface)
            translate(ox, oy) {
                scale(s, Offset.Zero) {
                    val sky = prepared.first()
                    drawSky(sky, now, still, shift = -pos * pageWidth * SKY_DEPTH)
                    val view = Rect(-ox / s, -oy / s, (w - ox) / s, (h - oy) / s)
                    val first = floor(pos).toInt()
                    for (page in first..first + 1) {
                        val art = prepared.getOrNull(page) ?: continue
                        val offset = (page - pos) * pageWidth
                        if (abs(page - pos) >= 1f) continue
                        val t = now - (starts[page] ?: now)
                        val zone = demoZone(LiveDemo.at(t, still), zoneColors)
                        // The far land lags behind the page, so it would still be half in view when its page
                        // is gone and would vanish at once: it melts away as its page leaves instead.
                        withAlpha(alpha * (1f - abs(page - pos)), view) {
                            drawDepth(art, 1, offset * FAR_DEPTH, t, still, zone, 1f)
                        }
                        drawDepth(art, 2, offset, t, still, zone, alpha)
                    }
                    drawFade(sky)
                }
            }
            if (fadeEndPx > 0f) {
                drawRect(Brush.horizontalGradient(0f to surface.copy(alpha = 0f), 1f to surface, startX = w - fadeEndPx, endX = w))
            }
        }
    }
}

/** Seconds since the pictures appeared, ticking every frame unless motion is removed. */
@Composable
private fun rememberArtClock(still: Boolean): State<Float> = produceState(0f, still) {
    if (still) return@produceState
    val start = withFrameNanos { it }
    while (true) withFrameNanos { value = (it - start) / NANOS_PER_SECOND }
}

private fun demoZone(mix: LiveDemo.Mix, zones: ZoneColors): Color =
    lerp(colorOf(mix.from, zones), colorOf(mix.to, zones), mix.fraction)

private fun colorOf(shown: LiveDemo.Shown, zones: ZoneColors) = when (shown) {
    LiveDemo.Shown.IN_TUNE -> zones.inTune
    LiveDemo.Shown.SHARP -> zones.near
    LiveDemo.Shown.FLAT -> zones.off
}

/** The sky stands; its stars drift with the pages and come round again, so every page has its sky full. */
private fun DrawScope.drawSky(art: PreparedArt, t: Float, still: Boolean, shift: Float) {
    art.layers.forEachIndexed { i, layer ->
        if (layer.depth != 0 || layer.fade) return@forEachIndexed
        if (layer.fill == SKY_FILL) {
            art.draw(this, i, t, still, Color.Unspecified, 1f)
        } else {
            val span = OnboardingArtData.WIDTH + 2 * STAR_MARGIN
            val x = art.centerX[i]
            val wrapped = ((x + shift + STAR_MARGIN) % span + span) % span - STAR_MARGIN
            translate(wrapped - x, 0f) { art.draw(this, i, t, still, Color.Unspecified, 1f) }
        }
    }
}

private fun DrawScope.drawDepth(art: PreparedArt, depth: Int, dx: Float, t: Float, still: Boolean, zone: Color, alpha: Float) {
    translate(dx, 0f) {
        art.layers.forEachIndexed { i, layer ->
            if (layer.depth == depth && !layer.fade) art.draw(this, i, t, still, zone, alpha)
        }
    }
}

/** Draws [block] as one layer at [alpha], so shapes of the same page never show through each other. */
private inline fun DrawScope.withAlpha(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
    if (alpha <= 0f) return
    if (alpha >= 1f) return block()
    val canvas = drawContext.canvas
    canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
    block()
    canvas.restore()
}

private fun DrawScope.drawFade(art: PreparedArt) {
    art.layers.forEachIndexed { i, layer -> if (layer.fade) art.draw(this, i, 0f, true, Color.Unspecified, 1f) }
}

/** A scene with its paths parsed and its brushes made once; only what follows the zone is made per frame. */
private class PreparedArt(scene: ArtScene) {
    val layers = scene.layers
    private val gradients = scene.gradients
    private val paths: List<Path> = layers.map { layer ->
        PathParser().parsePathString(layer.d).toPath().apply { if (layer.evenOdd) fillType = PathFillType.EvenOdd }
    }
    val centerX: List<Float> = paths.map { it.getBounds().center.x }
    private val motions: List<ArtMotion?> = layers.map { ArtMotion.parse(it.motion) }
    private val followsZone: List<Boolean> = layers.map { layer ->
        layer.fill == ZONE || gradientOf(layer)?.stops?.any { it.color == ZONE } == true
    }
    private val brushes: List<Brush?> = layers.mapIndexed { i, layer -> if (followsZone[i]) null else brushOf(layer, Color.Unspecified) }
    private val strokes: List<Stroke?> = layers.map { layer ->
        if (layer.stroke == null) null else Stroke(width = layer.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    private val strokeColors: List<Color?> = layers.map { layer -> layer.stroke?.let(::hexColor) }

    fun draw(scope: DrawScope, i: Int, t: Float, still: Boolean, zone: Color, groupAlpha: Float) {
        val layer = layers[i]
        val motion = motions[i]
        val alpha = (motion?.alpha(t, still) ?: 1f) * groupAlpha
        if (alpha <= 0f) return
        val shift = motion?.shift(t, still) ?: ArtShift.None
        scope.translate(shift.dx, shift.dy) {
            val brush = brushes[i] ?: if (followsZone[i]) brushOf(layer, zone) else null
            if (brush != null) drawPath(paths[i], brush, alpha = layer.opacity * alpha)
            val stroke = strokes[i]
            val color = strokeColors[i]
            if (stroke != null && color != null) drawPath(paths[i], color, alpha = layer.opacity * alpha, style = stroke)
        }
    }

    private fun gradientOf(layer: ArtLayer): ArtGradient? =
        layer.fill?.takeIf { it.startsWith(GRADIENT_PREFIX) }?.let { gradients.getValue(it.removePrefix(GRADIENT_PREFIX)) }

    private fun brushOf(layer: ArtLayer, zone: Color): Brush? {
        val fill = layer.fill ?: return null
        if (fill == ZONE) return SolidColor(zone)
        val gradient = gradientOf(layer) ?: return SolidColor(hexColor(fill))
        val stops = gradient.stops.map { stop ->
            val base = if (stop.color == ZONE) zone else hexColor(stop.color)
            stop.offset to base.copy(alpha = stop.alpha)
        }.toTypedArray()
        return when (gradient) {
            is ArtGradient.Linear -> Brush.linearGradient(*stops, start = Offset(gradient.x1, gradient.y1), end = Offset(gradient.x2, gradient.y2))
            is ArtGradient.Radial -> Brush.radialGradient(*stops, center = Offset(gradient.cx, gradient.cy), radius = gradient.r)
        }
    }
}

internal fun hexColor(hex: String): Color = Color(0xFF000000 or hex.removePrefix("#").toLong(HEX_RADIX))

private const val SKY_FILL = "g:sky"
private const val SKY_DEPTH = 0.3f
private const val FAR_DEPTH = 0.6f
private const val STAR_MARGIN = 60f
private const val HEX_RADIX = 16
private const val NANOS_PER_SECOND = 1_000_000_000f
