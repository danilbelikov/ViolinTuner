package com.violinjourney.app.feature.sound.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.violinjourney.app.core.audio.fx.SoundMeters
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

/** The meters of «Звук» step thirty times a second while the sound plays, inside the frame, and sleep without it (spec 5.11). */
@OptIn(ExperimentalCoroutinesApi::class)
class MeterClockTest {
    @Test
    fun `the output meter steps thirty times a second inside the frame and sleeps at rest`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val meters = mutableStateOf<SoundMeters?>(null)
        val motion = OutputMeterMotion()
        var level = 0f
        val noted = mutableListOf<Float>()
        display.afterFrame = { noted += level }
        backgroundScope.launch(display) { runMeter(motion, reading = { meters.value }) { level = motion.level } }

        advanceTimeBy(2 * SECOND_MS)
        assertEquals(0, display.asked, "frames asked with nothing playing")

        // a bar that is always falling: a new, louder reading every ~43 ms, as the player gives them
        var peak = -40.0
        meters.value = SoundMeters(outputPeakDb = peak, reductionDb = 0.0, limiting = false)
        Snapshot.sendApplyNotifications()
        var ms = 0L
        while (ms < SECOND_MS) {
            advanceTimeBy(43)
            ms += 43
            peak += 0.5
            meters.value = SoundMeters(outputPeakDb = peak, reductionDb = 0.0, limiting = false)
        }
        testScheduler.runCurrent()
        assertTrue(noted.size in 29..32, "${noted.size} frames a second")
        assertTrue(display.asked <= noted.size + 1, "${display.asked} frames asked")
        assertEquals(noted.last(), level, "written inside the frame")

        // the sound stops: the bar falls in ~300 ms and the frames stop with it
        meters.value = null
        Snapshot.sendApplyNotifications()
        advanceTimeBy(500)
        assertEquals(0f, level)
        val asked = display.asked
        advanceTimeBy(2 * SECOND_MS)
        assertEquals(asked, display.asked, "frames asked after the bar has fallen")
    }

    @Test
    fun `the reduction meter steps thirty times a second while the sound plays and sleeps without it`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val reading = mutableStateOf<Double?>(null)
        val motion = ReductionMeterMotion(20.0)
        var level = 0f
        backgroundScope.launch(display) { runMeter(motion, reading = { reading.value }) { level = motion.level } }
        advanceTimeBy(2 * SECOND_MS)
        assertEquals(0, display.asked)

        reading.value = 10.0
        Snapshot.sendApplyNotifications()
        advanceTimeBy(SECOND_MS)
        assertEquals(0.5f, level, 1e-6f)
        val playing = display.asked
        assertTrue(playing in 29..32, "$playing frames a second")

        reading.value = null
        Snapshot.sendApplyNotifications()
        advanceTimeBy(400)
        assertEquals(0f, level)
        val asked = display.asked
        advanceTimeBy(2 * SECOND_MS)
        assertEquals(asked, display.asked)
    }
}
