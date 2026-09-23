package com.violinjourney.app.core.recording

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a recording is being made anywhere in the app — on Live, on the screen of a piece, on
 * the music stand. The pipelines are one per screen; whoever must not start under a recording
 * (a copy of the data, spec 3.20) asks here.
 */
@Singleton
class RecordingWatch @Inject constructor() {
    private val mutableRecording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = mutableRecording.asStateFlow()

    internal fun set(recording: Boolean) {
        mutableRecording.value = recording
    }
}
