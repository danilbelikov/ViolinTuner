package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier

/** One note on the mini bar of the recording strip: its share of the bar and its zone. */
data class RecordingBar(val fraction: Float, val zone: Zone)

data class RecordingProgress(val elapsedMs: Long, val bars: List<RecordingBar>)

sealed interface RecordingResult {
    /** Shorter than [IntonationConfig.minSessionMs]: dropped without a word (spec 3.9). */
    data object TooShort : RecordingResult

    /** Long enough, but without a single countable note. */
    data object NoNotes : RecordingResult

    data class Recorded(val session: NewSession) : RecordingResult
}

/**
 * One recording (spec 3.9): collects engine readings, reports progress for the strip on Live and
 * turns into a [NewSession] at the end. Pure and single-threaded, like the engine it sits next to.
 */
class SessionRecorder(
    private val config: IntonationConfig,
    private val startedAtEpochMs: Long,
) {
    private val sampler = SessionSampler(config)

    // Mini bar: runs of one note in one zone among the closed buckets, so a long note that
    // drifts shows the drift. Pauses take no room on it.
    private class Run(val midi: Int, val zone: Zone, var count: Int = 0)

    private val runs = ArrayList<Run>()
    private var openRun: Run? = null
    private var readBuckets = 0
    private var cachedBars: List<RecordingBar> = emptyList()
    private var barsDirty = false

    val limitReached: Boolean
        get() = sampler.durationMs >= config.maxSessionMs

    fun add(tMs: Long, reading: IntonationReading) {
        sampler.add(tMs, reading)
        val closed = sampler.closedSamples
        while (readBuckets < closed.size) {
            val sample = closed[readBuckets++]
            if (sample == null) {
                openRun = null
                continue
            }
            val zone = ZoneClassifier.classify(sample.cents, config)
            val run = openRun?.takeIf { it.midi == sample.midi && it.zone == zone } ?: Run(sample.midi, zone).also {
                runs += it
                openRun = it
            }
            run.count++
            barsDirty = true
        }
    }

    fun progress(): RecordingProgress {
        if (barsDirty) {
            val barBuckets = maxOf(sampler.durationMs, config.recordingBarMinMs) / config.sessionBucketMs
            cachedBars = runs.map { RecordingBar(fraction = it.count.toFloat() / barBuckets, zone = it.zone) }
            barsDirty = false
        }
        return RecordingProgress(sampler.durationMs, cachedBars)
    }

    /** [audioFileName] is the finished take of the same stream, or null when there is no sound. */
    fun finish(audioFileName: String? = null): RecordingResult {
        if (sampler.durationMs < config.minSessionMs) return RecordingResult.TooShort
        val samples = sampler.snapshot()
        val analysis = SessionAnalyzer.analyze(samples, config)
        val metrics = analysis.metrics ?: return RecordingResult.NoNotes
        return RecordingResult.Recorded(
            NewSession(
                startedAtEpochMs = startedAtEpochMs,
                durationMs = sampler.durationMs,
                config = config,
                samples = samples,
                metrics = metrics,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config),
                audioPath = audioFileName,
            ),
        )
    }
}
