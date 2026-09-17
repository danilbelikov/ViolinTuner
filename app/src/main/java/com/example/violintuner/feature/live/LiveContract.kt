package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.Zone

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
    ) : LiveSignal
}

/** Geometry of the cents scale, taken from [IntonationConfig]. */
data class ScaleSpec(
    val rangeCents: Double,
    val toleranceCents: Double,
) {
    constructor(config: IntonationConfig) : this(config.scaleRangeCents, config.toleranceCents)
}

data class LiveState(
    val mode: LiveMode,
    val signal: LiveSignal,
    val scale: ScaleSpec,
    /** Duration of the zone color cross-fade (spec 3.2). */
    val zoneCrossfadeMs: Int,
)

sealed interface LiveIntent {
    data class SelectMode(val mode: LiveMode) : LiveIntent

    data object RecordClicked : LiveIntent

    data object GrantMicClicked : LiveIntent

    /** Reported by the route on every resume and after the system dialog. */
    data class MicPermissionChanged(val granted: Boolean) : LiveIntent
}

sealed interface LiveEffect {
    /** Recording is v2; the button only explains that (spec 7). */
    data object ShowRecordingUnavailable : LiveEffect

    data object RequestMicPermission : LiveEffect
}
