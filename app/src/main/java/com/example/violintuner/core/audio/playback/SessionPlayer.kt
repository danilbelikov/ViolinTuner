package com.example.violintuner.feature.session.player

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    /** False until the file is prepared; the controls are not shown before that. */
    val ready: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** The file cannot be played: the player disappears, the rest of the screen stays. */
    val failed: Boolean = false,
)

/** Plays the sound of one session (spec 3.10, item 3). Main-thread only. */
interface SessionPlayer {
    val state: StateFlow<PlayerState>

    fun load(file: File)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun release()
}

fun interface SessionPlayerFactory {
    /** [scope] carries the position updates and ends with the screen. */
    fun create(scope: CoroutineScope): SessionPlayer
}
