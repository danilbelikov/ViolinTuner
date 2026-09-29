package com.violinjourney.app.feature.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.StringFinger
import com.violinjourney.app.core.domain.session.forSession
import kotlin.math.abs
import kotlin.math.roundToInt

/** Stored session → what the screen shows. Pure; the thresholds come from the config. */
object SessionContentMapper {
    /** [soundFound]: the file of its sound is there to be played — otherwise it is a recording without sound (spec 3.17). */
    fun contentOf(details: SessionDetails, defaultConfig: IntonationConfig, soundFound: Boolean = details.summary.audioPath != null): SessionContent {
        val summary = details.summary
        val config = defaultConfig.forSession(summary)
        val metrics = details.analysis.metrics
        // how many times each note sounded: «3 раза за запись» of its sheet
        val sounded = details.analysis.segments.groupingBy { it.midi }.eachCount()
        val segments = details.analysis.segments.map { segment ->
            RollSegment(
                note = Note(segment.midi),
                startMs = segment.startMs,
                endMs = segment.endMs,
                meanCents = segment.meanCents,
                minCents = segment.minCents,
                maxCents = segment.maxCents,
                zone = ZoneClassifier.classify(segment.meanCents, config),
                steady = (segment.maxCents - segment.minCents) / 2 <= config.nearCents,
                contour = SessionAnalyzer.contour(details.samples, segment),
                position = StringFinger.of(segment.midi),
                sameNoteCount = sounded.getValue(segment.midi),
            )
        }
        return SessionContent(
            title = summary.title,
            startedAtEpochMs = summary.startedAtEpochMs,
            durationMs = summary.durationMs,
            toleranceCents = summary.toleranceCents,
            scorePercent = summary.scorePercent,
            nearPercent = summary.nearPercent,
            offPercent = summary.offPercent,
            maeCents = summary.maeCents,
            biasCents = summary.biasCents,
            biasZone = if (abs(summary.biasCents) < config.biasNeutralCents) {
                null
            } else {
                ZoneClassifier.classify(summary.biasCents, config)
            },
            perString = metrics?.perString.orEmpty().mapValues { (_, percent) ->
                percent?.let { it to scoreZone(it, config) }
            },
            problemNotes = metrics?.problemNotes.orEmpty().map {
                ProblemNoteUi(
                    note = Note(it.midi),
                    string = StringFinger.of(it.midi).string,
                    meanCents = it.meanCents,
                    zone = ZoneClassifier.classify(it.meanCents, config),
                )
            },
            rollNotes = segments.map { it.note.midi }.distinct().sortedDescending().map(::Note),
            segments = segments,
            hasAudio = soundFound,
            hasVideo = summary.videoPath != null,
            nearCents = config.nearCents.roundToInt(),
        )
    }

    /**
     * Where [note] drifts the most — the sheet a row of «Что уходит» opens (spec 3.36.5): the index of its segment with the largest
     * |mean|; of equal ones the longest, then the first. Null when the note has no segment.
     */
    fun worstSegmentOf(note: Note, segments: List<RollSegment>): Int? = segments.indices
        .filter { segments[it].note == note }
        .maxWithOrNull(
            compareBy<Int> { abs(segments[it].meanCents) }
                .thenBy { segments[it].endMs - segments[it].startMs }
                .thenByDescending { it },
        )

    /**
     * «±N ц» of the tile «размах» of the sheet of a note (spec 3.36.5): half the swing of [segment], rounded — but never the border of
     * «рядом» ([nearCents]) or less for a note the advice calls wandering ([RollSegment.steady] false: over the border by less than
     * half a cent, 20.3 → 21), so that «Размах больше 20 ц» beside it is not answered by «±20 ц». Which advice — the rules of 3.10, as
     * they are.
     */
    fun halfRangeShown(segment: RollSegment, nearCents: Int): Int {
        val rounded = ((segment.maxCents - segment.minCents) / 2).roundToInt()
        return if (segment.steady) rounded else maxOf(rounded, nearCents + 1)
    }

    /** Color of a score: green from "good", amber from "fair", red below (spec 3.10, 3.11). */
    fun scoreZone(percent: Int, config: IntonationConfig): Zone = when {
        percent >= config.scoreGoodPercent -> Zone.IN_TUNE
        percent >= config.scoreFairPercent -> Zone.NEAR
        else -> Zone.OFF
    }
}
