package com.violinjourney.app.core.domain

/**
 * A candidate note becomes the locked note only after it has been the raw candidate for
 * [IntonationConfig.noteLockMs] without interruption, so fast passages never repaint the screen.
 */
class NoteLock(private val config: IntonationConfig) {
    var locked: Int? = null
        private set
    var candidate: Int? = null
        private set
    private var candidateSinceMs = 0L

    /** Returns the locked note after this frame; it may still be the previous one, or null. */
    fun update(tMs: Long, candidateMidi: Int): Int? {
        if (candidateMidi != candidate) {
            candidate = candidateMidi
            candidateSinceMs = tMs
        }
        if (candidateMidi != locked && tMs - candidateSinceMs >= config.noteLockMs) {
            locked = candidateMidi
        }
        return locked
    }

    fun reset() {
        locked = null
        candidate = null
    }
}
