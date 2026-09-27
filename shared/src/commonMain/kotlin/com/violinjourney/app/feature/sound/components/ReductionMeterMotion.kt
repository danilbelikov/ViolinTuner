package com.violinjourney.app.feature.sound.components

/**
 * The motion of «Сейчас сжимает» (spec 5.11), stepped by frame times: the bar — the share of [fullDb] the
 * compressor takes off — goes up at once and falls in ~300 ms ([MeterFall]), like the output meter; the number
 * follows the reading ten times a second, so it reads as a number, not a flicker. Pure; the screen draws what it
 * says ([runMeter]) and sleeps while [atRest].
 */
internal class ReductionMeterMotion(private val fullDb: Double) : MeterMotion<Double> {
    /** How full the bar is, 0…1. */
    var level = 0f
        private set

    /** Decibels taken off, as the words say them; 0 — nothing. */
    var number = 0.0
        private set

    private var lastFrameMs: Long? = null
    private var numberAtMs: Long? = null

    override fun step(nowMs: Long, reading: Double?) {
        val target = ((reading ?: 0.0) / fullDb).toFloat().coerceIn(0f, 1f)
        val elapsedMs = lastFrameMs?.let { nowMs - it } ?: 0L
        lastFrameMs = nowMs
        level = MeterFall.next(level, target, elapsedMs)
        val shownAt = numberAtMs
        if (shownAt == null || nowMs - shownAt >= NUMBER_EVERY_MS) {
            numberAtMs = nowMs
            number = reading ?: 0.0
        }
    }

    override fun atRest(reading: Double?): Boolean = reading == null && level <= 0f

    override fun rest() {
        lastFrameMs = null
        numberAtMs = null
        number = 0.0
    }

    companion object {
        /** Ten numbers a second, as before the bar was stepped by frames. */
        const val NUMBER_EVERY_MS = 100L
    }
}
