package com.violinjourney.app.core.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class NoteLockTest {
    private val lock = NoteLock(IntonationConfig())

    @Test
    fun `note locks after 100 ms of stability`() {
        assertNull(lock.update(0, 69))
        assertNull(lock.update(99, 69))
        assertEquals(69, lock.update(100, 69))
    }

    @Test
    fun `a change shorter than the lock time keeps the previous note`() {
        lock.update(0, 69)
        lock.update(100, 69)
        assertEquals(69, lock.update(110, 71))
        assertEquals(69, lock.update(190, 71))
        assertEquals(69, lock.update(200, 69))
    }

    @Test
    fun `a sustained change relocks`() {
        lock.update(0, 69)
        lock.update(100, 69)
        lock.update(110, 71)
        assertEquals(71, lock.update(210, 71))
    }

    @Test
    fun `an interrupted candidate starts its timer again`() {
        lock.update(0, 69)
        lock.update(100, 69)
        lock.update(110, 71)
        lock.update(160, 69)
        lock.update(170, 71)
        assertEquals(69, lock.update(230, 71))
        assertEquals(71, lock.update(270, 71))
    }

    @Test
    fun `a pause is not heard and does not reset the candidate`() {
        assertNull(lock.update(0, 69))
        assertNull(lock.update(50, 69))
        lock.pause(60)
        lock.pause(190)
        assertNull(lock.update(200, 69), "the 140 ms of the pause are not heard, the frame after it counts its own 10")
        assertEquals(69, lock.update(240, 69))
    }

    @Test
    fun `a candidate heard in every other frame still locks`() {
        var t = 0L
        while (t < 200) {
            assertNull(lock.update(t, 69))
            lock.pause(t + 10)
            t += 20
        }
        assertEquals(69, lock.update(200, 69), "ten heard steps of 10 ms")
    }

    @Test
    fun `a locked note stays locked across a pause`() {
        lock.update(0, 69)
        lock.update(100, 69)
        lock.pause(390)
        assertEquals(69, lock.update(400, 69))
        assertEquals(69, lock.update(410, 71))
    }

    @Test
    fun `fast passage never locks`() {
        var t = 0L
        for (midi in listOf(69, 71, 73, 74, 76, 78)) {
            repeat(5) {
                assertNull(lock.update(t, midi))
                t += 10
            }
        }
    }
}
