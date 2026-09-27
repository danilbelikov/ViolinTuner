package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The rate a backing is made ready at on iOS (spec 5.25): what the microphone last got on the route the sound goes to —
 * a take under the backing is made in headphones, while Live mostly listens on the loudspeaker.
 */
class IosRecordingRateTest {
    private val speaker = AudioRoute(BackingOutput.SPEAKER, null)
    private val usb = AudioRoute(BackingOutput.USB, "Scarlett 2i2")
    private var now = speaker
    private val routes = object : AudioRoutes {
        override fun current(): AudioRoute = now

        override val changes: Flow<AudioRoute> = emptyFlow()
    }
    private val rate = IosRecordingRate(listOf(48_000, 44_100), routes)

    @Test
    fun `before the microphone has opened it is the rate the microphone asks for`() {
        assertEquals(48_000, rate.likelyHz())
    }

    @Test
    fun `a route that gave 44_1 kHz is made ready at it and the loudspeaker is not`() {
        now = usb
        rate.heard(44_100)
        assertEquals(44_100, rate.likelyHz())

        // Live on the loudspeaker in between does not undo what the headphones gave
        now = speaker
        assertEquals(48_000, rate.likelyHz())
        rate.heard(48_000)
        now = usb
        assertEquals(44_100, rate.likelyHz())
    }

    @Test
    fun `the last rate a route gave is the one`() {
        rate.heard(44_100)
        rate.heard(48_000)
        assertEquals(48_000, rate.likelyHz())
    }

    @Test
    fun `a rate that is not ours falls back to the first of ours`() {
        rate.heard(96_000)
        assertEquals(48_000, rate.likelyHz())
    }
}
