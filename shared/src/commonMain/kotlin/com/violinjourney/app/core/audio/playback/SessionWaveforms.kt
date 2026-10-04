package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.io.PlatformFile

/** The waveform of a recording for the player at the bottom of «Звук» and of the recording (spec 3.17, 3.36.5, 5.11): [BARS] columns, 0…1, the loudest is 1. */
interface SessionWaveforms {
    /** Null when the sound cannot be decoded. Cancellable: leaving the screen stops the reckoning. */
    suspend fun of(audio: PlatformFile): FloatArray?

    /**
     * Keeps [waveform] — reckoned already, along with the analysis of a file (spec 5.13, 0.94) — as the waveform of [audio]: its first
     * opening reads it instead of decoding the whole sound again. Never throws: a waveform that cannot be kept is reckoned on that opening,
     * as before.
     */
    suspend fun keep(audio: PlatformFile, waveform: FloatArray)

    /** [audioNames] — the sound files that still belong to a session; every waveform of another name goes. */
    suspend fun deleteOrphans(audioNames: Set<String>)

    companion object {
        /** Columns 2 dp wide every 3 dp across the player of a 412-dp screen (handoff `sizes`). */
        const val BARS = 120
    }
}
