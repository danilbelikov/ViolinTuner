package com.violinjourney.app.core.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The cents as a number a person can read (spec 5.8). The smoothed cents still move many times
 * a second; shown as they are, the digits would flicker like the hold arc they came to replace.
 * So the shown value changes at most every [IntonationConfig.centsReadoutIntervalMs] and only
 * when the pitch has moved a whole [IntonationConfig.centsReadoutDeadbandCents] away from what
 * is on screen — 4.49 ↔ 4.51 does not toggle "4" and "5". A new note shows at once.
 * Time comes from the frames; not thread-safe.
 */
class CentsReadout(private val config: IntonationConfig) {
    private var shown: Int? = null
    private var shownFromCents = 0.0
    private var shownAtTMs = 0L
    private var shownMidi = 0

    fun update(tMs: Long, midi: Int, cents: Double): Int {
        val current = shown
        val due = tMs - shownAtTMs >= config.centsReadoutIntervalMs
        val moved = abs(cents - shownFromCents) >= config.centsReadoutDeadbandCents
        if (current != null && midi == shownMidi && !(due && moved)) return current
        return cents.roundToInt().coerceIn(-config.centsReadoutMax, config.centsReadoutMax).also {
            shown = it
            shownFromCents = cents
            shownAtTMs = tMs
            shownMidi = midi
        }
    }

    /** Silence: the next note starts clean. */
    fun reset() {
        shown = null
    }
}
