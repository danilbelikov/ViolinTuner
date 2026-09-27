package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.venue.Venue
import kotlin.math.roundToInt

/** What the user has chosen to measure against; read by the engine on every frame. */
data class LiveTarget(
    val mode: LiveMode = LiveMode.PLAY,
    val lockedString: ViolinString? = null,
)

object LiveReducer {
    /**
     * How strongly the ring should glow (spec 5.8) while a note sounds in [zone]: little for a miss,
     * more near, bright in tune and growing to full with the hold. A miss is quieter than a
     * hit on purpose; without a note there is no glow.
     */
    fun glowTargetOf(zone: Zone, holdProgress: Double, config: IntonationConfig): Float {
        val hold = holdProgress.toFloat().coerceIn(0f, 1f)
        return when (zone) {
            Zone.OFF -> config.glowOff
            Zone.NEAR -> config.glowNear
            Zone.IN_TUNE -> config.glowInTune + (1f - config.glowInTune) * hold
        }
    }

    /**
     * The glow for system animations switched off: no growth, only the steps — the zone's, and full once the hold is
     * complete. Nothing without a note.
     */
    fun glowStepOf(signal: LiveSignal, config: IntonationConfig): Float {
        val sounding = signal as? LiveSignal.Sounding ?: return 0f
        return glowTargetOf(sounding.zone, if (sounding.holdComplete) 1.0 else 0.0, config)
    }

    /** The status line (spec 3.14): hidden while a note sounds; without the permission the prompt speaks instead. */
    fun statusLineOf(target: LiveTarget, signal: LiveSignal): StatusLine? = when (signal) {
        is LiveSignal.Sounding, LiveSignal.NoMicPermission -> null
        LiveSignal.TooNoisy -> StatusLine(StatusDot.BLOCKED, StatusMessage.TOO_NOISY)
        LiveSignal.MicUnavailable -> StatusLine(StatusDot.BLOCKED, StatusMessage.MIC_UNAVAILABLE)
        LiveSignal.Silence -> StatusLine(
            StatusDot.READY,
            when {
                target.mode == LiveMode.PLAY -> StatusMessage.PLAY
                target.lockedString != null -> StatusMessage.TUNE_LOCKED
                else -> StatusMessage.TUNE_AUTO
            },
        )
    }

    fun targetModeOf(target: LiveTarget): TargetMode = when (target.mode) {
        LiveMode.PLAY -> TargetMode.Chromatic
        LiveMode.TUNING -> TargetMode.Strings(locked = target.lockedString)
    }

    /** Switching the mode drops the lock (spec 3.5). */
    fun selectMode(target: LiveTarget, mode: LiveMode): LiveTarget =
        if (mode == target.mode) target else LiveTarget(mode = mode, lockedString = null)

    /** A tap pins the string; a tap on the pinned string returns to auto. Tuning mode only. */
    fun clickString(target: LiveTarget, string: ViolinString): LiveTarget = when {
        target.mode != LiveMode.TUNING -> target
        target.lockedString == string -> target.copy(lockedString = null)
        else -> target.copy(lockedString = string)
    }

    /** Recording is a play-mode thing and needs a working microphone (spec 3.9). */
    fun canRecord(target: LiveTarget, signal: LiveSignal): Boolean =
        target.mode == LiveMode.PLAY &&
            signal != LiveSignal.NoMicPermission && signal != LiveSignal.MicUnavailable

    /**
     * The whole screen from what was chosen and what sounds; the same on Android and iOS. [recording] — the strip of
     * a running take, [practiceMs] — the running practice, [venue] — where Live takes place: what the platform knows.
     * [stringHz] — the captions of the strings, made once per configuration ([stringHzOf]); [quietSinceNanos] — when
     * the last note or take ended, for the light ([LiveState.quietSinceNanos]).
     */
    fun stateOf(
        target: LiveTarget,
        config: IntonationConfig,
        signal: LiveSignal,
        recording: RecordingState? = null,
        practiceMs: Long? = null,
        venue: Venue? = null,
        stringHz: Map<ViolinString, Int> = stringHzOf(config),
        quietSinceNanos: Long? = null,
    ) = LiveState(
        mode = target.mode,
        signal = signal,
        tuning = tuningStateOf(target, signal, stringHz),
        recording = recording,
        canRecord = canRecord(target, signal),
        scale = ScaleSpec(config),
        zoneCrossfadeMs = config.zoneCrossfadeMs,
        glowStep = glowStepOf(signal, config),
        statusLine = statusLineOf(target, signal),
        practiceMs = practiceMs,
        venue = venue,
        quietSinceNanos = quietSinceNanos,
    )

    /** The rounded open-string frequencies of the button captions. */
    fun stringHzOf(config: IntonationConfig): Map<ViolinString, Int> =
        ViolinString.entries.associateWith { it.frequency(config.a4Hz).roundToInt() }

    fun tuningStateOf(target: LiveTarget, signal: LiveSignal, config: IntonationConfig): TuningState =
        tuningStateOf(target, signal, stringHzOf(config))

    fun tuningStateOf(target: LiveTarget, signal: LiveSignal, stringHz: Map<ViolinString, Int>): TuningState {
        val sounding = (signal as? LiveSignal.Sounding)?.takeIf { target.mode == LiveMode.TUNING }
        return TuningState(
            lockedString = target.lockedString,
            targetString = target.lockedString ?: sounding?.let { ViolinString.fromMidi(it.note.midi) },
            stringHz = stringHz,
        )
    }
}
