package com.example.violintuner.core.domain.session

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.ZoneClassifier
import kotlin.math.abs
import kotlin.math.roundToInt

/** One played note: a run of samples of the same note. Times are relative to the session start. */
data class NoteSegment(
    val midi: Int,
    /** Index of the first sample and the number of samples; the contour is that slice. */
    val firstSample: Int,
    val sampleCount: Int,
    val startMs: Long,
    val endMs: Long,
    val meanCents: Double,
    val minCents: Double,
    val maxCents: Double,
) {
    val durationMs: Long get() = endMs - startMs
}

/** A note that is off on average across the session. */
data class ProblemNote(val midi: Int, val meanCents: Double)

data class SessionMetrics(
    /** Share of in-tune samples, percent; with [nearPercent] and [offPercent] it sums to 100. */
    val scorePercent: Int,
    val nearPercent: Int,
    val offPercent: Int,
    /** Mean absolute deviation. */
    val maeCents: Double,
    /** Mean signed deviation: negative is flat. */
    val biasCents: Double,
    /** In-tune percent per string by the first-position heuristic; null where nothing was played. */
    val perString: Map<ViolinString, Int?>,
    val problemNotes: List<ProblemNote>,
)

data class SessionAnalysis(
    val segments: List<NoteSegment>,
    /** Null when the session has no note long enough to count. */
    val metrics: SessionMetrics?,
)

/** Spec 5.5. Stateless; the config carries the tolerance the session was recorded with. */
object SessionAnalyzer {
    fun analyze(samples: List<SessionSample?>, config: IntonationConfig): SessionAnalysis {
        val segments = segment(samples, config)
        if (segments.isEmpty()) return SessionAnalysis(segments, metrics = null)

        val cents = segments.flatMap { contour(samples, it) }
        val inTune = cents.count { ZoneClassifier.classify(it, config) == Zone.IN_TUNE }
        val near = cents.count { ZoneClassifier.classify(it, config) == Zone.NEAR }
        val scorePercent = percent(inTune, cents.size)
        val nearPercent = percent(near, cents.size).coerceAtMost(PERCENT - scorePercent)

        val perString = ViolinString.entries.associateWith { string ->
            val onString = segments.filter { StringFinger.of(it.midi).string == string }
                .flatMap { contour(samples, it) }
            if (onString.isEmpty()) {
                null
            } else {
                percent(onString.count { ZoneClassifier.classify(it, config) == Zone.IN_TUNE }, onString.size)
            }
        }
        val problems = segments.groupBy { it.midi }
            .map { (midi, ofNote) -> ProblemNote(midi, ofNote.map { it.meanCents }.average()) }
            .filter { abs(it.meanCents) > config.toleranceCents }
            .sortedByDescending { abs(it.meanCents) }
            .take(config.problemNotesMax)

        return SessionAnalysis(
            segments = segments,
            metrics = SessionMetrics(
                scorePercent = scorePercent,
                nearPercent = nearPercent,
                offPercent = PERCENT - scorePercent - nearPercent,
                maeCents = cents.sumOf { abs(it) } / cents.size,
                biasCents = cents.average(),
                perString = perString,
                problemNotes = problems,
            ),
        )
    }

    /** Zones of the first notes by their mean deviation: the mini bars of a history card. */
    fun previewZones(segments: List<NoteSegment>, config: IntonationConfig): List<Zone> =
        segments.take(config.sessionPreviewNotes).map { ZoneClassifier.classify(it.meanCents, config) }

    /** Deviation over time inside [segment]. */
    fun contour(samples: List<SessionSample?>, segment: NoteSegment): List<Double> =
        samples.subList(segment.firstSample, segment.firstSample + segment.sampleCount).map { it!!.cents }

    private fun segment(samples: List<SessionSample?>, config: IntonationConfig): List<NoteSegment> {
        val segments = ArrayList<NoteSegment>()
        var first = 0
        while (first < samples.size) {
            val midi = samples[first]?.midi
            var end = first + 1
            while (end < samples.size && samples[end]?.midi == midi) end++
            if (midi != null && (end - first) * config.sessionBucketMs >= config.minSegmentMs) {
                val cents = (first until end).map { samples[it]!!.cents }
                segments += NoteSegment(
                    midi = midi,
                    firstSample = first,
                    sampleCount = end - first,
                    startMs = first * config.sessionBucketMs,
                    endMs = end * config.sessionBucketMs,
                    meanCents = cents.average(),
                    minCents = cents.min(),
                    maxCents = cents.max(),
                )
            }
            first = end
        }
        return segments
    }

    private fun percent(part: Int, whole: Int): Int = (part * PERCENT.toDouble() / whole).roundToInt()

    private const val PERCENT = 100
}
