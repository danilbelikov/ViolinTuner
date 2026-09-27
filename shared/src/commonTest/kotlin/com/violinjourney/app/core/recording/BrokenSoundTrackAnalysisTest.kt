package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.dsp.SignalSynth
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.platformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * A sound track whose reader gave up half-way (iOS: `AVAssetReader` failed — the app went to the background, a damaged
 * stretch) is not a shorter take: the analysis cannot open it, as a file with no readable sound.
 */
class BrokenSoundTrackAnalysisTest {
    private val rate = 48_000
    private val tone = SignalSynth.toPcm16(SignalSynth.tone(440.0, rate, 3 * rate, SignalSynth.VIOLIN))

    /** Three seconds of A4, then the end — or the reader giving up there. */
    private inner class Pcm(override val broken: Boolean) : OpenedPcm {
        private var at = 0
        override val sampleRate = rate
        override val totalSamples = 10L * rate

        override fun read(out: ShortArray): Int {
            if (at >= tone.size) return PcmSource.END
            val count = minOf(out.size, tone.size - at)
            tone.copyInto(out, 0, at, at + count)
            at += count
            return count
        }

        override fun release() = Unit
    }

    private suspend fun analyze(broken: Boolean): FileAnalysisResult =
        DecodingFileTakeAnalyzer(PitchDetectorFactory(::MpmDetector), RepertoireConfig(), Dispatchers.Default, PcmFileOpener { Pcm(broken) })
            .analyze(platformFile("take.mp4"), IntonationConfig(), startedAtEpochMs = 0, audioFileName = "take.mp4") {}

    @Test
    fun `a sound the reader gave up on half-way cannot be opened`() = runTest {
        assertEquals(FileAnalysisResult.CannotOpen, analyze(broken = true))
    }

    @Test
    fun `the same sound read to its end is a recorded take`() = runTest {
        assertIs<FileAnalysisResult.Recorded>(analyze(broken = false))
    }
}
