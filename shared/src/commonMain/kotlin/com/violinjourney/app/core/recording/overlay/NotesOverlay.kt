package com.violinjourney.app.core.recording.overlay

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionRibbon
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.session.forSession
import kotlin.math.abs
import kotlin.math.roundToInt

/** One note of the lane: a segment of the analysis (spec 5.5), in the colour of the zone of its mean (spec 3.37). */
data class OverlayNote(val midi: Int, val startMs: Long, val endMs: Long, val zone: Zone) {
    /** «F#5»: Latin, in every language (spec 3.6). */
    val name: String get() = Note(midi).name
}

/** «Что уходит» of the summary: the first problem note of the analysis (spec 5.5) with its mean. */
data class OverlayDrift(val midi: Int, val meanCents: Double, val zone: Zone) {
    val name: String get() = Note(midi).name
}

/** «Прошлый дубль»: the score of the previous take with the same tolerance, and by how much this one is better — null when not. */
data class OverlayPrevious(val scorePercent: Int, val gainPercent: Int?)

/** The tag over the playhead: which note it names and how much of it shows. */
data class OverlayTag(val note: OverlayNote, val alpha: Float)

/**
 * What is drawn over one recording with a video (spec 3.37, 5.30): made once from its stored analysis, read at every frame.
 * The notes are in time order and do not overlap — a recording is one voice.
 */
class NotesOverlay(
    val notes: List<OverlayNote>,
    /** The heights of the lane: fractional, since a span widened to its minimum is shared evenly up and down. */
    val lowMidi: Float,
    val highMidi: Float,
    val scorePercent: Int,
    val toleranceCents: Int,
    /** «Менуэт соль мажор · 3 октября» — the title of the shared file. */
    val title: String,
    val bestMidi: Int?,
    /** Null — no note drifts: «ничего». */
    val drift: OverlayDrift?,
    val previous: OverlayPrevious?,
    val ribbon: List<RecordingBar>,
    val config: NotesVideoConfig,
) {
    /** Where each note's run of notes began: one with breaks shorter than [NotesVideoConfig.holdMs] between them. */
    private val phraseStartMs: LongArray = LongArray(notes.size).also { starts ->
        notes.forEachIndexed { index, note ->
            val joined = index > 0 && note.startMs - notes[index - 1].endMs < config.holdMs
            starts[index] = if (joined) starts[index - 1] else note.startMs
        }
    }

    /**
     * The note under the playhead at [tMs] — or, in a break, the one just played while it ended less than
     * [NotesVideoConfig.holdMs] ago: a change of bow leaves the lane as it was (spec 5.30). Null in a longer pause.
     */
    fun currentAt(tMs: Long): OverlayNote? {
        val index = lastStartedAt(tMs)
        if (index < 0) return null
        val note = notes[index]
        return note.takeIf { tMs < note.endMs + config.holdMs }
    }

    /** The tag at [tMs]: the current note, coming in over [NotesVideoConfig.tagFadeMs] from the start of its run and going out as long after it. */
    fun tagAt(tMs: Long): OverlayTag? {
        val index = lastStartedAt(tMs)
        if (index < 0) return null
        val note = notes[index]
        val shownUntil = note.endMs + config.holdMs
        val fade = config.tagFadeMs.toFloat()
        val coming = ((minOf(tMs, shownUntil) - phraseStartMs[index]) / fade).coerceIn(0f, 1f)
        val going = if (tMs < shownUntil) 1f else 1f - (tMs - shownUntil) / fade
        return if (going <= 0f) null else OverlayTag(note, coming * going)
    }

    /** The notes that touch the span from [fromMs] to [toMs], in time order. */
    fun notesBetween(fromMs: Long, toMs: Long): List<OverlayNote> {
        var low = 0
        var high = notes.size
        // the first note that ends after the span begins: ends grow with starts, the notes never overlapping
        while (low < high) {
            val middle = (low + high) / 2
            if (notes[middle].endMs <= fromMs) low = middle + 1 else high = middle
        }
        val shown = ArrayList<OverlayNote>()
        var index = low
        while (index < notes.size && notes[index].startMs < toMs) shown += notes[index++]
        return shown
    }

    /** The last note begun by [tMs], or −1. */
    private fun lastStartedAt(tMs: Long): Int {
        var low = 0
        var high = notes.size
        while (low < high) {
            val middle = (low + high) / 2
            if (notes[middle].startMs <= tMs) low = middle + 1 else high = middle
        }
        return low - 1
    }
}

/** Makes the overlay of a recording out of what it already keeps: no sound is analysed again (spec 3.37). */
object NotesOverlays {
    /**
     * [recordings] — every stored recording, for «Прошлый дубль»; [intonation] — the config of now, which [details] carries
     * the tolerance of its own recording into, as the screen of the recording does.
     */
    fun of(
        details: SessionDetails,
        recordings: List<SessionSummary>,
        intonation: IntonationConfig,
        title: String,
        config: NotesVideoConfig,
    ): NotesOverlay {
        val summary = details.summary
        val own = intonation.forSession(summary)
        val segments = details.analysis.segments
        val notes = segments.map { OverlayNote(it.midi, it.startMs, it.endMs, ZoneClassifier.classify(it.meanCents, own)) }
        val (low, high) = heights(notes.map { it.midi }, config)
        return NotesOverlay(
            notes = notes,
            lowMidi = low,
            highMidi = high,
            scorePercent = summary.scorePercent,
            toleranceCents = summary.toleranceCents.roundToInt(),
            title = title,
            bestMidi = bestNote(details, own, config),
            drift = details.analysis.metrics?.problemNotes?.firstOrNull()?.let { OverlayDrift(it.midi, it.meanCents, ZoneClassifier.classify(it.meanCents, own)) },
            previous = previousTake(summary, recordings),
            ribbon = SessionRibbon.of(details.samples, own),
            config = config,
        )
    }

    /** The notes played and a semitone of air, never fewer than [NotesVideoConfig.minPitchSpan] semitones (spec 5.30). */
    fun heights(midis: List<Int>, config: NotesVideoConfig): Pair<Float, Float> {
        if (midis.isEmpty()) return 0f to config.minPitchSpan.toFloat()
        val low = (midis.min() - config.pitchPadSemitones).toFloat()
        val high = (midis.max() + config.pitchPadSemitones).toFloat()
        if (high - low >= config.minPitchSpan) return low to high
        val middle = (low + high) / 2
        return middle - config.minPitchSpan / 2f to middle + config.minPitchSpan / 2f
    }

    /**
     * «Лучшая нота» (spec 5.30): of the notes that sounded [NotesVideoConfig.bestNoteMinMs] in all and were in tune at least
     * [NotesVideoConfig.bestNoteMinShare] of their samples — the one in tune the most; a tie goes to the longer, then the lower.
     */
    internal fun bestNote(details: SessionDetails, own: IntonationConfig, config: NotesVideoConfig): Int? {
        class Tally(var durationMs: Long = 0, var inTune: Int = 0, var samples: Int = 0)
        val tallies = HashMap<Int, Tally>()
        for (segment in details.analysis.segments) {
            val tally = tallies.getOrPut(segment.midi) { Tally() }
            tally.durationMs += segment.durationMs
            SessionAnalyzer.contour(details.samples, segment).forEach { cents ->
                tally.samples++
                if (abs(cents) <= own.toleranceCents) tally.inTune++
            }
        }
        return tallies.entries
            .filter { (_, tally) -> tally.durationMs >= config.bestNoteMinMs && tally.samples > 0 && share(tally.inTune, tally.samples) >= config.bestNoteMinShare }
            .maxWithOrNull(
                compareBy<Map.Entry<Int, Tally>> { (_, tally) -> share(tally.inTune, tally.samples) }
                    .thenBy { (_, tally) -> tally.durationMs }
                    .thenByDescending { (midi, _) -> midi },
            )?.key
    }

    /**
     * «Прошлый дубль» (spec 5.30): of the takes of the same piece begun before this recording with the same tolerance, the
     * latest — any kind; the gain is said only when this one is better. A recording without a piece has none.
     */
    internal fun previousTake(summary: SessionSummary, recordings: List<SessionSummary>): OverlayPrevious? {
        val piece = summary.pieceId ?: return null
        val previous = recordings
            .filter { it.id != summary.id && it.pieceId == piece && it.startedAtEpochMs < summary.startedAtEpochMs && it.toleranceCents == summary.toleranceCents }
            .maxByOrNull { it.startedAtEpochMs }
            ?: return null
        return OverlayPrevious(previous.scorePercent, (summary.scorePercent - previous.scorePercent).takeIf { it > 0 })
    }

    private fun share(part: Int, whole: Int): Double = part.toDouble() / whole
}
