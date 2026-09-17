package com.example.violintuner.feature.session

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.ZoneClassifier
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionDetails
import com.example.violintuner.core.domain.session.StringFinger
import com.example.violintuner.core.domain.session.forSession
import kotlin.math.abs

/** Stored session → what the screen shows. Pure; the thresholds come from the config. */
object SessionContentMapper {
    fun contentOf(details: SessionDetails, defaultConfig: IntonationConfig): SessionContent {
        val summary = details.summary
        val config = defaultConfig.forSession(summary)
        val metrics = details.analysis.metrics
        val segments = details.analysis.segments.map { segment ->
            RollSegment(
                note = Note(segment.midi),
                startMs = segment.startMs,
                endMs = segment.endMs,
                meanCents = segment.meanCents,
                minCents = segment.minCents,
                maxCents = segment.maxCents,
                zone = ZoneClassifier.classify(segment.meanCents, config),
                contour = SessionAnalyzer.contour(details.samples, segment),
                position = StringFinger.of(segment.midi),
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
            hasAudio = summary.audioPath != null,
        )
    }

    /** Color of a score: green from "good", amber from "fair", red below (spec 3.10, 3.11). */
    fun scoreZone(percent: Int, config: IntonationConfig): Zone = when {
        percent >= config.scoreGoodPercent -> Zone.IN_TUNE
        percent >= config.scoreFairPercent -> Zone.NEAR
        else -> Zone.OFF
    }
}
