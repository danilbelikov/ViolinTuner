package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier

data class RecordingProgress(val elapsedMs: Long, val ribbon: RecordingRibbon)

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

    // The mini ribbon: runs of one note in one zone among the closed buckets, so a long note that drifts shows the
    // drift. Pauses take no room on it. The closed runs lie in [closed] and are never changed in place: a ribbon handed
    // out is a view of the array it was made from, so a closed bucket costs one piece at most, and joining neighbours on
    // a long take ([join]) makes a new array.
    private var closed = arrayOfNulls<RecordingBar>(INITIAL_PIECES)
    private var closedCount = 0
    private var openMidi = NO_NOTE
    private var openZone = Zone.IN_TUNE
    private var openCount = 0
    private var readBuckets = 0
    private var ribbon = RecordingRibbon.EMPTY
    private var ribbonDirty = false

    val limitReached: Boolean
        get() = sampler.durationMs >= config.maxSessionMs

    fun add(tMs: Long, reading: IntonationReading) {
        sampler.add(tMs, reading)
        val samples = sampler.closedSamples
        while (readBuckets < samples.size) {
            val sample = samples[readBuckets++]
            if (sample == null) {
                closeRun()
                continue
            }
            val zone = ZoneClassifier.classify(sample.cents, config)
            if (openCount > 0 && (sample.midi != openMidi || zone != openZone)) closeRun()
            if (openCount == 0) {
                openMidi = sample.midi
                openZone = zone
            }
            openCount++
            ribbonDirty = true
        }
    }

    fun progress(): RecordingProgress {
        if (ribbonDirty) {
            val open = if (openCount > 0) RecordingBar(openCount, openZone) else null
            ribbon = RecordingRibbon(PieceView(closed, closedCount, open), spanBuckets().toFloat())
            ribbonDirty = false
        }
        return RecordingProgress(sampler.durationMs, ribbon)
    }

    /** The full width of the ribbon in buckets: the take so far, but never less than [IntonationConfig.recordingBarMinMs]. */
    private fun spanBuckets(): Long = maxOf(sampler.durationMs, config.recordingBarMinMs) / config.sessionBucketMs

    private fun closeRun() {
        if (openCount == 0) return
        if (closedCount == closed.size) closed = closed.copyOf(closed.size * 2)
        closed[closedCount++] = RecordingBar(openCount, openZone)
        openCount = 0
        openMidi = NO_NOTE
        if (closedCount > config.recordingBarMaxPieces) join()
    }

    /**
     * A long take has more runs than the ribbon has pixels: neighbours are joined while together they are at most
     * [JOIN_WIDTH] of the cap's share of the width — narrower than a pixel of any ribbon up to half the cap in pixels —
     * and the joined piece takes the zone with more buckets in it, the worse one when they are even. Runs wider than
     * that stay whole, so a note one can see keeps its own piece and colour. Should that leave the ribbon still near the
     * cap, the next pass joins twice as wide, so a join happens only every quarter of the cap of new runs.
     */
    private fun join() {
        var limit = JOIN_WIDTH * spanBuckets() / config.recordingBarMaxPieces
        while (true) {
            val joined = arrayOfNulls<RecordingBar>(closed.size)
            var count = 0
            var groupStart = 0
            var groupBuckets = 0L
            for (i in 0 until closedCount) {
                val piece = closed[i]!!
                if (i > groupStart && groupBuckets + piece.buckets > limit) {
                    joined[count++] = joinedOf(groupStart, i)
                    groupStart = i
                    groupBuckets = 0
                }
                groupBuckets += piece.buckets
            }
            joined[count++] = joinedOf(groupStart, closedCount)
            closed = joined
            closedCount = count
            if (closedCount <= config.recordingBarMaxPieces * JOINED_SHARE) break
            limit *= 2
        }
        ribbonDirty = true
    }

    private fun joinedOf(from: Int, until: Int): RecordingBar {
        if (until - from == 1) return closed[from]!!
        val byZone = IntArray(ZONES.size)
        for (i in from until until) closed[i]!!.let { byZone[it.zone.ordinal] += it.buckets }
        // Zone runs from in tune to off: on a tie the later one, the worse, is kept — a join never hides a miss
        var zone = ZONES[0]
        for (candidate in ZONES) if (byZone[candidate.ordinal] >= byZone[zone.ordinal]) zone = candidate
        return RecordingBar(byZone.sum(), zone)
    }

    /** The closed pieces a ribbon was made from, as they were then, and the run still being played. */
    private class PieceView(
        private val closed: Array<RecordingBar?>,
        private val closedCount: Int,
        private val open: RecordingBar?,
    ) : AbstractList<RecordingBar>() {
        override val size: Int = closedCount + if (open != null) 1 else 0

        override fun get(index: Int): RecordingBar = when {
            index in 0 until closedCount -> closed[index]!!
            index == closedCount && open != null -> open
            else -> throw IndexOutOfBoundsException("piece $index of $size")
        }
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

    private companion object {
        const val NO_NOTE = -1
        const val INITIAL_PIECES = 64

        /** Joined neighbours are at most this many shares of 1 / cap of the width. */
        const val JOIN_WIDTH = 2

        /** A join is done again, twice as wide, until the ribbon holds at most this share of the cap. */
        const val JOINED_SHARE = 0.75
        val ZONES = Zone.entries
    }
}
