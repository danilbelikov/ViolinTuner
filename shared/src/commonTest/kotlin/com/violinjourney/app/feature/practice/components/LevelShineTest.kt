package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.violinjourney.app.feature.journey.art.SECOND_MS
import com.violinjourney.app.feature.journey.art.VSYNC_120
import com.violinjourney.app.feature.journey.art.VirtualDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/** The shine of the level bar (spec 3.16) runs its passes only while the bar is on the screen and still. */
@OptIn(ExperimentalCoroutinesApi::class)
class LevelShineTest {
    private class Bar(scope: TestScope, seen: Boolean = true) {
        val display = VirtualDisplay(scope.testScheduler, VSYNC_120)
        val shine = LevelShine().apply {
            barWidthPx = BAR_PX
            this.seen = seen
        }
        val growing = mutableStateOf(false)
        val noted = mutableListOf<Float>()

        init {
            display.afterFrame = { noted += shine.progress }
            scope.backgroundScope.launch(display) {
                runShinePasses(shine, fill = { FILL }, growing = { growing.value }, density = DENSITY)
            }
        }
    }

    @Test
    fun `out of sight no pass runs and no frame is asked`() = runTest {
        val bar = Bar(this, seen = false)
        advanceTimeBy(10 * SECOND_MS)
        assertEquals(0, bar.display.asked, "frames asked out of sight")
        assertEquals(LevelShine.NONE, bar.shine.progress)

        // back in view it waits the usual pause, then runs
        bar.shine.seen = true
        Snapshot.sendApplyNotifications()
        advanceTimeBy(REST_MS - 10)
        assertEquals(0, bar.display.asked, "frames asked during the pause")
        advanceTimeBy(PASS_MS + 100)
        assertTrue(bar.display.asked > 100, "${bar.display.asked} frames for a pass")
        assertEquals(LevelShine.NONE, bar.shine.progress)
    }

    @Test
    fun `a pass is written inside its frames and ends with the light out`() = runTest {
        val bar = Bar(this)
        advanceTimeBy(REST_MS + PASS_MS + 100)
        testScheduler.runCurrent()
        val lit = bar.noted.filter { it != LevelShine.NONE }
        assertTrue(lit.size in 130..150, "${lit.size} frames lit")
        assertTrue(lit.zipWithNext().all { (a, b) -> b > a }, "the light goes on every frame")
        assertEquals(LevelShine.NONE, bar.noted.last(), "the last frame puts it out")
        // the first frame of a pass only marks the time, the last one puts the light out
        assertEquals(lit.size + 2, bar.display.asked)
        val asked = bar.display.asked
        advanceTimeBy(REST_MS - 300)
        assertEquals(asked, bar.display.asked, "frames asked between the passes")
    }

    @Test
    fun `a bar scrolled away drops its pass at once`() = runTest {
        val bar = Bar(this)
        advanceTimeBy(REST_MS + PASS_MS / 2)
        assertTrue(bar.shine.progress > 0f)
        bar.shine.seen = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(20)
        assertEquals(LevelShine.NONE, bar.shine.progress)
        val asked = bar.display.asked
        advanceTimeBy(10 * SECOND_MS)
        assertEquals(asked, bar.display.asked, "frames asked after it went out of sight")
    }

    @Test
    fun `a growing bar drops the pass and the next waits for it to rest`() = runTest {
        val bar = Bar(this)
        advanceTimeBy(REST_MS + PASS_MS / 2)
        bar.growing.value = true
        Snapshot.sendApplyNotifications()
        advanceTimeBy(20)
        assertEquals(LevelShine.NONE, bar.shine.progress)
        val asked = bar.display.asked
        advanceTimeBy(5 * SECOND_MS)
        assertEquals(asked, bar.display.asked, "frames asked while the bar grows")

        bar.growing.value = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(LevelShineMath.AFTER_GROWTH_MS - 10)
        assertEquals(asked, bar.display.asked, "frames asked before the bar has rested")
        advanceTimeBy(PASS_MS / 2)
        assertTrue(bar.shine.progress > 0f, "the next pass runs")
    }

    private companion object {
        const val BAR_PX = 1_100
        const val FILL = 0.8f
        const val DENSITY = 2.75f

        /** 320 dp filled: a full pass (spec 5.10). */
        const val PASS_MS = LevelShineMath.FULL_PASS_MS.toLong()
        const val REST_MS = (LevelShineMath.PERIOD_MS - LevelShineMath.FULL_PASS_MS).toLong()
    }
}
