package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * A display of virtual time: a frame comes at the first vsync after it is asked for, its time an
 * uptime that is no multiple of a cell ([BOOT]) plus [leadNanos] — iOS gives the time the frame will
 * be shown, a vsync ahead. [afterFrame] runs once the frame's lambda has, before the waiter goes on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualDisplay(
    private val scheduler: TestCoroutineScheduler,
    private val vsyncNanos: Long,
    private val leadNanos: Long = 0,
) : MonotonicFrameClock {
    var asked = 0
        private set
    var afterFrame: () -> Unit = {}
    var onTime: (Long) -> Unit = {}

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        asked++
        val now = scheduler.currentTime * NANOS_PER_MILLI
        val vsync = (now / vsyncNanos + 1) * vsyncNanos
        delay((vsync - now + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI)
        val time = BOOT + vsync + leadNanos
        onTime(time)
        val result = onFrame(time)
        afterFrame()
        return result
    }

    private companion object {
        const val BOOT = 7_654_321_987_654L
    }
}

internal const val NANOS_PER_MILLI = 1_000_000L
internal const val VSYNC_120 = 8_333_333L
internal const val VSYNC_60 = 16_666_667L
internal const val SECOND_MS = 1_000L
