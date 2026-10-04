package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.playback.PictureFollow.Step
import com.violinjourney.app.core.audio.playback.PictureFollow.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The picture of iOS after the words of the sound (spec 5.13, 0.94): it waits for a sound that has not started yet instead of running
 * ahead of it and seeking back, runs with a sound that moves, stands on the very frame of a paused one, and lets words that say nothing
 * new of the sound — A/B, a knob — go by.
 */
class PictureFollowTest {
    @Test
    fun `a play stands the picture until the sound moves - then it runs where it stood`() {
        val paused = Word(positionMs = 0, playing = false, atMs = 0)
        val play = Word(positionMs = 0, playing = true, atMs = 10)
        assertEquals(Step(runs = false, seekToMs = null), PictureFollow.step(paused, play, pictureMs = 0), "the output is still starting")
        // the first chunk heard a third of a second later: the sound moves, and the picture goes with it from where it stood
        val heard = Word(positionMs = 43, playing = true, atMs = 330)
        assertEquals(Step(runs = true, seekToMs = null), PictureFollow.step(play, heard, pictureMs = 0))
    }

    @Test
    fun `a running picture is put back only when it is far from the sound`() {
        val before = Word(positionMs = 10_000, playing = true, atMs = 5_000)
        val now = Word(positionMs = 10_043, playing = true, atMs = 5_043)
        assertEquals(Step(runs = true, seekToMs = null), PictureFollow.step(before, now, pictureMs = 10_150))
        assertEquals(Step(runs = true, seekToMs = 10_043), PictureFollow.step(before, now, pictureMs = 10_250))
    }

    @Test
    fun `a word that says nothing new of the sound leaves the picture as it goes`() {
        val playing = Word(positionMs = 10_000, playing = true, atMs = 5_000)
        assertNull(PictureFollow.step(playing, playing.copy(atMs = 5_020), pictureMs = 10_400), "A/B turned while it plays")
    }

    @Test
    fun `a pause stands the picture on the frame of the sound - a chunk behind is no reason to seek`() {
        val playing = Word(positionMs = 10_000, playing = true, atMs = 5_000)
        val paused = Word(positionMs = 10_010, playing = false, atMs = 5_005)
        assertEquals(Step(runs = false, seekToMs = 10_010), PictureFollow.step(playing, paused, pictureMs = 10_100))
        // started when the first chunk was heard, the picture runs a chunk behind the place of the sound
        assertEquals(Step(runs = false, seekToMs = null), PictureFollow.step(playing, paused, pictureMs = 9_967))
    }

    @Test
    fun `a jump while it plays seeks and stands until the sound moves on from there`() {
        val playing = Word(positionMs = 5_000, playing = true, atMs = 1_000)
        // «Смотреть это место», the slider dragged: the sound starts anew where it was sent
        val sent = Word(positionMs = 20_000, playing = true, atMs = 1_043)
        assertEquals(Step(runs = false, seekToMs = 20_000), PictureFollow.step(playing, sent, pictureMs = 5_040))
        val heard = Word(positionMs = 20_043, playing = true, atMs = 1_400)
        assertEquals(Step(runs = true, seekToMs = null), PictureFollow.step(sent, heard, pictureMs = 20_000))
    }

    @Test
    fun `the first word stands the picture where the sound is`() {
        assertEquals(Step(runs = false, seekToMs = null), PictureFollow.step(null, Word(positionMs = 0, playing = false, atMs = 0), pictureMs = 0))
        assertEquals(Step(runs = false, seekToMs = 0), PictureFollow.step(null, Word(positionMs = 0, playing = true, atMs = 0), pictureMs = 300))
    }
}
