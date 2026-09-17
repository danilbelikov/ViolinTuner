package com.example.violintuner.feature.live

/** Positions on the cents scale as fractions of its length, 0 = left end, 1 = right end. */
object ScaleMath {
    private const val CENTER = 0.5

    /** Marker position; deviations beyond the scale range stick to the ends. */
    fun markerFraction(cents: Double, scale: ScaleSpec): Double =
        (CENTER + cents / (2 * scale.rangeCents)).coerceIn(0.0, 1.0)

    /** Width of the green in-tune segment. */
    fun inTuneFraction(scale: ScaleSpec): Double =
        (scale.toleranceCents / scale.rangeCents).coerceIn(0.0, 1.0)
}
