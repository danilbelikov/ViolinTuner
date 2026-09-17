package com.example.violintuner.core.domain

import kotlin.math.abs

enum class Zone { IN_TUNE, NEAR, OFF }

enum class Direction {
    SHARP, FLAT;

    companion object {
        /** Null for exactly zero deviation. */
        fun of(cents: Double): Direction? = when {
            cents > 0 -> SHARP
            cents < 0 -> FLAT
            else -> null
        }
    }
}

/** Stateless zone rule (spec 5.2). */
object ZoneClassifier {
    fun classify(cents: Double, config: IntonationConfig): Zone =
        classify(abs(cents), config.toleranceCents, config.nearCents)

    internal fun classify(absCents: Double, inTuneLimit: Double, nearLimit: Double): Zone = when {
        absCents <= inTuneLimit -> Zone.IN_TUNE
        absCents <= nearLimit -> Zone.NEAR
        else -> Zone.OFF
    }
}

/**
 * Zone rule with hysteresis (spec 5.3): a boundary has to be overshot by
 * [IntonationConfig.hysteresisCents] to leave a zone and undershot by the same amount to return.
 */
class ZoneHysteresis(private val config: IntonationConfig) {
    private var current: Zone? = null

    fun update(cents: Double): Zone {
        val h = config.hysteresisCents
        val zone = when (current) {
            null -> ZoneClassifier.classify(cents, config)
            Zone.IN_TUNE ->
                ZoneClassifier.classify(abs(cents), config.toleranceCents + h, config.nearCents + h)
            Zone.NEAR ->
                ZoneClassifier.classify(abs(cents), config.toleranceCents - h, config.nearCents + h)
            Zone.OFF ->
                ZoneClassifier.classify(abs(cents), config.toleranceCents - h, config.nearCents - h)
        }
        current = zone
        return zone
    }

    fun reset() {
        current = null
    }
}
