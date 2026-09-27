package com.violinjourney.app.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwaitSoundTest {
    @Test
    fun `sound at once ends the wait at once`() {
        var asked = 0
        assertTrue(awaitSound({ asked++; true }, limitNanos = 100, sliceNanos = 20) { true })
        assertEquals(1, asked)
    }

    @Test
    fun `no sound waits out the limit in slices`() {
        val slices = ArrayList<Long>()
        assertFalse(awaitSound({ slices += it; false }, limitNanos = 100, sliceNanos = 30) { true })
        assertEquals(listOf(30L, 30L, 30L, 10L), slices)
    }

    @Test
    fun `a collection no longer wanted stops waiting`() {
        var asked = 0
        var alive = true
        assertFalse(awaitSound({ asked++; alive = false; false }, limitNanos = 1_000, sliceNanos = 10) { alive })
        assertEquals(1, asked, "it looked after one slice, not after the limit")
    }
}
