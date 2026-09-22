package com.example.violintuner.core.audio.share

import com.example.violintuner.core.domain.sound.SoundSettings
import java.io.File

/** Remembers that it was asked to sweep; hands out nothing. */
class FakeShareFiles : ShareFiles {
    var sweeps = 0
        private set
    var sweptAtMs: Long? = null
        private set

    override fun processed(audioName: String, settings: SoundSettings, fileName: String): File = File("/share/$fileName")

    override suspend fun original(audio: File, fileName: String): File? = null

    override suspend fun sweep(nowEpochMs: Long) {
        sweeps++
        sweptAtMs = nowEpochMs
    }
}
