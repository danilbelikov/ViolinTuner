package com.violinjourney.app.feature.onboarding.art

import androidx.compose.runtime.mutableFloatStateOf
import com.violinjourney.app.feature.journey.art.SECOND_MS
import com.violinjourney.app.feature.journey.art.VSYNC_120
import com.violinjourney.app.feature.journey.art.VirtualDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/** The pictures of «Знакомство» (spec 3.33): every frame while a page's quick motions run, the postcards' thirty a second after. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtClockTest {
    @Test
    fun `the clock takes every frame while the quick motions run and thirty a second after them`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val seconds = mutableFloatStateOf(0f)
        var smoothUntil = 1f
        val noted = mutableListOf<Float>()
        display.afterFrame = { noted += seconds.floatValue }
        backgroundScope.launch(display) { runArtClock(seconds) { smoothUntil } }

        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        val quick = display.asked
        assertTrue(quick in 118..122, "$quick frames in the first second")
        assertEquals(noted.last(), seconds.floatValue, "written inside the frame")

        advanceTimeBy(2 * SECOND_MS)
        testScheduler.runCurrent()
        val calm = display.asked - quick
        assertTrue(calm in 58..63, "$calm frames in the next two seconds")
        assertTrue(seconds.floatValue in 2.9f..3.01f, "${seconds.floatValue} s")

        // another page comes into view: its quick motions get every frame again
        smoothUntil = seconds.floatValue + 1f
        val before = display.asked
        advanceTimeBy(SECOND_MS / 2)
        assertTrue(display.asked - before in 55..62, "${display.asked - before} frames in half a second")
    }

    @Test
    fun `the quick motions are those that run once and fast`() {
        assertEquals(ArtMotion.WALK_S, ArtMotion.Walk.quickOnceUntil())
        assertEquals(ArtMotion.COPY_DELAY_S + ArtMotion.COPY_S, ArtMotion.Copy.quickOnceUntil())
        assertEquals(1.8f + ArtMotion.APPEAR_S, ArtMotion.Appear(1.8f).quickOnceUntil())
        assertEquals(null, ArtMotion.Sink(20f, 14f).quickOnceUntil())
        assertEquals(null, ArtMotion.Twinkle(3.2f, 0f).quickOnceUntil())
    }
}
