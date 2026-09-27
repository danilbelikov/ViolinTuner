package com.violinjourney.app.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

/** The I/O buffer of the session: a hop while a microphone listens, and what was before once none does. */
class IoBufferPreferenceTest {
    private var preferred = 0.005
    private val writes = ArrayList<Double>()
    private val preference = IoBufferPreference(read = { preferred }, write = { preferred = it; writes += it })

    private fun microphoneIn(seconds: Double = HOP) {
        preference.microphoneOpening(seconds)
        preference.microphoneOpened()
    }

    @Test
    fun `a microphone asks for a hop and gives the old one back when it leaves alone`() {
        microphoneIn()
        assertEquals(HOP, preferred)
        preference.microphoneLeft(remaining = 0)
        assertEquals(0.005, preferred)
    }

    @Test
    fun `a microphone leaving under a player does not reconfigure it — the letting go gives it back`() {
        microphoneIn()
        preference.playerOpening(others = 1) // a player comes in while the Live still listens for its two seconds
        preference.microphoneLeft(remaining = 1)
        assertEquals(HOP, preferred, "the player plays on as it began")
        preference.lettingGo()
        assertEquals(0.005, preferred)
    }

    @Test
    fun `a player taking its output again alone plays with what was before`() {
        microphoneIn()
        preference.playerOpening(others = 1)
        preference.microphoneLeft(remaining = 1)
        assertEquals(HOP, preferred, "its first sound plays on as it began")
        preference.playerOpening(others = 0) // play again after a pause: its engine is stopped, and it is alone
        assertEquals(0.005, preferred)
    }

    @Test
    fun `a player coming in beside another does not reconfigure the other`() {
        microphoneIn()
        preference.playerOpening(others = 1)
        preference.microphoneLeft(remaining = 1)
        preference.playerOpening(others = 1) // another player, the first maybe still playing
        assertEquals(HOP, preferred, "nothing under a running output")
        preference.lettingGo()
        assertEquals(0.005, preferred)
    }

    @Test
    fun `a player with no microphone before it changes nothing`() {
        preference.playerOpening(others = 0)
        preference.lettingGo()
        assertEquals(emptyList(), writes)
    }

    @Test
    fun `two microphones remember the preference of before both`() {
        microphoneIn()
        microphoneIn()
        preference.microphoneLeft(remaining = 1)
        assertEquals(HOP, preferred, "one still listens")
        preference.microphoneLeft(remaining = 0)
        assertEquals(0.005, preferred)
    }

    @Test
    fun `a microphone whose session would not open leaves nothing behind`() {
        preference.microphoneOpening(HOP)
        preference.microphoneFailed()
        assertEquals(0.005, preferred)
        preference.lettingGo()
        assertEquals(listOf(HOP, 0.005), writes, "nothing more to give back")
    }

    private companion object {
        const val HOP = 512 / 48_000.0
    }
}
