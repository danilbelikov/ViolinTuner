package com.violinjourney.app.core.audio.playback

import kotlin.math.abs

/**
 * How the picture of iOS — a silent AVPlayer, which plays on by itself once it is set going — follows the words of the sound (spec 5.13,
 * 0.94): on a word that says something new of the sound, whether the picture runs, and where it must seek first. After a play or a seek
 * the sound's place stands while the sound is on its way to the ear — the output starting, for the first play the audio session too —
 * and the picture stands with it, on the frame of that place; it runs once the place moves. That is the rule of Android's picture
 * ([PictureCarry]). Until 0.94 the picture ran at once, ahead of a sound that had not started, and seeked back as soon as the sound
 * moved: a jolt at the start of every play. Pure; the player of the platform only does what it says.
 */
object PictureFollow {
    /** What the sound said: where it is, whether it plays, and when — in milliseconds that only go forward. */
    data class Word(val positionMs: Long, val playing: Boolean, val atMs: Long)

    /** What the picture does: run, or stand — after a seek to [seekToMs] when that is not null. */
    data class Step(val runs: Boolean, val seekToMs: Long?)

    /** Further than this from the sound, a running picture is put back in place. */
    const val DRIFT_PLAYING_MS = 150L

    /**
     * A picture that stands shows the frame of the sound's place, give or take this: the place of iOS moves a chunk at a time (~43 ms at
     * 48 kHz), and a picture that started a chunk behind would otherwise seek on nearly every pause.
     */
    const val DRIFT_STILL_MS = 80L

    /**
     * The step for [word], [previous] being the last word that said something new; [pictureMs] — where the picture is, or is going with
     * a seek on its way. Null when [word] says nothing new of the sound — A/B or a knob turned while it plays: the picture goes on as it goes.
     */
    fun step(previous: Word?, word: Word, pictureMs: Long): Step? {
        if (previous != null && previous.positionMs == word.positionMs && previous.playing == word.playing) return null
        val moving = previous != null &&
            PictureCarry.carries(previous.positionMs, previous.atMs, previous.playing, word.positionMs, word.atMs, word.playing)
        val drift = abs(pictureMs - word.positionMs)
        return Step(runs = moving, seekToMs = word.positionMs.takeIf { drift > if (moving) DRIFT_PLAYING_MS else DRIFT_STILL_MS })
    }
}
