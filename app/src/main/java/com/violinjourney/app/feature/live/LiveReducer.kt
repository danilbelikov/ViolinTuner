package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import kotlin.math.floor
import kotlin.math.roundToInt

/** What the user has chosen to measure against; read by the engine on every frame. */
data class LiveTarget(
    val mode: LiveMode = LiveMode.PLAY,
    val lockedString: ViolinString? = null,
)

object LiveReducer {
    /**
     * How strongly the ring should glow (spec 5.8): nothing without a note, little for a miss,
     * more near, bright in tune and growing to full with the hold. A miss is quieter than a
     * hit on purpose.
     */
    fun glowTargetOf(signal: LiveSignal, config: IntonationConfig, stepped: Boolean = false): Float {
        val sounding = signal as? LiveSignal.Sounding ?: return 0f
        val hold = sounding.holdProgress.toFloat().coerceIn(0f, 1f)
        return when (sounding.zone) {
            Zone.OFF -> config.glowOff
            Zone.NEAR -> config.glowNear
            // Stepped is for system animations switched off: no growth, only the two levels.
            Zone.IN_TUNE -> config.glowInTune + (1f - config.glowInTune) * (if (stepped) floor(hold) else hold)
        }
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

    fun tuningStateOf(target: LiveTarget, signal: LiveSignal, config: IntonationConfig): TuningState {
        val sounding = (signal as? LiveSignal.Sounding)?.takeIf { target.mode == LiveMode.TUNING }
        return TuningState(
            lockedString = target.lockedString,
            targetString = target.lockedString ?: sounding?.let { ViolinString.fromMidi(it.note.midi) },
            stringHz = ViolinString.entries.associateWith { it.frequency(config.a4Hz).roundToInt() },
        )
    }
}
