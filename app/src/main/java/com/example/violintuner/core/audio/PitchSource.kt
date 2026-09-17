package com.example.violintuner.core.audio

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.PitchFrame
import kotlinx.coroutines.flow.Flow

/** A stream of pitch estimates. Collecting starts the source, cancelling the collector stops it. */
interface PitchSource {
    /** True when [frames] must not be collected before RECORD_AUDIO is granted. */
    val requiresMicPermission: Boolean

    /**
     * Frames analysed with [config], which may change between collections as the player edits
     * the settings. Fails with [MicUnavailableException] when the input cannot be opened or
     * breaks down.
     */
    fun frames(config: IntonationConfig): Flow<PitchFrame>
}

/** The microphone could not be opened or stopped delivering audio; retrying later may help. */
class MicUnavailableException(message: String) : Exception(message)
