package com.example.violintuner.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The light that travels along the filled part of the level bar every few seconds (spec 3.16).
 * A reflection on a surface, not a change of the fill. Lives in drawing only: [draw] reads the
 * pass in the draw phase, so the header redraws six dp of bar and recomposes nothing.
 */
@Stable
internal class LevelShine {
    /** Width of the whole bar, told by the layout; the pass is sized by what is filled of it. */
    var barWidthPx: Int = 0

    /** Eased progress of the running pass, or a negative number between passes. */
    var progress by mutableFloatStateOf(NONE)

    /** Set for the length of one pass: the fill it was sized for. */
    var pass: ShinePass? = null

    private val clip = Path()

    fun draw(scope: DrawScope, fillWidth: Float, corner: Float, color: Color) = with(scope) {
        val now = progress
        val running = pass
        if (now < 0f || running == null) return@with
        val band = running.bandDp * density
        val left = running.bandLeftDp(now) * density
        clip.reset()
        clip.addRoundRect(RoundRect(0f, 0f, fillWidth, size.height, CornerRadius(corner)))
        // Inside the fill only: on a short bar and on a full one nothing reaches past its rounded end.
        clipPath(clip) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), startX = left, endX = left + band),
                topLeft = Offset(left, 0f),
                size = Size(band, size.height),
            )
        }
    }

    companion object {
        const val NONE = -1f
    }
}

/**
 * Runs the passes. One long-lived loop that sleeps between them (`delay` spends no frames) and
 * steps by frames only while the light is on its way. It gives way to the bar itself: no pass
 * starts while [filled] is moving, a running one is dropped, and the next waits for
 * [LevelShineMath.AFTER_GROWTH_MS] after the bar has come to rest.
 */
@Composable
internal fun rememberLevelShine(filled: Animatable<Float, AnimationVector1D>, enabled: Boolean): LevelShine {
    val shine = remember { LevelShine() }
    val density = LocalDensity.current.density
    LaunchedEffect(enabled, density) {
        shine.progress = LevelShine.NONE
        if (!enabled) return@LaunchedEffect
        var rest = (LevelShineMath.PERIOD_MS - LevelShineMath.FULL_PASS_MS).toLong()
        while (true) {
            delay(rest)
            if (filled.isRunning) {
                snapshotFlow { filled.isRunning }.first { !it }
                rest = LevelShineMath.AFTER_GROWTH_MS
                continue
            }
            val pass = LevelShineMath.passOf(shine.barWidthPx * filled.value / density)
            if (pass == null) {
                rest = LevelShineMath.PERIOD_MS.toLong()
                continue
            }
            shine.pass = pass
            val start = withFrameMillis { it }
            var gaveWay = false
            while (true) {
                val fraction = withFrameMillis { (it - start).toFloat() / pass.durationMs }
                if (filled.isRunning) {
                    gaveWay = true
                    break
                }
                if (fraction >= 1f) break
                shine.progress = FastOutSlowInEasing.transform(fraction)
            }
            shine.progress = LevelShine.NONE
            shine.pass = null
            rest = if (gaveWay) 0L else LevelShineMath.pauseAfter(pass)
        }
    }
    return shine
}
