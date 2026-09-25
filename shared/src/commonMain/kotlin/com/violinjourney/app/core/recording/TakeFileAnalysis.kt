package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.FrameAnalyzer
import com.violinjourney.app.core.audio.dsp.PitchDetector
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.RecordingResult
import com.violinjourney.app.core.domain.session.SessionRecorder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Mono PCM16 of a recorded file, handed out in pieces of any size. */
interface PcmSource {
    val sampleRate: Int
    val totalSamples: Long

    /** Fills [out] from its start with up to its size of samples; how many, or [END] once there are no more. */
    fun read(out: ShortArray): Int

    companion object {
        const val END = -1
    }
}

/** How far the analysis of a file has come: the share done and the notes found so far, as shares of the whole file. */
data class FileAnalysisProgress(val fraction: Float, val bars: List<RecordingBar>)

sealed interface FileAnalysisResult {
    data class Recorded(val session: NewSession) : FileAnalysisResult

    /** Long enough, but without a single countable note (spec 5.5). */
    data object NoNotes : FileAnalysisResult

    /** Shorter than the shortest session there is. */
    data object TooShort : FileAnalysisResult

    /** Longer than the longest session there is (spec 5.13). */
    data object TooLong : FileAnalysisResult

    /** The sound is sampled at a rate the detector is not tuned for. */
    data object UnsupportedRate : FileAnalysisResult

    /** No sound track, or nothing this device can decode. */
    data object CannotOpen : FileAnalysisResult
}

/**
 * The sound of a file through the very chain that listens to the microphone (spec 3.19, 5.13):
 * hops of [IntonationConfig.hopSizeSamples] → [FrameAnalyzer] → [IntonationEngine] →
 * [SessionRecorder]. Time is the sample clock, there is no other clock on this path — so it runs
 * as fast as the processor lets it and gives what the microphone would have given on this sound.
 * Pure Kotlin. Cancellable between hops.
 */
object TakeFileAnalysis {
    /** [onProgress] is called about [PROGRESS_STEPS] times over the file, from the calling coroutine. */
    suspend fun run(
        source: PcmSource,
        config: IntonationConfig,
        detector: PitchDetector,
        startedAtEpochMs: Long,
        supportedRatesHz: Set<Int>,
        audioFileName: String? = null,
        onProgress: (FileAnalysisProgress) -> Unit = {},
    ): FileAnalysisResult {
        if (source.sampleRate !in supportedRatesHz) return FileAnalysisResult.UnsupportedRate
        val totalMs = source.totalSamples * MS_PER_SECOND / source.sampleRate
        if (totalMs > config.maxSessionMs) return FileAnalysisResult.TooLong
        if (totalMs < config.minSessionMs) return FileAnalysisResult.TooShort

        val analyzer = FrameAnalyzer(detector, config, source.sampleRate)
        val engine = IntonationEngine(config)
        val recorder = SessionRecorder(config, startedAtEpochMs)
        // The recorder counts time from its first reading: this one pins its zero to the zero of
        // the file, so the notes line up with the sound — and the picture — they were found in.
        recorder.add(0, IntonationReading.Silence)

        val hop = ShortArray(config.hopSizeSamples)
        val piece = ShortArray(config.hopSizeSamples)
        var filled = 0
        var seen = 0L
        val progressEvery = (source.totalSamples / PROGRESS_STEPS).coerceAtLeast(hop.size.toLong())
        var nextProgress = progressEvery
        while (true) {
            coroutineContext.ensureActive()
            val read = source.read(piece)
            if (read == PcmSource.END) break
            // A decoder hands out what it has; the analyzer must see the same whole hops the microphone gives it.
            var taken = 0
            while (taken < read) {
                val count = minOf(hop.size - filled, read - taken)
                piece.copyInto(hop, destinationOffset = filled, startIndex = taken, endIndex = taken + count)
                filled += count
                taken += count
                if (filled == hop.size) {
                    analyzer.push(hop)?.let { frame -> recorder.add(frame.tMs, engine.process(frame, TargetMode.Chromatic)) }
                    filled = 0
                }
            }
            seen += read
            if (seen >= nextProgress) {
                nextProgress += progressEvery
                onProgress(progressOf(recorder, seen, source.totalSamples, totalMs, config))
            }
        }
        // The tail shorter than a hop is dropped, as the microphone loop drops a read it did not finish.
        return when (val result = recorder.finish(audioFileName)) {
            is RecordingResult.Recorded -> FileAnalysisResult.Recorded(result.session)
            RecordingResult.NoNotes -> FileAnalysisResult.NoNotes
            RecordingResult.TooShort -> FileAnalysisResult.TooShort
        }
    }

    // The recorder measures its bars against the time recorded so far (the strip on Live grows with the take);
    // here the whole is known in advance, so the bars are re-measured against it and the strip fills up like a progress bar.
    private fun progressOf(recorder: SessionRecorder, seen: Long, total: Long, totalMs: Long, config: IntonationConfig): FileAnalysisProgress {
        val progress = recorder.progress()
        val measuredAgainstMs = maxOf(progress.elapsedMs, config.recordingBarMinMs)
        val scale = measuredAgainstMs.toFloat() / totalMs.coerceAtLeast(1)
        return FileAnalysisProgress(
            fraction = (seen.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f),
            bars = progress.bars.map { it.copy(fraction = it.fraction * scale) },
        )
    }

    private const val MS_PER_SECOND = 1_000L
    private const val PROGRESS_STEPS = 200
}
