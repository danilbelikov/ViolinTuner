package com.violinjourney.app.core.audio

import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionReasonAppWasSuspended
import platform.AVFAudio.AVAudioSessionInterruptionReasonDefault
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which interruptions end the microphone of iOS — those that take the input away, not those that give it back — and
 * which stops of its audio engine: those of iOS, not those of another user of the session in the app.
 */
class IosMicInterruptionTest {
    @Test
    fun `an interruption that begins ends the input`() {
        assertTrue(interruptionEndsInput(AVAudioSessionInterruptionTypeBegan, AVAudioSessionInterruptionReasonDefault, engineRunning = false))
        assertTrue(interruptionEndsInput(AVAudioSessionInterruptionTypeBegan, reason = null, engineRunning = true))
    }

    @Test
    fun `an interruption that ends leaves the input alone — it may be open again already`() {
        assertFalse(interruptionEndsInput(AVAudioSessionInterruptionTypeEnded, AVAudioSessionInterruptionReasonDefault, engineRunning = true))
        assertFalse(interruptionEndsInput(AVAudioSessionInterruptionTypeEnded, reason = null, engineRunning = false))
    }

    @Test
    fun `a late beginning for a suspended app ends the input only if the engine stopped`() {
        assertFalse(interruptionEndsInput(AVAudioSessionInterruptionTypeBegan, AVAudioSessionInterruptionReasonAppWasSuspended, engineRunning = true))
        assertTrue(interruptionEndsInput(AVAudioSessionInterruptionTypeBegan, AVAudioSessionInterruptionReasonAppWasSuspended, engineRunning = false))
    }

    @Test
    fun `a notification without a type is taken as a beginning`() {
        assertTrue(interruptionEndsInput(type = null, reason = null, engineRunning = true))
    }

    @Test
    fun `an engine still running after a change of its configuration keeps the input`() {
        assertFalse(engineStopEndsInput(engineRunning = true, category = AVAudioSessionCategoryPlayAndRecord))
        assertFalse(engineStopEndsInput(engineRunning = true, category = AVAudioSessionCategoryPlayback))
    }

    @Test
    fun `an engine iOS stopped while the session still records ends the input`() {
        assertTrue(engineStopEndsInput(engineRunning = false, category = AVAudioSessionCategoryPlayAndRecord))
    }

    @Test
    fun `an engine stopped because the player took the session is no lost input`() {
        // a player started while a Live that went away still listens: no mic_unavailable for that (spec 5.27)
        assertFalse(engineStopEndsInput(engineRunning = false, category = AVAudioSessionCategoryPlayback))
    }

    @Test
    fun `an engine stopped with no category to tell is left to the no-sound limit`() {
        assertFalse(engineStopEndsInput(engineRunning = false, category = null))
    }
}
