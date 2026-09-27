package com.violinjourney.app.core.audio

/**
 * The frame-clock time of a stream on the clock its input stamps its sound with (spec 5.25): the microphone of each
 * platform says, now and then, that the sample [anchorFrame] of the stream was captured at [anchorNanos]; a moment
 * [tMs] of the stream is that stamp plus the samples between. The sample is a whole one — a time between two samples
 * falls to the earlier. What the input adds on top (its latency on iOS) the platform takes off [anchorNanos] itself.
 * One formula for both platforms: the shift of a take under a backing is computed from it.
 */
object FrameClock {
    private const val MS_PER_SECOND = 1_000L
    private const val NANOS_PER_SECOND = 1_000_000_000L

    fun nanosAt(tMs: Long, anchorFrame: Long, anchorNanos: Long, sampleRateHz: Int): Long =
        anchorNanos + ((tMs * sampleRateHz / MS_PER_SECOND) - anchorFrame) * NANOS_PER_SECOND / sampleRateHz
}
