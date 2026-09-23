package com.violinjourney.app.core.audio.recording

import java.io.File

/**
 * Lets a session recording take the sound of the stream the pitch frames come from (spec 3.9).
 * Audio and frames share one sample clock, so a take that starts at [State.Running.startTMs]
 * lines up with frames of that time without any fitting.
 */
interface AudioTap {
    sealed interface State {
        data object Idle : State

        /** Asked to start; the take begins with the next hop the stream reads. */
        data object Starting : State

        /** [startTMs] is the frame-clock time of the first recorded sample. */
        data class Running(val startTMs: Long) : State

        /** The encoder could not be created or fell behind; the session goes on without audio. */
        data object Failed : State
    }

    val state: State

    /** The rate of the take being recorded; null before its first hop. */
    val sampleRateHz: Int? get() = null

    /** Thread-safe. Does nothing unless idle. */
    fun start(file: File)

    /** Finishes the file. True when it holds a usable take; back to idle either way. */
    suspend fun stop(): Boolean
}

/** Encodes 16-bit mono PCM into a file. Created and fed on the audio thread. */
interface PcmEncoder {
    /** Never blocks. False when the encoder cannot keep up or has failed: stop feeding it. */
    fun offer(hop: ShortArray, count: Int): Boolean

    /** Flushes and closes the file; may block for a moment. True when the file is complete. */
    fun finish(): Boolean
}

fun interface PcmEncoderFactory {
    /** May throw when the device cannot provide the encoder. */
    fun create(file: File, sampleRateHz: Int): PcmEncoder
}
