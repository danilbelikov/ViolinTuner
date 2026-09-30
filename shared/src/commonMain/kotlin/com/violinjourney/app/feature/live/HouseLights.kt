package com.violinjourney.app.feature.live

import com.violinjourney.app.feature.live.components.LiveMotion

/**
 * When the light of Live comes back (spec 3.27, 5.20): [LiveMotion.LIGHT_ON_AFTER_MS] after the last note or take
 * ended ([LiveState.quietSinceNanos]), counted across a rotation and a trip to another screen — a return after
 * that finds it on, a return before it waits for the rest. Pure: the clock is given.
 */
object HouseLights {
    /** How long the light stays out from [nowNanos] on; 0 — it is on. Nothing ended yet, or a moment ahead of now, is on. */
    fun msStillOut(quietSinceNanos: Long?, nowNanos: Long, afterMs: Long = LiveMotion.LIGHT_ON_AFTER_MS): Long {
        if (quietSinceNanos == null || nowNanos < quietSinceNanos) return 0
        val quietMs = (nowNanos - quietSinceNanos) / NANOS_PER_MS
        return (afterMs - quietMs).coerceAtLeast(0)
    }

    /**
     * A note sounds or a take records: the light is out (spec 3.27, 5.20), and on Live only the ring and the dot of a take move — the
     * living «Начать занятие» of «Сначала — занятие» stands still then (3.36.6). One rule for the light and for the lights of the button.
     */
    fun playing(state: LiveState): Boolean = state.signal is LiveSignal.Sounding || state.recording != null

    private const val NANOS_PER_MS = 1_000_000L
}
