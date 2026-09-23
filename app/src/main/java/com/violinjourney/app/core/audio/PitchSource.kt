package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlinx.coroutines.flow.Flow

/** A stream of pitch estimates. Collecting starts the source, cancelling the collector stops it. */
interface PitchSource {
    /** True when [frames] must not be collected before RECORD_AUDIO is granted. */
    val requiresMicPermission: Boolean

    /** The sound behind [frames] for session recordings; null when the source has no sound. */
    val audioTap: AudioTap?

    /**
     * Where a frame-clock time lies on `CLOCK_MONOTONIC` — the clock the output reports on too, so a take and its
     * backing can be lined up (spec 5.25). Null for a source without such a clock (the fake one).
     */
    val clock: SampleClock? get() = null

    /**
     * Frames analysed with [config], which may change between collections as the player edits
     * the settings. Fails with [MicUnavailableException] when the input cannot be opened or
     * breaks down.
     */
    fun frames(config: IntonationConfig): Flow<PitchFrame>
}

/** Frame-clock time → nanoseconds of `CLOCK_MONOTONIC`; null while the input has not told its time yet. */
fun interface SampleClock {
    fun nanosAt(tMs: Long): Long?
}

/** The microphone could not be opened or stopped delivering audio; retrying later may help. */
class MicUnavailableException(message: String) : Exception(message)
