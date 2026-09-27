package com.violinjourney.app.feature.sound.components

import com.violinjourney.app.core.audio.fx.SoundMeters

/**
 * The motion of the output meter of the mini player, stepped by frame times: the level goes up at
 * once and the whole bar falls in ~300 ms ([MeterFall], spec 5.11), the limiter mark stays lit a
 * while after each time the limiter worked, the number is refreshed a few times a second. Pure; the
 * screen only draws what it says, thirty times a second ([runMeter]), and sleeps while [atRest].
 *
 * "Never yet" is null, not a sentinel time: `now - Long.MIN_VALUE` wrapped around to a negative
 * number, which lit «ограничитель» from the first frame on and kept the frames going forever.
 */
internal class OutputMeterMotion : MeterMotion<SoundMeters> {
    /** How full the bar is, 0…1. */
    var level = 0f
        private set

    /** The limiter worked less than [LIMITER_LIT_MS] ago: the mark is lit and the word replaces the number. */
    var lit = false
        private set

    /** The peak to write out, dBFS; null — nothing to show. */
    var number: Double? = null
        private set

    private var lastFrameMs: Long? = null
    private var limitedAtMs: Long? = null
    private var numberAtMs: Long? = null

    override fun step(nowMs: Long, reading: SoundMeters?) {
        val target = reading?.let { ((it.outputPeakDb - FLOOR_DB) / -FLOOR_DB).toFloat().coerceIn(0f, 1f) } ?: 0f
        val elapsedMs = lastFrameMs?.let { nowMs - it } ?: 0L
        lastFrameMs = nowMs
        level = MeterFall.next(level, target, elapsedMs)
        if (reading?.limiting == true) limitedAtMs = nowMs
        lit = limitedAtMs?.let { nowMs - it < LIMITER_LIT_MS } ?: false
        val shownAt = numberAtMs
        if (shownAt == null || nowMs - shownAt >= NUMBER_EVERY_MS) {
            numberAtMs = nowMs
            number = reading?.outputPeakDb
        }
    }

    override fun atRest(reading: SoundMeters?): Boolean = reading == null && level <= 0f && !lit

    override fun rest() {
        lastFrameMs = null
        limitedAtMs = null
        numberAtMs = null
        number = null
    }

    companion object {
        /** The bottom of the bar, dBFS: quieter reads as nothing. */
        const val FLOOR_DB = -60.0

        /** Spec 5.11: the mark is lit for 600 ms per event. */
        const val LIMITER_LIT_MS = 600L
        const val NUMBER_EVERY_MS = 100L
    }
}
