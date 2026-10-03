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
import com.violinjourney.app.core.ui.format.CentsFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One note of the lane: a segment of the analysis (spec 5.5), in the colour of the zone of its mean (spec 3.37). The tag says
 * [meanCents] beside the name (since 0.90), in whole cents; the zone is of that whole number, so the two never disagree.
 */
data class OverlayNote(val midi: Int, val startMs: Long, val endMs: Long, val zone: Zone, val meanCents: Double) {
    /** «F#5»: Latin, in every language (spec 3.6). */
    val name: String get() = Note(midi).name

    /** The shape the colour of the tag is backed by (spec 2, principle 5): the dot in tune, else the arrow up for high, down for low. */
    val sign: OverlaySign
        get() = when {
            zone == Zone.IN_TUNE -> OverlaySign.DOT
            meanCents > 0 -> OverlaySign.UP
            else -> OverlaySign.DOWN
        }

    /** The number of the tag: «+12», «−7», «0» — whole cents, signed, without «ц» (spec 5.30). */
    val centsText: String get() = CentsFormat.signed(meanCents)
}

/** The sign beside the name in the tag (since 0.90), as in the line of the status of Live. */
enum class OverlaySign { UP, DOWN, DOT }

/** «Что уходит» of the summary: the first problem note of the analysis (spec 5.5) with its mean. */
data class OverlayDrift(val midi: Int, val meanCents: Double, val zone: Zone) {
    val name: String get() = Note(midi).name
}

/** «Прошлый дубль»: the score of the previous take with the same tolerance, and by how much this one is better — null when not. */
data class OverlayPrevious(val scorePercent: Int, val gainPercent: Int?)

/** The tag over the playhead: which note it names and how much of it shows. */
data class OverlayTag(val note: OverlayNote, val alpha: Float)

/** The opening title at a moment (spec 5.30, since 0.90): how much of it shows, and how far above its place it still is, in u. */
data class OverlayOpening(val alpha: Float, val raiseU: Float)

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
    /** «Менуэт соль мажор · 3 октября» — the title of the shared file: the summary says it. */
    val title: String,
    /** «Менуэт соль мажор» and «3 октября» — the same title in two lines, for the opening (since 0.90). */
    val heading: String,
    val date: String,
    val bestMidi: Int?,
    /** Null — no note drifts: «ничего». */
    val drift: OverlayDrift?,
    val previous: OverlayPrevious?,
    val ribbon: List<RecordingBar>,
    val config: NotesVideoConfig,
) {
    /** Where each note's run of notes began: one with breaks no longer than [NotesVideoConfig.holdMs] between them. */
    private val phraseStartMs = LongArray(notes.size)

    /**
     * How bright the tag already was when each run began: a run that starts while the tag of the one before still fades goes on
     * from there — the tag never drops to nothing between two notes.
     */
    private val phraseCarry = FloatArray(notes.size)

    init {
        notes.forEachIndexed { index, note ->
            val previous = notes.getOrNull(index - 1)
            if (previous != null && note.startMs - previous.endMs <= config.holdMs) {
                phraseStartMs[index] = phraseStartMs[index - 1]
                phraseCarry[index] = phraseCarry[index - 1]
            } else {
                phraseStartMs[index] = note.startMs
                phraseCarry[index] = if (previous != null) tagAlphaOf(index - 1, note.startMs) else 0f
            }
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
        // gone out wholly: the pause is longer than the hold and the fade together
        if (tMs >= note.endMs + config.holdMs + config.tagFadeMs) return null
        return OverlayTag(note, tagAlphaOf(index, tMs))
    }

    /** How much of the tag of the note at [index] shows at [tMs], from the start of its run (and what it carried in) to its fading. */
    private fun tagAlphaOf(index: Int, tMs: Long): Float {
        val shownUntil = notes[index].endMs + config.holdMs
        val fade = config.tagFadeMs.toFloat()
        val coming = (phraseCarry[index] + (minOf(tMs, shownUntil) - phraseStartMs[index]) / fade).coerceIn(0f, 1f)
        val going = if (tMs < shownUntil) 1f else (1f - (tMs - shownUntil) / fade).coerceIn(0f, 1f)
        return coming * going
    }

    /** The notes that touch the span from [fromMs] to [toMs], in time order. */
    fun notesBetween(fromMs: Long, toMs: Long): List<OverlayNote> {
        val shown = ArrayList<OverlayNote>()
        var index = firstEndingAfter(fromMs)
        while (index < notes.size && notes[index].startMs < toMs) shown += notes[index++]
        return shown
    }

    /** The index of the first note that ends after [tMs] — [notes] size when none does. */
    internal fun firstEndingAfter(tMs: Long): Int {
        var low = 0
        var high = notes.size
        // ends grow with starts, the notes never overlapping
        while (low < high) {
            val middle = (low + high) / 2
            if (notes[middle].endMs <= tMs) low = middle + 1 else high = middle
        }
        return low
    }

    /**
     * The opening title at [tMs] of a video that ends at [videoEndMs] (spec 5.30): it comes in, dropping into place, stays and
     * goes out. A video shorter than [NotesVideoConfig.openingShortVideoMs] ends it [NotesVideoConfig.openingLeadMs] before its
     * own end — what is cut is first the time it stays, then the coming in and the going out alike, each no shorter than
     * [NotesVideoConfig.openingMinFadeMs]; one shorter than [NotesVideoConfig.openingMinVideoMs] has none. Null while none shows.
     */
    fun openingAt(tMs: Long, videoEndMs: Long): OverlayOpening? {
        if (videoEndMs < config.openingMinVideoMs) return null
        val start = config.openingInMs
        val end = if (videoEndMs < config.openingShortVideoMs) videoEndMs - config.openingLeadMs else config.openingEndMs
        val fullIn = config.openingShownMs - config.openingInMs
        val fullOut = config.openingEndMs - config.openingOutMs
        val span = end - start
        val (fadeIn, fadeOut) = if (span >= fullIn + fullOut) {
            fullIn to fullOut
        } else {
            val half = maxOf(config.openingMinFadeMs, span / 2)
            half to half
        }
        val outFrom = maxOf(start + fadeIn, end - fadeOut)
        return when {
            tMs < start || tMs >= outFrom + fadeOut -> null
            tMs < start + fadeIn -> {
                val shown = easeOut((tMs - start).toFloat() / fadeIn)
                OverlayOpening(shown, config.openingDropU * (1f - shown))
            }
            tMs < outFrom -> OverlayOpening(1f, 0f)
            else -> {
                val gone = (tMs - outFrom).toFloat() / fadeOut
                OverlayOpening(1f - gone * gone, 0f)
            }
        }
    }

    /** Slowing towards the end (spec 5.30). */
    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

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
     * the tolerance of its own recording into, as the screen of the recording does; [title] — of the summary and the file,
     * [heading] and [date] — the same in two lines, for the opening.
     */
    fun of(
        details: SessionDetails,
        recordings: List<SessionSummary>,
        intonation: IntonationConfig,
        title: String,
        heading: String,
        date: String,
        config: NotesVideoConfig,
    ): NotesOverlay {
        val summary = details.summary
        val own = intonation.forSession(summary)
        val segments = details.analysis.segments
        val notes = segments.map { OverlayNote(it.midi, it.startMs, it.endMs, zoneOf(it.meanCents, own), it.meanCents) }
        val (low, high) = heights(notes.map { it.midi }, config)
        return NotesOverlay(
            notes = notes,
            lowMidi = low,
            highMidi = high,
            scorePercent = summary.scorePercent,
            toleranceCents = summary.toleranceCents.roundToInt(),
            title = title,
            heading = heading,
            date = date,
            bestMidi = bestNote(details, own, config),
            drift = details.analysis.metrics?.problemNotes?.firstOrNull()?.let { OverlayDrift(it.midi, it.meanCents, zoneOf(it.meanCents, own)) },
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

    /**
     * The zone of a mean as it is printed: whole cents. The tag and «Что уходит» say the rounded number, so +8.3 with a tolerance
     * of 8 is «+8» and in tune — the colour, the arrow and the number never disagree (spec 3.37).
     */
    internal fun zoneOf(meanCents: Double, own: IntonationConfig): Zone = ZoneClassifier.classify(meanCents.roundToInt().toDouble(), own)
}
