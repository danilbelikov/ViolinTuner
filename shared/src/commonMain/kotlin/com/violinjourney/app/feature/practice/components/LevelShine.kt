package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The light that travels along the filled part of the level bar every few seconds (spec 3.16).
 * A reflection on a surface, not a change of the fill. Lives in drawing only: [draw] reads the
 * pass in the draw phase, so the header redraws six dp of bar and recomposes nothing. The band's
 * brush is made once for its width and colour and moved along; a frame makes nothing.
 */
@Stable
class LevelShine {
    /** Width of the whole bar, told by the layout; the pass is sized by what is filled of it. */
    var barWidthPx: Int = 0

    /** Eased progress of the running pass, or a negative number between passes. */
    var progress by mutableFloatStateOf(NONE)

    /** Set for the length of one pass: the fill it was sized for. */
    var pass: ShinePass? = null

    /** Whether any of the bar is on the screen, told by the layout; true until it speaks. Out of sight no pass runs. */
    var seen by mutableStateOf(true)

    private val clip = Path()
    private var brush: Brush? = null
    private var brushBand = 0f
    private var brushColor = Color.Unspecified

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
            translate(left, 0f) { drawRect(brush = brushFor(band, color), size = Size(band, size.height)) }
        }
    }

    /** The band from its left edge at 0: made again only for another width or colour, which is once a pass at most. */
    private fun brushFor(band: Float, color: Color): Brush {
        val made = brush
        if (made != null && band == brushBand && color == brushColor) return made
        return Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), startX = 0f, endX = band).also {
            brush = it
            brushBand = band
            brushColor = color
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
fun rememberLevelShine(filled: Animatable<Float, AnimationVector1D>, enabled: Boolean): LevelShine {
    val shine = remember { LevelShine() }
    val density = LocalDensity.current.density
    LaunchedEffect(enabled, density) {
        shine.progress = LevelShine.NONE
        if (!enabled) return@LaunchedEffect
        runShinePasses(shine, fill = { filled.value }, growing = { filled.isRunning }, density = density)
    }
    return shine
}

/**
 * The loop of [rememberLevelShine]: a pass every [LevelShineMath.PERIOD_MS], stepped by the frames of
 * the display — at thirty a second the band would jump by a third of its width — and written inside
 * them. It gives way to the bar while [growing]. While the bar is not [LevelShine.seen] (the header
 * scrolled away) no pass starts and a running one is dropped; back in view, the next comes after the
 * usual pause.
 */
internal suspend fun runShinePasses(shine: LevelShine, fill: () -> Float, growing: () -> Boolean, density: Float) {
    val unseenRest = (LevelShineMath.PERIOD_MS - LevelShineMath.FULL_PASS_MS).toLong()
    var rest = unseenRest
    while (true) {
        delay(rest)
        if (growing()) {
            snapshotFlow { growing() }.first { !it }
            rest = LevelShineMath.AFTER_GROWTH_MS
            continue
        }
        if (!shine.seen) {
            snapshotFlow { shine.seen }.first { it }
            rest = unseenRest
            continue
        }
        val pass = LevelShineMath.passOf(shine.barWidthPx * fill() / density)
        if (pass == null) {
            rest = LevelShineMath.PERIOD_MS.toLong()
            continue
        }
        shine.pass = pass
        val ended = runPass(shine, pass, growing)
        shine.pass = null
        rest = when (ended) {
            PassEnd.GAVE_WAY -> 0L
            PassEnd.UNSEEN -> unseenRest
            PassEnd.DONE -> LevelShineMath.pauseAfter(pass)
        }
    }
}

private enum class PassEnd { GAVE_WAY, UNSEEN, DONE }

/** One pass, frame by frame: the first frame only marks the time; the last puts the light out inside itself. */
private suspend fun runPass(shine: LevelShine, pass: ShinePass, growing: () -> Boolean): PassEnd {
    val start = withFrameMillis { it }
    while (true) {
        val ended = withFrameMillis { now ->
            val fraction = (now - start).toFloat() / pass.durationMs
            val end = when {
                growing() -> PassEnd.GAVE_WAY
                !shine.seen -> PassEnd.UNSEEN
                fraction >= 1f -> PassEnd.DONE
                else -> null
            }
            shine.progress = if (end == null) FastOutSlowInEasing.transform(fraction) else LevelShine.NONE
            end
        }
        if (ended != null) return ended
    }
}
