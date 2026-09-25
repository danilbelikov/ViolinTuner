package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.io.PlatformFile
import kotlinx.coroutines.flow.StateFlow

/**
 * The backing listened to on the piece screen (spec 3.32): as it is, through whatever output there is — the speaker
 * too: listening is not recording. Main thread only.
 */
interface BackingPreview {
    val playing: StateFlow<Boolean>

    /** Plays [file] from its start, or stops it when it plays. */
    fun toggle(file: PlatformFile)

    fun stop()
}
