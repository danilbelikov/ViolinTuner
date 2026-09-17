package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.TargetMode

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

    fun targetModeOf(mode: LiveMode): TargetMode = when (mode) {
        LiveMode.PLAY -> TargetMode.Chromatic
        LiveMode.TUNING -> TargetMode.Strings()
    }
}
