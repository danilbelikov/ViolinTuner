package com.violinjourney.app.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The permission for the hint of «Микрофон» (spec 3.36.8; the review of stage 120): read on every step and every return to the screen,
 * but not on the return from the system's own question — the step its answer moves to reads it, so the hint never goes from the face
 * of «Микрофон» before that face is leaving, whichever comes first on the platform.
 */
class MicAllowanceTest {
    private var granted = false
    private val mic = MicAllowance { granted }

    @Test
    fun `the return from the system's question does not read the permission the step does`() {
        assertFalse(mic.allowed)
        mic.asked()
        granted = true
        // iOS: back on the screen before the answer comes
        mic.resumed()
        assertFalse(mic.allowed, "the face of «Микрофон» keeps its hint while it stands")
        mic.answered()
        mic.stepShown()
        assertTrue(mic.allowed, "«Эталон» reads it")
    }

    @Test
    fun `the answer before the return lets the return read it`() {
        // Android: the answer comes first, then the screen is back — in one frame with the new step
        mic.asked()
        granted = true
        mic.answered()
        mic.resumed()
        assertTrue(mic.allowed)
    }

    @Test
    fun `allowed in the settings while the step waits the return reads it`() {
        granted = true
        mic.resumed()
        assertTrue(mic.allowed)
        granted = false
        mic.stepShown()
        assertFalse(mic.allowed, "taken back — read again on the next step")
    }
}
