package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.recording.OpenedPcm
import com.violinjourney.app.core.recording.PcmFileOpener
import java.io.File

/** The sound track of a file on Android: [PcmDecoder] finds it among the others of the container. */
object AndroidPcmFileOpener : PcmFileOpener {
    override fun open(file: File): OpenedPcm? = PcmDecoder.open(file)?.let { decoder ->
        object : OpenedPcm {
            override val sampleRate = decoder.sampleRate
            override val totalSamples = decoder.totalSamples

            override fun read(out: ShortArray): Int = decoder.read(out)

            override fun release() = decoder.release()
        }
    }
}
