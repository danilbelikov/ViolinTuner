package com.example.violintuner.core.audio.playback

import com.example.violintuner.core.audio.fx.SoundMeters
import com.example.violintuner.core.domain.sound.SoundSettings
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
    /** The settings given to the player do something to the sound: there is an A and a B to tell apart. */
    val processed: Boolean = false,
    /** A/B stands at A: the recording is heard as it was recorded, whatever the settings. */
    val original: Boolean = false,
)

/**
 * Plays the sound of one session (spec 3.10, item 3) the way its settings make it sound
 * (spec 3.17). Main-thread only. The position is that of the recording: processing does not
 * shift it, and the tail of the hall rings on with the position at the end.
 */
interface SessionPlayer {
    val state: StateFlow<PlayerState>

    /** Level at the output and what the compressor and the limiter are doing; null while nothing plays through the chain. */
    val meters: StateFlow<SoundMeters?>

    fun load(file: File)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Heard at once — also while playing, gliding in without a click. */
    fun setSound(settings: SoundSettings)

    /** A/B: true — the original; false — the processing, which is what a recording starts with. */
    fun setOriginal(original: Boolean)

    fun release()
}

fun interface SessionPlayerFactory {
    /** [scope] ends with the screen. */
    fun create(scope: CoroutineScope): SessionPlayer
}
