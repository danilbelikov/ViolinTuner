package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.ViolinString
import kotlin.math.roundToInt

/** What the user has chosen to measure against; read by the engine on every frame. */
data class LiveTarget(
    val mode: LiveMode = LiveMode.PLAY,
    val lockedString: ViolinString? = null,
)

object LiveReducer {
    fun signalOf(reading: IntonationReading): LiveSignal = when (reading) {
        IntonationReading.Silence -> LiveSignal.Silence
        IntonationReading.TooNoisy -> LiveSignal.TooNoisy
        is IntonationReading.Active -> LiveSignal.Sounding(
            note = reading.note,
            cents = reading.cents,
            zone = reading.zone,
            direction = reading.direction,
            holdProgress = reading.holdProgress,
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

    fun tuningStateOf(target: LiveTarget, signal: LiveSignal, config: IntonationConfig): TuningState {
        val sounding = (signal as? LiveSignal.Sounding)?.takeIf { target.mode == LiveMode.TUNING }
        return TuningState(
            lockedString = target.lockedString,
            targetString = target.lockedString ?: sounding?.let { ViolinString.fromMidi(it.note.midi) },
            stringHz = ViolinString.entries.associateWith { it.frequency(config.a4Hz).roundToInt() },
        )
    }
}
