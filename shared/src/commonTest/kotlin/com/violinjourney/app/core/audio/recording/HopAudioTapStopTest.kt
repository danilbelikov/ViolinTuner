package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.io.platformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The encoder a stop has taken out of the tap is closed, whatever becomes of the one who asked (the rest — `HopAudioTapTest` in `app`). */
@OptIn(ExperimentalCoroutinesApi::class)
class HopAudioTapStopTest {
    private class FakeEncoder : PcmEncoder {
        var finished = 0

        override fun offer(hop: ShortArray, count: Int): Boolean = true

        override fun finish(): Boolean {
            finished++
            return true
        }
    }

    @Test
    fun `stop from a caller cancelled meanwhile still closes the encoder`() = runTest {
        val encoder = FakeEncoder()
        // a dispatcher of its own: the closing really moves off the caller's, as it does onto the encoder's thread
        val tap = HopAudioTap(encoderFactory = { _, _ -> encoder }, finishDispatcher = StandardTestDispatcher(testScheduler), analytics = NoOpAnalytics())
        tap.start(platformFile("/violin-test/take.m4a"))
        tap.onHop(ShortArray(HOP), HOP, hopStartTMs = 0, sampleRateHz = RATE)

        val stopping = launch(start = CoroutineStart.UNDISPATCHED) { tap.stop() }
        assertEquals(AudioTap.State.Idle, tap.state, "the encoder is out of the tap")
        assertEquals(0, encoder.finished, "its closing waits for the finishing dispatcher")
        stopping.cancel()
        advanceUntilIdle()

        assertEquals(1, encoder.finished, "the file is closed, and the encoder's thread with it")
    }

    private companion object {
        const val HOP = 512
        const val RATE = 48_000
    }
}
