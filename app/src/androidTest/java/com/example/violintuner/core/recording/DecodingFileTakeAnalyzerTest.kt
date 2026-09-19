package com.example.violintuner.core.recording

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.audio.dsp.MpmDetector
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.testing.TestVideo
import java.io.File
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The sound track of a real video through a real decoder into a session. */
@RunWith(AndroidJUnit4::class)
class DecodingFileTakeAnalyzerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.cacheDir, "video-analysis-test").apply { mkdirs() }
    private val config = IntonationConfig()
    private val analyzer = DecodingFileTakeAnalyzer({ MpmDetector(it) }, RepertoireConfig(), Dispatchers.Default)

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun hz(midi: Int, cents: Double = 0.0) = 440.0 * 2.0.pow((midi - 69 + cents / 100) / 12)

    @Test
    fun aVideoIsHeardLikeARecording() = runBlocking {
        // A4 in tune for three seconds, a second of silence, D5 twenty-five cents flat for three
        val file = TestVideo.make(File(directory, "take.mp4"), seconds = 7) { t ->
            when {
                t < 3 -> hz(69)
                t < 4 -> null
                else -> hz(74, cents = -25.0)
            }
        }
        var last = 0f
        var steps = 0
        val started = System.nanoTime()
        val result = analyzer.analyze(file, config, startedAtEpochMs = 5_000, audioFileName = "take.mp4") {
            assertTrue(it.fraction >= last)
            last = it.fraction
            steps++
        }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        Log.i("FileTakeAnalysisSpeed", "7 s of video analysed in $tookMs ms: ${7_000f / tookMs}x")

        val session = (result as FileAnalysisResult.Recorded).session
        assertEquals(7_000.0, session.durationMs.toDouble(), 150.0)
        assertEquals("take.mp4", session.audioPath)
        assertTrue(steps > 20)
        val bucket = config.sessionBucketMs
        assertEquals(69, session.samples[(1_500 / bucket).toInt()]?.midi)
        assertEquals(null, session.samples[(3_600 / bucket).toInt()])
        val flat = session.samples[(5_500 / bucket).toInt()]!!
        assertEquals(74, flat.midi)
        assertEquals(-25.0, flat.cents, 3.0)
        // half the notes in tune, half off
        assertEquals(50.0, session.metrics.scorePercent.toDouble(), 8.0)
        assertTrue("faster than the sound itself", tookMs < 7_000)
    }

    @Test
    fun aMuteVideoCannotBeOpened() = runBlocking {
        val file = TestVideo.make(File(directory, "mute.mp4"), seconds = 3, withSound = false)
        assertEquals(FileAnalysisResult.CannotOpen, analyzer.analyze(file, config, 0, "mute.mp4") {})
    }

    @Test
    fun aVideoWithoutNotesIsNotATake() = runBlocking {
        val file = TestVideo.make(File(directory, "silent.mp4"), seconds = 3) { null }
        assertEquals(FileAnalysisResult.NoNotes, analyzer.analyze(file, config, 0, "silent.mp4") {})
    }

    @Test
    fun whatIsNotAVideoCannotBeOpened() = runBlocking {
        val file = File(directory, "junk.mp4").apply { writeBytes(ByteArray(4_096) { it.toByte() }) }
        assertEquals(FileAnalysisResult.CannotOpen, analyzer.analyze(file, config, 0, "junk.mp4") {})
    }
}
