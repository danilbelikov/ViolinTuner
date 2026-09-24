package com.violinjourney.app.feature.practice.components

/** One pass of the shine along the level bar: how wide the band is, how far it travels, for how long. All in dp and ms. */
data class ShinePass(val bandDp: Float, val travelDp: Float, val durationMs: Int) {
    /** Left edge of the band at [progress] 0…1 (already eased): from wholly before the fill to wholly past it. */
    fun bandLeftDp(progress: Float): Float = -bandDp + travelDp * progress.coerceIn(0f, 1f)
}

/** The rules of the shine (spec 5.10, handoff `Иконки и жизнь`, 16a–16b), free of Compose. */
object LevelShineMath {
    const val PERIOD_MS = 4_000
    const val FULL_PASS_MS = 1_200
    const val MIN_PASS_MS = 500

    /** After the bar has grown or a level has turned, the shine waits this long. */
    const val AFTER_GROWTH_MS = 1_500L

    const val BAND_DP = 56f
    const val MIN_BAND_DP = 12f

    /** On a short fill the band is this share of it — but never thinner than [MIN_BAND_DP]. */
    const val BAND_SHARE = 0.6f

    /** The travel the full duration is meant for: 62 % of a 380-dp bar plus the band in and out, as drawn in 16a. */
    const val REFERENCE_TRAVEL_DP = 348f

    /** Null when the fill is too short to carry a shine — or empty. */
    fun passOf(fillDp: Float): ShinePass? {
        if (fillDp < MIN_BAND_DP) return null
        val band = (fillDp * BAND_SHARE).coerceIn(MIN_BAND_DP, BAND_DP)
        val travel = fillDp + band
        // The light moves at one speed: a shorter way takes less time, within reason.
        val duration = (FULL_PASS_MS * travel / REFERENCE_TRAVEL_DP).toInt().coerceIn(MIN_PASS_MS, FULL_PASS_MS)
        return ShinePass(band, travel, duration)
    }

    /** The rest between two passes: a band that never stops reads as "loading". */
    fun pauseAfter(pass: ShinePass): Long = (PERIOD_MS - pass.durationMs).toLong()
}
