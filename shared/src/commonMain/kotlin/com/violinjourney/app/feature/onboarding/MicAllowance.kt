package com.violinjourney.app.feature.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the microphone is allowed, for the hint of «Микрофон» (spec 3.36.8): read again on every step and on every return to the
 * screen — the permission may come from the system settings while the onboarding waits — but not on the return from the system's own
 * question while it is asked: its answer moves the step on, and that step reads it. So the hint never goes from the face of
 * «Микрофон» before the face is leaving (the review of stage 120): on Android the answer comes before the return to the screen, on
 * iOS it may come after it. Pure but for [check]; with a test.
 */
@Stable
internal class MicAllowance(private val check: () -> Boolean) {
    var allowed by mutableStateOf(check())
        private set

    private var asking = false

    /** The system is asked: its dialog is up, and the return from it is no reason to look. */
    fun asked() {
        asking = true
    }

    /** The system answered; the step the answer moves to reads the permission. */
    fun answered() {
        asking = false
    }

    /** Back on the screen: the permission may have come from the settings — unless the system's question is what was left. */
    fun resumed() {
        if (!asking) allowed = check()
    }

    /** A step came into view. */
    fun stepShown() {
        allowed = check()
    }
}
