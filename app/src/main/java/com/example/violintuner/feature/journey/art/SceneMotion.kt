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
    const val WINDOW_DIP = 0.14f
    const val GLOW_DIP = 0.18f
    const val GLOW_PERIOD_S = 5f
    const val WATER_DRIFT = 7f
    const val WATER_PERIOD_S = 9f

    /** The least time between two frames of a living postcard: thirty a second is plenty for something this slow. */
    const val FRAME_NANOS = 33_000_000L

    private val WINDOWS = setOf("window", "windowLit", "chandelier")
    private const val WATER = "waterLit"

    fun moves(layer: SceneLayer, mode: SceneMode): Boolean =
        layer.fill == WATER || (mode == SceneMode.EVENING && (layer.fill in WINDOWS || layer.fill == SceneLayer.GLOW || layer.warmGlow))

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

    /** 0…1…0, a sine. */
    private fun wave(seconds: Float, period: Float, phase: Float): Float = 0.5f + 0.5f * sin(2 * PI.toFloat() * (seconds + phase) / period)
}

/**
 * Where the eye stands before a postcard shown larger than its frame: a zoom and a pan in pixels.
 * The planes answer the pan by their depth — the far one lags, which is all the parallax there is.
 * Pure geometry, with tests.
 */
object SceneCamera {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 2.5f
    const val DOUBLE_TAP_ZOOM = 2f

    /** How much of the pan each plane takes: far, middle, near. */
    private val FOLLOW = floatArrayOf(0.6f, 0.85f, 1f)

    /** The scale at which the grid covers the box: cropped, never stretched. */
    fun cover(width: Float, height: Float): Float = maxOf(width / SceneGrid.WIDTH, height / SceneGrid.HEIGHT)

    fun zoom(current: Float, change: Float): Float = (current * change).coerceIn(MIN_ZOOM, MAX_ZOOM)

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
