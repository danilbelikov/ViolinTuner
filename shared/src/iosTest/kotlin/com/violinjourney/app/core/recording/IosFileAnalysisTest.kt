package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.recording.Tone
import com.violinjourney.app.core.audio.recording.writeAacTones
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The analysis of a file on iOS — how a video take is heard: the notes, their cents and the share in tune come out as they
 * were played, as `DecodingFileTakeAnalyzerTest` holds on Android; a file of silence has no notes.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileAnalysisTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"
    private val config = IntonationConfig()
    private val analyzer = DecodingFileTakeAnalyzer(PitchDetectorFactory(::MpmDetector), RepertoireConfig(), Dispatchers.Default, IosPcmFileOpener)

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    private fun hz(midi: Int, cents: Double = 0.0) = 440.0 * 2.0.pow((midi - 69 + cents / 100) / 12)

    @Test
    fun `A4 in tune and then D5 twenty-five cents flat are heard as they were`() = runTest {
        writeAacTones(path, RATE, listOf(Tone(hz(69), 1.5), Tone(hz(74, cents = -25.0), 1.5)))

        val result = analyzer.analyze(PlatformFile(path), config, startedAtEpochMs = 0, audioFileName = "take.m4a") {}
        val session = assertIs<FileAnalysisResult.Recorded>(result).session
        assertTrue(session.durationMs in 2_850L..3_150L, "duration ${session.durationMs}")
        val bucket = config.sessionBucketMs
        val inTune = assertNotNull(session.samples[(750 / bucket).toInt()], "a note in the middle of A4")
        assertEquals(69, inTune.midi)
        assertTrue(abs(inTune.cents) < 3, "A4 at ${inTune.cents} cents")
        val flat = assertNotNull(session.samples[(2_250 / bucket).toInt()], "a note in the middle of D5")
        assertEquals(74, flat.midi)
        assertEquals(-25.0, flat.cents, 3.0, "D5 flat")
        // half the time in tune, half off
        assertEquals(50.0, session.metrics.scorePercent.toDouble(), 10.0, "in tune")
    }

    @Test
    fun `a file of silence has no notes`() = runTest {
        // as long as a take has to be: a shorter one would be too short before it is silent
        writeAacTones(path, RATE, listOf(Tone(hz = null, seconds = 3.0)))
        assertEquals(FileAnalysisResult.NoNotes, analyzer.analyze(PlatformFile(path), config, startedAtEpochMs = 0, audioFileName = "silent.m4a") {})
    }

    @Test
    fun `a file opened from its middle gives the rest of the sound`() {
        writeAacTones(path, RATE, listOf(Tone(hz = null, seconds = 2.0)))

        val whole = assertNotNull(IosPcmFileOpener.open(PlatformFile(path)))
        val rest = assertNotNull(IosPcmFileOpener.openAt(PlatformFile(path), fromSample = RATE.toLong()))
        try {
            assertEquals(whole.totalSamples, rest.totalSamples, "the length stays the whole file's")
            val all = count(whole)
            val tail = count(rest)
            assertTrue(tail in RATE / 2..RATE * 3 / 2, "from the middle: $tail of $all")
            // a reader that came to the end — of the file, or of the stretch asked for — gave up on nothing
            assertFalse(whole.broken, "the end of the file is no failure")
            assertFalse(rest.broken, "the end of a stretch is no failure")
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

    private companion object {
        const val RATE = 48_000
    }
}
