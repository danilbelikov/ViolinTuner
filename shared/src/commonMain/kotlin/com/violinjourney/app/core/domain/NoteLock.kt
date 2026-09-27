package com.violinjourney.app.core.domain

/**
 * A candidate note becomes the locked note only after it has been heard as the raw candidate for
 * [IntonationConfig.noteLockMs], so fast passages never repaint the screen (spec 5.3). Each frame of the
 * candidate counts its own step since the frame before it; the steps of frames without pitch ([pause]) do not
 * count, and they do not reset what was heard, while another candidate starts it anew. So two octave errors
 * on both sides of a bow change never add up to a lock, and one unclear frame in an attack delays it by a frame.
 */
class NoteLock(private val config: IntonationConfig) {
    var locked: Int? = null
        private set
    var candidate: Int? = null
        private set

    // how long the candidate has been heard, and when the last frame of any kind came
    private var heardMs = 0L
    private var lastFrameMs: Long? = null

    /** Returns the locked note after this frame; it may still be the previous one, or null. */
    fun update(tMs: Long, candidateMidi: Int): Int? {
        if (candidateMidi != candidate) {
            candidate = candidateMidi
            heardMs = 0
        } else {
            lastFrameMs?.let { heardMs += tMs - it }
        }
        lastFrameMs = tMs
        if (candidateMidi != locked && heardMs >= config.noteLockMs) {
            locked = candidateMidi
        }
        return locked
    }

    /** A frame without pitch at [tMs]: its step is not heard, and the next frame of the candidate counts only its own. */
    fun pause(tMs: Long) {
        lastFrameMs = tMs
    }

    fun reset() {
        locked = null
        candidate = null
        heardMs = 0
        lastFrameMs = null
    }
}
