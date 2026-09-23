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
    /** A take under a backing whose sound could be prepared: the switch «с минусовкой / только скрипка» is there (spec 3.32). */
    val hasBacking: Boolean = false,
    /** The backing is heard with the violin; false — «только скрипка». */
    val backingHeard: Boolean = true,
    /**
     * The backing's sound is being made for the mix (spec 5.25): the player is not [ready] until it is, and the screen
     * says so rather than show nothing. Only when it was not made already — a prepared one is ready at once.
     */
    val preparingBacking: Boolean = false,
)

/**
 * The backing of a take made under one (spec 3.32): [pcm] gives its sound prepared at the recording's rate
 * (it runs on the player's thread and may take a moment the first time), [offsetMs] and [gainDb] mix it.
 */
class PlayerBacking(
    val pcm: (sampleRate: Int) -> File?,
    val offsetMs: Int,
    val gainDb: Float,
    /** The sound already made at a rate, without making it: null — [pcm] has work to do, and the screen is told. */
    val cached: (sampleRate: Int) -> File? = { null },
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

    /** [load] with the backing the take was made under, mixed in; a player without backings just loads the file. */
    fun loadWithBacking(file: File, backing: PlayerBacking?) = load(file)

    /** A new shift or level of the backing, heard at once and gliding in. */
    fun setBackingMix(offsetMs: Int, gainDb: Float) = Unit

    /** «с минусовкой / только скрипка». */
    fun setBackingHeard(heard: Boolean) = Unit

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
