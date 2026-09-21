package com.example.violintuner.feature.journey.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * How a postcard lives (spec 3.23): nothing is marked in the scenes for it — a layer moves by what
 * it is filled with. Lit windows waver a little, each at its own pace (the handoff's `flick`),
 * the glow of the lamps breathes, the glints on the water drift. Slow and of low contrast: a
 * postcard, not a screensaver. Pure; time comes from outside, in seconds.
 */
object SceneMotion {
    const val WINDOW_DIP = 0.32f
    const val GLOW_DIP = 0.4f
    const val GLOW_PERIOD_S = 3.5f
    const val WATER_DRIFT = 14f
    const val WATER_PERIOD_S = 6f

    /** Stars of the evening sky and clouds of the day one: not layers of a scene, drawn by rule behind everything but the sky — outdoors only. */
    const val STARS = 70
    const val STAR_DIP = 0.75f
    const val CLOUDS = 3
    const val CLOUD_SPEED = 5f
    private const val SKY_SPAN = 760f
    private const val STAR_TOP = -420f
    private const val STAR_BOTTOM = 100f
    private const val STAR_FADE_FROM = -40f
    private const val SKY_LEFT = -170f

    /** The least time between two frames of a living postcard: thirty a second is plenty for something this slow. */
    const val FRAME_NANOS = 33_000_000L

    const val BIRD_FADE = 12f
    const val BIRD_BOB = 3f
    const val BIRD_FLAP_HZ = 2.6f
    const val FALL_FADE = 0.25f

    private val WINDOWS = setOf("window", "windowLit", "chandelier")
    private const val WATER = "waterLit"

    fun moves(layer: SceneLayer, mode: SceneMode): Boolean =
        (layer.anim != null && layer.anim != SceneAnim(dash = layer.anim.dash)) || layer.fill == WATER || (mode == SceneMode.EVENING && (layer.fill in WINDOWS || layer.fill == SceneLayer.GLOW || layer.warmGlow))

    /** What the layer's own opacity is multiplied by at [seconds]; 1 for a layer that does not flicker. */
    fun alpha(layer: SceneLayer, index: Int, mode: SceneMode, seconds: Float): Float = when {
        mode != SceneMode.EVENING -> 1f
        layer.fill in WINDOWS -> 1f - WINDOW_DIP * wave(seconds, period = 4f + index % 3, phase = (index % 5) * 0.6f)
        layer.fill == SceneLayer.GLOW || layer.warmGlow -> 1f - GLOW_DIP * wave(seconds, GLOW_PERIOD_S, phase = index % 4 * 0.9f)
        else -> 1f
    }

    /** How far, in units of the grid, the layer has drifted sideways at [seconds]. */
    fun drift(layer: SceneLayer, index: Int, seconds: Float): Float =
        if (layer.fill == WATER) WATER_DRIFT * sin(2 * PI.toFloat() * seconds / WATER_PERIOD_S + index * 1.7f) else 0f

    /** What a moving thing is at [seconds]: shifted by [dx], [dy] from where it is drawn, flattened to [flap] of its height (a bird's wings), seen at [alpha]. */
    data class Moved(val dx: Float, val dy: Float, val flap: Float, val alpha: Float, val degrees: Float = 0f, val pivotX: Float = 0f, val pivotY: Float = 0f) {
        companion object {
            val STILL = Moved(0f, 0f, 1f, 1f)
        }
    }

    /**
     * Where [anim] has taken a layer at [seconds]. A ride and a bob depend on the time alone, so
     * every layer of one tram moves as one; birds and falling petals are single layers and take
     * their own pace from [index]. [baseX], [baseY] is where the layer is drawn — where it is when nothing moves.
     */
    fun moved(anim: SceneAnim, baseX: Float, baseY: Float, index: Int, seconds: Float): Moved {
        var dx = 0f
        var dy = 0f
        var flap = 1f
        var alpha = 1f
        anim.ride?.let { ride ->
            val span = ride.to - ride.from
            if (span > 0f) dx += ride.from + (((-ride.from + seconds * ride.speed) % span) + span) % span
        }
        anim.bob?.let { bob -> dy += bob.amplitude * sin(2 * PI.toFloat() * (seconds + bob.delay) / bob.period) }
        anim.fly?.let { fly -> dx += fly.amplitude * ((seconds % fly.period) / fly.period) }
        anim.bird?.let { way ->
            val speed = 8f + hash(index, 11) * 7f
            val along = (((baseX - way.left) + seconds * speed) % way.span + way.span) % way.span
            dx += way.left + along - baseX
            dy += BIRD_BOB * sin(2 * PI.toFloat() * seconds / (3f + hash(index, 12) * 2f) + index)
            flap = 0.35f + 0.65f * wave(seconds, 1f / BIRD_FLAP_HZ, phase = hash(index, 13))
            alpha *= (minOf(along, way.span - along) / BIRD_FADE).coerceIn(0f, 1f)
        }
        anim.flashPeriod?.let { period -> if ((seconds % period) / period > 0.35f) alpha = 0f }
        // eyes: shut for a twentieth of the period, each pair in its own time
        anim.blinkPeriod?.let { period -> val at = ((seconds + (index % 4) * 0.7f) % period) / period; if (at in 0.92f..0.97f) alpha = 0f }
        anim.flick?.let { alpha *= 1f - 0.45f * wave(seconds + it.delay, it.period, phase = 0f) }
        anim.rise?.let { val at = (((seconds + it.delay) % it.period) + it.period) % it.period / it.period; dy -= 10f * at; alpha *= 0.7f * (1f - at) }
        anim.sway?.let { dx += it.amplitude * sin(2 * PI.toFloat() * (seconds + it.delay) / it.period) }
        var degrees = 0f
        anim.swing?.let { degrees = it.degrees * sin(2 * PI.toFloat() * seconds / it.period) }
        anim.fall?.let { fall ->
            // where it is drawn is where it is when nothing moves: the fall starts from there
            val start = (baseY - fall.top).coerceIn(0f, fall.height)
            val down = (start + seconds * fall.speed * (0.7f + hash(index, 15) * 0.6f)) % fall.height
            dy += down - start
            dx += 5f * (sin(down / 9f + index) - sin(start / 9f + index))
            val left = 1f - down / fall.height
            alpha *= (left / FALL_FADE).coerceIn(0f, 1f) * (down / (fall.height * 0.1f)).coerceIn(0f, 1f)
        }
        return Moved(dx, dy, flap, alpha, degrees, anim.swing?.pivotX ?: 0f, anim.swing?.pivotY ?: 0f)
    }

    /** A star: where it is on the grid, how large, and how bright at [seconds] — each twinkles at its own pace. */
    data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

    fun star(index: Int, seconds: Float): Star {
        val x = SKY_LEFT + hash(index, 1) * SKY_SPAN
        // far above the frame as well: the whole card seen upright has a tall sky over it
        val y = STAR_TOP + hash(index, 2) * (STAR_BOTTOM - STAR_TOP)
        // fainter towards the glow of the horizon
        val height = 1f - ((y - STAR_FADE_FROM) / (STAR_BOTTOM - STAR_FADE_FROM)).coerceIn(0f, 1f) * 0.7f
        val twinkle = 1f - STAR_DIP * wave(seconds, period = 1.6f + hash(index, 3) * 2.4f, phase = hash(index, 4) * 4f)
        return Star(x, y, 0.5f + hash(index, 5) * 0.7f, (height * twinkle).coerceIn(0f, 1f))
    }

    /** A cloud: its centre and size at [seconds]; it sails to the right and comes back from the left. */
    data class Cloud(val x: Float, val y: Float, val width: Float)

    fun cloud(index: Int, seconds: Float): Cloud {
        val width = 70f + hash(index, 6) * 50f
        val start = hash(index, 7) * SKY_SPAN
        val speed = CLOUD_SPEED * (0.6f + hash(index, 8) * 0.8f)
        return Cloud(SKY_LEFT + (start + seconds * speed) % SKY_SPAN, 20f + index * 26f + hash(index, 9) * 10f, width)
    }

    /** A number in 0…1 that is always the same for the same [index] and [salt]: the sky is the same sky every time. */
    private fun hash(index: Int, salt: Int): Float {
        var h = index * 374_761_393 + salt * 668_265_263
        h = (h xor (h ushr 13)) * 1_274_126_177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65_535f
    }

    /** 0…1…0, a sine. */
    private fun wave(seconds: Float, period: Float, phase: Float): Float = 0.5f + 0.5f * sin(2 * PI.toFloat() * (seconds + phase) / period)
}

/**
 * Where the eye stands before a postcard shown larger than its frame: a zoom and a pan in pixels.
 * The planes answer the pan by their depth — the far one lags, which is all the parallax there is.
 * Pure geometry, with tests.
 */
object SceneCamera {
    /** 1 is the zoom at which the picture covers the box. */
    const val COVER_ZOOM = 1f
    const val MAX_ZOOM = 2.5f
    const val DOUBLE_TAP_ZOOM = 2f

    /** How much of the pan each plane takes: far, middle, near. */
    private val FOLLOW = floatArrayOf(0.6f, 0.85f, 1f)

    /** The scale at which the grid covers the box: cropped, never stretched. */
    fun cover(width: Float, height: Float): Float = maxOf(width / SceneGrid.WIDTH, height / SceneGrid.HEIGHT)

    /** The zoom at which the whole card is seen by its width: below 1 upright, 1 where the card already fits. */
    fun wholeZoom(width: Float, height: Float): Float = ((width / SceneGrid.WIDTH) / cover(width, height)).coerceAtMost(COVER_ZOOM)

    fun zoom(current: Float, change: Float, width: Float, height: Float): Float = (current * change).coerceIn(wholeZoom(width, height), MAX_ZOOM)

    /** A double tap walks round: covering → closer → the whole card → covering. */
    fun nextZoom(current: Float, width: Float, height: Float): Float {
        val whole = wholeZoom(width, height)
        return when {
            current > COVER_ZOOM + EPSILON -> if (whole < COVER_ZOOM - EPSILON) whole else COVER_ZOOM
            current < COVER_ZOOM - EPSILON -> COVER_ZOOM
            else -> DOUBLE_TAP_ZOOM
        }
    }

    /**
     * Where the top of the picture stands when it is lower than the box. Outdoors it stands on the
     * bottom edge and the sky — which the scenes have plenty of — fills the rest; a room has no sky,
     * so it is centred, like a photograph on a dark table.
     */
    fun top(zoom: Float, width: Float, height: Float, outdoors: Boolean): Float {
        val tall = SceneGrid.HEIGHT * cover(width, height) * zoom
        return if (tall < height && outdoors) height - tall else (height - tall) / 2
    }

    private const val EPSILON = 0.01f

    /** The pan kept within what the picture has beyond the box: the near plane never shows its edge, and the others lag inside it. */
    fun clamp(panX: Float, panY: Float, zoom: Float, width: Float, height: Float): Pair<Float, Float> {
        val k = cover(width, height) * zoom
        val overX = ((SceneGrid.WIDTH * k - width) / 2).coerceAtLeast(0f)
        val overY = ((SceneGrid.HEIGHT * k - height) / 2).coerceAtLeast(0f)
        return panX.coerceIn(-overX, overX) to panY.coerceIn(-overY, overY)
    }

    /** The sideways shift of a plane, in pixels, for a pan of [panX]. */
    fun shift(panX: Float, depth: Int): Float = panX * FOLLOW[depth.coerceIn(0, 2)]
}

/**
 * Seconds for a living postcard, stepped by frames while [enabled] and while «убрать анимации» is
 * off; it is read where the scene is drawn, so the postcard is redrawn, never recomposed. Null
 * when nothing should move.
 */
@Composable
fun rememberSceneSeconds(enabled: Boolean = true): State<Float>? {
    val reduce = LocalReduceMotion.current
    val seconds = remember { mutableFloatStateOf(0f) }
    val run = enabled && !reduce
    LaunchedEffect(run) {
        if (!run) return@LaunchedEffect
        var start = -1L
        var shown = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                if (now - shown >= SceneMotion.FRAME_NANOS) {
                    shown = now
                    seconds.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return if (run) seconds else null
}
