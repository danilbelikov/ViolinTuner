package com.example.violintuner.core.audio

import com.example.violintuner.core.domain.PitchFrame
import kotlinx.coroutines.flow.Flow

/** A stream of pitch estimates. Collecting starts the source, cancelling the collector stops it. */
interface PitchSource {
    val frames: Flow<PitchFrame>
}
