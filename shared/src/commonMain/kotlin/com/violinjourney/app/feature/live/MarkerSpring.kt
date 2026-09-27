package com.violinjourney.app.feature.live

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The spring the scale marker rides on (spec 5.3), advanced frame by frame. The pitch gives
 * the marker a new target more often than the screen draws, and an animation restarted on
 * every target never gets past its first frame, which moves nothing: the marker used to stand
 * still while the pitch moved and catch up only in the pauses. A spring that is stepped has no
 * start to restart. Pure; positions are fractions of the scale.
 */
class MarkerSpring(private val dampingRatio: Float, private val stiffness: Float, start: Float) {
    var position = start
        private set
    private var velocity = 0f

    /** Moves towards [target] for [elapsedMs]; long frames are cut into short steps so the spring stays stable. */
    fun advance(target: Float, elapsedMs: Float) {
        if (elapsedMs <= 0f) return
        val steps = ceil(elapsedMs / MAX_STEP_MS).toInt()
        val dt = elapsedMs / steps / MS_PER_SECOND
        val damping = 2f * dampingRatio * sqrt(stiffness)
        repeat(steps) {
            // semi-implicit Euler: velocity first, then position with the new velocity
            velocity += (stiffness * (target - position) - damping * velocity) * dt
            position += velocity * dt
        }
    }

    fun snapTo(target: Float) {
        position = target
        velocity = 0f
    }

    fun isAtRest(target: Float): Boolean = abs(target - position) < REST_DISTANCE && abs(velocity) < REST_VELOCITY

    private companion object {
        const val MAX_STEP_MS = 4f
        const val MS_PER_SECOND = 1_000f
        const val REST_DISTANCE = 0.0005f
        const val REST_VELOCITY = 0.001f
    }
}

/**
 * Whether the marker of the scale needs frames, and when it jumps instead of riding the spring (spec 3.6, 5.3):
 * after a silence it shows up where the pitch is, not travelling from its old place. Every target is shown to
 * [follow] — the silences too, even while the spring sleeps at rest — so none of them is missed. Pure.
 */
class MarkerTrack(private val spring: MarkerSpring, visible: Boolean) {
    private var visible = visible

    /**
     * Takes the marker's target, null in silence. True when there is something to show: the marker has just
     * jumped to [target] after a silence, or the spring still has a way to go; false in silence and at rest.
     */
    fun follow(target: Float?): Boolean {
        if (target == null) {
            visible = false
            return false
        }
        if (!visible) {
            spring.snapTo(target)
            visible = true
            return true
        }
        return !spring.isAtRest(target)
    }
}
