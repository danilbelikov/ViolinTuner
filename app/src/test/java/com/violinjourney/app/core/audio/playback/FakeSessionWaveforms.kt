package com.violinjourney.app.core.audio.playback

import java.io.File

/** A flat waveform for any file; remembers what it was asked to keep. */
class FakeSessionWaveforms : SessionWaveforms {
    var kept: Set<String>? = null
    var asked = 0

    /** The waveforms reckoned along with an analysis and kept, by the name of their sound. */
    val stored = mutableMapOf<String, FloatArray>()

    override suspend fun of(audio: File): FloatArray? {
        asked++
        return FloatArray(SessionWaveforms.BARS) { 0.5f }
    }

    override suspend fun keep(audio: File, waveform: FloatArray) {
        stored[audio.name] = waveform
    }

    override suspend fun deleteOrphans(audioNames: Set<String>) {
        kept = audioNames
    }
}
