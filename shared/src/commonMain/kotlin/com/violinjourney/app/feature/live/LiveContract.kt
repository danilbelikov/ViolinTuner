package com.violinjourney.app.feature.live

import androidx.compose.runtime.Immutable
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.domain.venue.Venue

enum class LiveMode { PLAY, TUNING }

/** What the indicator shows; one case per row of the state table in spec 3.4. */
sealed interface LiveSignal {
    data object Silence : LiveSignal

    data object TooNoisy : LiveSignal

    data object NoMicPermission : LiveSignal

    /** The microphone cannot be opened right now; the view model keeps retrying. */
    data object MicUnavailable : LiveSignal

    /**
     * InTune, Sharp and Flat rows: they differ only in [zone] and [direction]. Only what is said in words and shapes is
     * here — it changes a few times a second at most; the numbers that move on every frame are in [LiveGauge].
     */
    data class Sounding(
        val note: Note,
        val zone: Zone,
        /** Null while in tune. */
        val direction: Direction?,
        /** The cents as digits: whole, within two digits, changing calmly ([LiveReadout], spec 5.8). */
        val displayCents: Int,
        /** The note has been held in tune for the whole hold (spec 5.3): the ring glows full and sends its wave. */
        val holdComplete: Boolean = false,
        /** Moves whenever a new note sounds; the ring sends a wave when it does. */
        val noteSerial: Int = 0,
    ) : LiveSignal
}

/**
 * What moves on Live with every frame of sound: the cents the marker of the scale rides on, the loudness the halo of the
 * ring breathes with, the glow the ring moves towards, and the mini ribbon of a take. Apart from [LiveState] on purpose:
 * it is read only while drawing and in effects, never in composition — a screen that read it there would be composed
 * again on every frame (docs/plan-performance.md).
 */
data class LiveGauge(
    /** The smoothed cents of the note; null without one. */
    val cents: Double? = null,
    /** Loudness 0..1, smoothed; 0 without a note. */
    val level: Float = 0f,
    /** 0..1, what the glow of the ring moves towards; how fast is the screen's business (spec 5.8). */
    val glowTarget: Float = 0f,
    /** The notes of the take being recorded (spec 3.9); null when nothing is recorded. */
    val ribbon: RecordingRibbon? = null,
)

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

/** String row of the tuning mode (spec 3.5). Immutable: its map is made once per configuration and never changed. */
@Immutable
data class TuningState(
    /** Pinned by a tap; null means the nearest string is followed automatically. */
    val lockedString: ViolinString?,
    /** The current target: the locked string, else the one nearest to the sound, else none. */
    val targetString: ViolinString?,
    /** Rounded open-string frequencies for the button captions. */
    val stringHz: Map<ViolinString, Int>,
)

/**
 * The strip above the record button while a session is being recorded (spec 3.9): its timer, in whole seconds — as the
 * timer shows it. Its notes move with every closed bucket and are in [LiveGauge.ribbon].
 */
data class RecordingState(val elapsedMs: Long)

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
    /** The glow of the ring without the growth of the hold: the zone's step, and full only once the hold is complete. */
    val glowStep: Float = 0f,
    /** Null while a note sounds and without the permission: the line is hidden, its place stays. */
    val statusLine: StatusLine? = null,
    /** How long the running practice has been going; null when none runs (the tag by the record key, spec 3.12). */
    val practiceMs: Long? = null,
    /** Where the player is, and so what picture Live takes place in: the room or a hall (spec 3.27); null until it is read. */
    val venue: Venue? = null,
    /**
     * When the last note or take on Live ended, on the monotonic clock (`monotonicNanos`); null — none since the screen's
     * model was made. The light comes back six seconds after it (spec 5.20), counted across a rotation and a trip to
     * another screen. Means nothing while a note sounds or a take runs.
     */
    val quietSinceNanos: Long? = null,
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

    /** The practice tag by the record key (spec 3.12): starts a practice, or leads to «Закончить занятие» of the running one. */
    data object PracticeTagClicked : LiveIntent

    /** The gear in the row of the switcher (spec 3.8, 4). */
    data object SettingsClicked : LiveIntent
}

sealed interface LiveEffect {
    /** A recording was stopped by the player and saved: show it (spec 3.9). */
    data class OpenSession(val id: Long) : LiveEffect

    /** A recording was stopped by the player, but there was not a single note in it. */
    data object ShowNoNotesRecorded : LiveEffect

    data object RequestMicPermission : LiveEffect

    /** «Закончить занятие»: its sheet lives on «Занятия», with the recap after it (spec 3.12, 3.31). */
    data object FinishPractice : LiveEffect

    data object OpenSettings : LiveEffect
}
