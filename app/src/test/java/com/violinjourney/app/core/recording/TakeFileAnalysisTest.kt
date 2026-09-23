package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.FrameAnalyzer
import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.SignalSynth
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingResult
import com.violinjourney.app.core.domain.session.SessionRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeFileAnalysisTest {
    private val config = IntonationConfig()
    private val rate = 48_000
    private val rates = setOf(44_100, 48_000)

    /** Hands the samples out in pieces of [piece] — a decoder gives what it has, not hops. */
    private class ArraySource(private val pcm: ShortArray, override val sampleRate: Int, private val piece: Int) : PcmSource {
        private var at = 0
        override val totalSamples: Long get() = pcm.size.toLong()
        override fun read(out: ShortArray): Int {
            if (at >= pcm.size) return PcmSource.END
            val count = minOf(piece, out.size, pcm.size - at)
            pcm.copyInto(out, 0, at, at + count)
            at += count
            return count
        }
    }

    private fun seconds(s: Double) = (rate * s).toInt()

    /** A4 in tune, a pause, then C#5 thirty cents sharp. */
    private fun music(): ShortArray = SignalSynth.toPcm16(
        SignalSynth.tone(SignalSynth.hz(69), rate, seconds(1.5)) +
            FloatArray(seconds(0.5)) +
            SignalSynth.tone(SignalSynth.hz(73, cents = 30.0), rate, seconds(1.5)),
    )

    private suspend fun analyse(pcm: ShortArray, piece: Int = 512, sampleRate: Int = rate, onProgress: (FileAnalysisProgress) -> Unit = {}) =
        TakeFileAnalysis.run(ArraySource(pcm, sampleRate, piece), config, MpmDetector(config), startedAtEpochMs = 1_000, supportedRatesHz = rates, audioFileName = "take.mp4", onProgress = onProgress)

    @Test
    fun `a file gives the session the microphone would have given on the same sound`() = runTest {
        val pcm = music()
        // the way of the microphone: whole hops straight into the analyzer
        val analyzer = FrameAnalyzer(MpmDetector(config), config, rate)
        val engine = IntonationEngine(config)
        val recorder = SessionRecorder(config, 1_000)
        recorder.add(0, IntonationReading.Silence)
        pcm.toList().chunked(config.hopSizeSamples).filter { it.size == config.hopSizeSamples }.forEach { hop ->
            analyzer.push(hop.toShortArray())?.let { recorder.add(it.tMs, engine.process(it, TargetMode.Chromatic)) }
        }
        val live = (recorder.finish("take.mp4") as RecordingResult.Recorded).session

        // the way of a file: pieces of an awkward size
        val fromFile = (analyse(pcm, piece = 300) as FileAnalysisResult.Recorded).session
        assertEquals(live.samples, fromFile.samples)
        assertEquals(live.metrics, fromFile.metrics)
        assertEquals(live.durationMs, fromFile.durationMs)
    }

    @Test
    fun `the notes stand where they sound in the file`() = runTest {
        val session = (analyse(music()) as FileAnalysisResult.Recorded).session
        val bucket = config.sessionBucketMs
        assertEquals(69, session.samples[(800 / bucket).toInt()]?.midi)
        assertEquals(null, session.samples[(1_800 / bucket).toInt()])
        val sharp = session.samples[(2_800 / bucket).toInt()]!!
        assertEquals(73, sharp.midi)
        assertEquals(30.0, sharp.cents, 2.0)
        assertEquals("take.mp4", session.audioPath)
        assertEquals(1_000L, session.startedAtEpochMs)
        assertEquals(3_500.0, session.durationMs.toDouble(), 30.0)
    }

    @Test
    fun `progress only grows and its strip fills up with the file`() = runTest {
        val seen = mutableListOf<FileAnalysisProgress>()
        analyse(music()) { seen += it }
        assertTrue(seen.size > 50)
        assertTrue(seen.zipWithNext().all { (a, b) -> b.fraction >= a.fraction })
        assertEquals(1f, seen.last().fraction, 0.02f)
        val bars = seen.last().bars
        assertEquals(listOf(Zone.IN_TUNE, Zone.OFF), bars.map { it.zone })
        // three seconds of notes out of three and a half: pauses take no room on the strip
        assertEquals(3_000f / 3_500f, bars.sumOf { it.fraction.toDouble() }.toFloat(), 0.06f)
    }

    @Test
    fun `too short, too long and a strange rate are told apart before any listening`() = runTest {
        assertEquals(FileAnalysisResult.TooShort, analyse(ShortArray(seconds(1.0))))
        assertEquals(FileAnalysisResult.UnsupportedRate, analyse(ShortArray(16_000 * 5), sampleRate = 16_000))
        val hour = object : PcmSource {
            override val sampleRate = rate
            override val totalSamples = rate * 3_601L
            override fun read(out: ShortArray): Int = error("not read")
        }
        assertEquals(FileAnalysisResult.TooLong, TakeFileAnalysis.run(hour, config, MpmDetector(config), 0, rates))
    }

    @Test
    fun `silence is a file without notes`() = runTest {
        assertEquals(FileAnalysisResult.NoNotes, analyse(ShortArray(seconds(3.0))))
    }

    @Test
    fun `the analysis can be cancelled half way`() = runTest {
        var job: Job? = null
        var progressed = 0
        job = launch {
            analyse(music()) {
                progressed++
                if (progressed == 20) job!!.cancel()
            }
            error("must not finish")
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(progressed in 20..22)
    }
}
