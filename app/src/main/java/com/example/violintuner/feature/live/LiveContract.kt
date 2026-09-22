package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.RecordingBar
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.domain.venue.VenueEntry
import kotlin.math.roundToInt

enum class LiveMode { PLAY, TUNING }

/** What the indicator shows; one case per row of the state table in spec 3.4. */
sealed interface LiveSignal {
    data object Silence : LiveSignal

    data object TooNoisy : LiveSignal

    data object NoMicPermission : LiveSignal

    /** The microphone cannot be opened right now; the view model keeps retrying. */
    data object MicUnavailable : LiveSignal

    /** InTune, Sharp and Flat rows: they differ only in [zone] and [direction]. */
    data class Sounding(
        val note: Note,
        val cents: Double,
        val zone: Zone,
        /** Null while in tune. */
        val direction: Direction?,
        val holdProgress: Double,
        /** The cents as digits: whole, within two digits, changing calmly ([LiveReadout], spec 5.8). */
        val displayCents: Int = cents.roundToInt(),
        /** Loudness 0..1, smoothed; the halo of the ring breathes with it. */
        val level: Float = 0f,
        /** Moves whenever a new note sounds; the ring sends a wave when it does. */
        val noteSerial: Int = 0,
    ) : LiveSignal
}

/** The dot of the status line: may one play right now. Two shapes, not only two colors (spec 3.14). */
enum class StatusDot { READY, BLOCKED }

enum class StatusMessage { PLAY, TOO_NOISY, MIC_UNAVAILABLE, TUNE_AUTO, TUNE_LOCKED }

/**
 * The small line above the ring, or the hint under the string row in tuning mode — the same
 * line in two places (spec 3.14). [StatusMessage.TUNE_LOCKED] is worded with the locked string
 * of [TuningState].
 */
data class StatusLine(val dot: StatusDot, val message: StatusMessage)

/** Geometry of the cents scale, taken from [IntonationConfig]. */
data class ScaleSpec(
    val rangeCents: Double,
    val toleranceCents: Double,
) {
    constructor(config: IntonationConfig) : this(config.scaleRangeCents, config.toleranceCents)
}

/** String row of the tuning mode (spec 3.5). */
data class TuningState(
    /** Pinned by a tap; null means the nearest string is followed automatically. */
    val lockedString: ViolinString?,
    /** The current target: the locked string, else the one nearest to the sound, else none. */
    val targetString: ViolinString?,
    /** Rounded open-string frequencies for the button captions. */
    val stringHz: Map<ViolinString, Int>,
)

/** The strip above the record button while a session is being recorded (spec 3.9). */
data class RecordingState(
    val elapsedMs: Long,
    /** Notes played so far as shares of the mini bar, in order. */
    val bars: List<RecordingBar>,
)

data class LiveState(
    val mode: LiveMode,
    val signal: LiveSignal,
    val tuning: TuningState,
    /** Null when nothing is being recorded. */
    val recording: RecordingState?,
    /** Recording exists only in play mode and only while the microphone delivers. */
    val canRecord: Boolean,
    val scale: ScaleSpec,
    /** Duration of the zone color cross-fade (spec 3.2). */
    val zoneCrossfadeMs: Int,
    /** 0..1, what the glow of the ring moves towards; how fast is the screen's business (spec 5.8). */
    val glowTarget: Float = 0f,
    /** The same without the growth of the hold: the zone's step, and full only once the hold is complete. */
    val glowStep: Float = 0f,
    /** Null while a note sounds and without the permission: the line is hidden, its place stays. */
    val statusLine: StatusLine? = null,
    /** How long the running practice has been going; null when none runs (the chip, spec 3.12). */
    val practiceMs: Long? = null,
    /** Where the player is, and so where Live takes place: the room or a hall (spec 3.27); null until it is read. */
    val venue: Venue? = null,
    /** The list «Где играть»: the room, the reached halls, the next stop, the road ahead. */
    val venueMenu: List<VenueEntry> = emptyList(),
)

sealed interface LiveIntent {
    data class SelectMode(val mode: LiveMode) : LiveIntent

    /** Tuning mode: pins the string, or returns to auto when it is pinned already. */
    data class StringClicked(val string: ViolinString) : LiveIntent

    /** Starts a recording, or stops the running one. */
    data object RecordClicked : LiveIntent

    data object GrantMicClicked : LiveIntent

    /** Reported by the route on every resume and after the system dialog. */
    data class MicPermissionChanged(val granted: Boolean) : LiveIntent

    /** The «занятие · 12:34» chip leads to the practice tab. */
    data object PracticeChipClicked : LiveIntent

    /** «Где играть»: the player goes to [venue] (spec 3.27); not while a recording runs. */
    data class VenueChosen(val venue: Venue) : LiveIntent
}

sealed interface LiveEffect {
    /** A recording was stopped by the player and saved: show it (spec 3.9). */
    data class OpenSession(val id: Long) : LiveEffect

    /** A recording was stopped by the player, but there was not a single note in it. */
    data object ShowNoNotesRecorded : LiveEffect

    data object RequestMicPermission : LiveEffect

    data object OpenPractice : LiveEffect
}
