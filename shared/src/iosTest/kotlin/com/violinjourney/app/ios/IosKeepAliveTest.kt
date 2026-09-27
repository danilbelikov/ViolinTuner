package com.violinjourney.app.ios

import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.ui.components.ScreenOnHolds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * A copy on iOS keeps the screen lit and asks for background time again whenever the app comes back (spec 3.20); a take
 * holds background time from its start until it is saved, without the screen (spec 3.9, 3.32).
 */
class IosKeepAliveTest {
    private class FakeTime : BackgroundTime {
        var begun = 0
        val ended = mutableListOf<ULong>()
        var expire: (() -> Unit)? = null

        override fun begin(onExpired: () -> Unit): ULong {
            begun++
            expire = onExpired
            return begun.toULong()
        }

        override fun end(task: ULong) {
            ended += task
        }

        override val none: ULong = 0u
    }

    private val time = FakeTime()
    private val lit = mutableListOf<Boolean>()
    private val screen = ScreenOnHolds { lit += it }
    private val turns = KeepAliveTurns(time, screen)

    @Test
    fun `the screen stays lit from the start of a copy to its end however often it is started`() {
        turns.start()
        turns.start()
        assertTrue(screen.held)
        assertEquals(1, time.begun, "one stint of background time")
        turns.stop()
        assertFalse(screen.held)
        assertEquals(listOf(1uL), time.ended)
        turns.stop()
        assertFalse(screen.held)
        assertEquals(listOf(1uL), time.ended, "a second stop gives back nothing")
    }

    @Test
    fun `a copy whose background time ran out gets a new stint when the app is back`() {
        turns.start()
        time.expire!!()
        assertEquals(listOf(1uL), time.ended, "the stint that ran out is given back at once")
        turns.becameActive()
        assertEquals(2, time.begun)
        turns.stop()
        assertEquals(listOf(1uL, 2uL), time.ended)
        assertFalse(screen.held)
    }

    @Test
    fun `a stint that has not run out is not asked for twice`() {
        turns.start()
        turns.becameActive()
        assertEquals(1, time.begun)
    }

    @Test
    fun `a finished copy asks for nothing when the app is back`() {
        turns.start()
        turns.stop()
        turns.becameActive()
        assertEquals(1, time.begun)
        assertFalse(screen.held)
    }

    @Test
    fun `a take holds background time from its start until it is saved and leaves the screen alone`() = runTest {
        val watch = RecordingWatch()
        val follower = backgroundScope.launch { IosTakeKeepAlive.follow(watch, time) }
        runCurrent()
        assertEquals(0, time.begun, "no take, no stint")

        watch.set(true)
        runCurrent()
        assertEquals(1, time.begun)
        assertFalse(screen.held)
        assertTrue(time.ended.isEmpty(), "held while the take is finished, not only while it records")

        watch.set(false)
        runCurrent()
        assertEquals(listOf(1uL), time.ended, "given back once the take is in the database")

        // the graph goes (a copy restored) in the middle of the next take: its stint goes with it
        watch.set(true)
        runCurrent()
        follower.cancel()
        runCurrent()
        assertEquals(listOf(1uL, 2uL), time.ended)
    }

    @Test
    fun `a stint that runs out after the copy ended gives back only itself`() {
        turns.start()
        val first = time.expire!!
        turns.stop()
        turns.start()
        // the old stint's clock runs out late: the new stint stays
        first()
        turns.becameActive()
        assertEquals(2, time.begun, "the stint of the second copy is still held")
        assertEquals(listOf(1uL, 1uL), time.ended)
    }
}
