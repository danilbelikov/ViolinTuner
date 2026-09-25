package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The analysis of a file on iOS — how a video take is heard: three seconds of A4 come out as a session in tune. */
@OptIn(ExperimentalForeignApi::class)
class IosFileAnalysisTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun `three seconds of A4 are a recorded session`() = runTest {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(3 * rate / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 10_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())

        val analyzer = DecodingFileTakeAnalyzer(PitchDetectorFactory(::MpmDetector), RepertoireConfig(), Dispatchers.Default, IosPcmFileOpener)
        val result = analyzer.analyze(PlatformFile(path), IntonationConfig(), startedAtEpochMs = 0, audioFileName = "take.m4a") {}
        val recorded = assertIs<FileAnalysisResult.Recorded>(result)
        assertTrue(recorded.session.durationMs in 2_500L..3_200L, "duration ${recorded.session.durationMs}")
    }

    @Test
    fun `a file opened from its middle gives the rest of the sound`() {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        repeat(2 * rate / hop.size) { encoder.offer(hop, hop.size) }
        assertTrue(encoder.finish())

        val whole = assertNotNull(IosPcmFileOpener.open(PlatformFile(path)))
        val rest = assertNotNull(IosPcmFileOpener.openAt(PlatformFile(path), fromSample = rate.toLong()))
        try {
            assertEquals(whole.totalSamples, rest.totalSamples, "the length stays the whole file's")
            val all = count(whole)
            val tail = count(rest)
            assertTrue(tail in rate / 2..rate * 3 / 2, "from the middle: $tail of $all")
        } finally {
            whole.release()
            rest.release()
        }
    }

    private fun count(pcm: OpenedPcm): Int {
        val chunk = ShortArray(4_096)
        var total = 0
        while (true) {
            val read = pcm.read(chunk)
            if (read == PcmSource.END) return total
            total += read
        }
    }
}
