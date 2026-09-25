package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.io.PlatformFile

/**
 * The picture of the app's camera and the sound of the take's own chain, made into one `.mp4` (spec 3.32, 5.25): the
 * sound stays at zero, the picture is moved by [shiftUs] — how much later it began. Nothing is re-encoded. True when the
 * whole thing worked; [target] is whole then, and gone otherwise.
 */
fun interface VideoMux {
    suspend fun mux(picture: PlatformFile, sound: PlatformFile, target: PlatformFile, shiftUs: Long): Boolean
}

/** How far the picture is moved against the sound. Pure. */
object VideoShift {
    /** In µs: the picture's start minus the sound's, both on the clock the microphone reports on. */
    fun shiftUs(pictureStartNanos: Long, soundStartNanos: Long): Long = (pictureStartNanos - soundStartNanos) / NANOS_PER_US

    /** Where a picture sample at [ptsUs] lands after the shift; null — before the sound, left out. */
    fun shiftedUs(ptsUs: Long, shiftUs: Long): Long? = (ptsUs + shiftUs).takeIf { it >= 0 }

    private const val NANOS_PER_US = 1_000L
}
