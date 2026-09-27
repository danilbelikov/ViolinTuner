package com.violinjourney.app.feature.live

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class MarkerTrackTest {
    private val spring = MarkerSpring(dampingRatio = 0.8f, stiffness = 600f, start = 0.3f)

    @Test
    fun `after a silence the marker jumps to the new pitch even when the spring was asleep`() {
        val track = MarkerTrack(spring, visible = true)
        assertFalse(track.follow(0.3f), "at rest on its target: no frames")
        assertFalse(track.follow(null), "silence")
        assertTrue(track.follow(0.8f))
        assertEquals(0.8f, spring.position, "snapped, not riding the spring from 0.3")
        assertFalse(track.follow(0.8f))
    }

    @Test
    fun `while shown a new target rides the spring`() {
        val track = MarkerTrack(spring, visible = true)
        assertTrue(track.follow(0.6f))
        assertEquals(0.3f, spring.position, "the spring moves only when it is advanced")
    }

    @Test
    fun `the first target of a hidden marker snaps`() {
        val track = MarkerTrack(spring, visible = false)
        assertTrue(track.follow(0.7f))
        assertEquals(0.7f, spring.position)
    }
}
