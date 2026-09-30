package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class HouseLightsTest {
    private val second = 1_000_000_000L
    private val now = 1_000 * second

    @Test
    fun `no note ever keeps the light on`() {
        assertEquals(0, HouseLights.msStillOut(null, now))
    }

    @Test
    fun `a note two seconds ago keeps it out four more`() {
        assertEquals(4_000, HouseLights.msStillOut(now - 2 * second, now))
    }

    @Test
    fun `six seconds and more after the note it is on`() {
        assertEquals(0, HouseLights.msStillOut(now - 6 * second, now))
        assertEquals(0, HouseLights.msStillOut(now - 10 * second, now))
    }

    @Test
    fun `a moment ahead of now is taken as long ago`() {
        assertEquals(0, HouseLights.msStillOut(now + second, now))
    }

    @Test
    fun `a note that just ended keeps it out the whole six seconds`() {
        assertEquals(6_000, HouseLights.msStillOut(now, now))
    }

    private fun stateOf(signal: LiveSignal, recording: RecordingState? = null) =
        LiveReducer.stateOf(LiveTarget(LiveMode.PLAY, null), IntonationConfig(), signal, recording = recording)

    @Test
    fun `one plays while a note sounds or a take records and only then`() {
        val sounding = LiveSignal.Sounding(note = Note(A4), zone = Zone.IN_TUNE, direction = null, displayCents = 2)
        assertTrue(HouseLights.playing(stateOf(sounding)), "a note sounds")
        assertTrue(HouseLights.playing(stateOf(LiveSignal.Silence, RecordingState(elapsedMs = 1_000))), "a take records in a pause")
        assertTrue(HouseLights.playing(stateOf(sounding, RecordingState(elapsedMs = 1_000))), "both")
        for (quiet in listOf(LiveSignal.Silence, LiveSignal.TooNoisy, LiveSignal.MicUnavailable, LiveSignal.NoMicPermission)) {
            assertFalse(HouseLights.playing(stateOf(quiet)), "$quiet without a take")
        }
    }

    private companion object {
        const val A4 = 69
    }
}
