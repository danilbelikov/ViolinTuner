package com.violinjourney.app.feature.sound.components

import androidx.compose.runtime.snapshotFlow
import com.violinjourney.app.feature.journey.art.awaitGridFrame
import kotlinx.coroutines.flow.first

/** How the level bars of «Звук» fall (spec 5.11 «рост мгновенный, спад ~300 мс»): the same for the output and «Сейчас сжимает». */
internal object MeterFall {
    /** The whole bar, full to empty. */
    const val FULL_FALL_MS = 300L

    /** A bar at [level] (0…1) after [elapsedMs] with a reading of [target]: up at once, down at [FULL_FALL_MS] a bar. */
    fun next(level: Float, target: Float, elapsedMs: Long): Float = maxOf(target, level - elapsedMs.toFloat() / FULL_FALL_MS, 0f)
}

/** The motion of a meter, stepped by frame times; pure — the screen only draws what it says. */
internal interface MeterMotion<R : Any> {
    fun step(nowMs: Long, reading: R?)

    /** Nothing plays and nothing is left to show: the frames may stop until a reading comes. */
    fun atRest(reading: R?): Boolean

    /** Asleep: the time spent so is not a frame to fall by, and the first frame after it shows its number at once. */
    fun rest()
}

/**
 * The frames of a meter: thirty a second on the postcards' grid ([awaitGridFrame]) — the readings come ~23 times a
 * second, and on iOS every frame waited for is a frame drawn — while there is something to show, written inside the
 * frame by [show]. At rest it sleeps until a [reading] comes (spec 5.11: «только пока звук играет»).
 */
internal suspend fun <R : Any> runMeter(motion: MeterMotion<R>, reading: () -> R?, show: () -> Unit) {
    var shown = 0L
    while (true) {
        if (motion.atRest(reading())) {
            motion.rest()
            show()
            snapshotFlow { reading() }.first { it != null }
        }
        shown = awaitGridFrame(shown) { now ->
            motion.step(now / NANOS_PER_MILLI, reading())
            show()
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
