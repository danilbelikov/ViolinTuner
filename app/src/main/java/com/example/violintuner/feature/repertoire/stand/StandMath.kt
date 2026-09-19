package com.example.violintuner.feature.repertoire.stand

/** What a tap on the stand means. */
enum class StandZone { PREVIOUS, NEXT, PANEL }

/** Geometry and rules of the music stand (spec 3.15, 5.9), free of Compose. */
object StandMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    const val DOUBLE_TAP_SCALE = 2f

    /** Pinching back to "almost one" counts as one: a page left at 1.003 would never turn again. */
    private const val ZOOMED_FROM = 1.02f

    /** The left and the right third turn pages (spec 5.9). */
    private const val ZONE_FRACTION = 1f / 3f

    fun isZoomed(scale: Float): Boolean = scale >= ZOOMED_FROM

    /** A zoomed page does not turn: there a tap near the edge is aimed at the notes, not at the next sheet. */
    fun zoneOf(x: Float, width: Float, zoomed: Boolean): StandZone = when {
        zoomed || width <= 0f -> StandZone.PANEL
        x < width * ZONE_FRACTION -> StandZone.PREVIOUS
        x > width * (1f - ZONE_FRACTION) -> StandZone.NEXT
        else -> StandZone.PANEL
    }

    /** The page a tap in [zone] leads to; null at the first and the last sheet — the page bounces instead. */
    fun target(current: Int, count: Int, zone: StandZone): Int? = when (zone) {
        StandZone.PREVIOUS -> (current - 1).takeIf { it >= 0 }
        StandZone.NEXT -> (current + 1).takeIf { it < count }
        StandZone.PANEL -> null
    }

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** How far a sheet of [size] scaled about its centre may be dragged before its edge would come off the screen's. */
    fun clampOffset(offset: Float, scale: Float, size: Float): Float {
        val limit = (scale - 1f).coerceAtLeast(0f) * size / 2f
        return offset.coerceIn(-limit, limit)
    }

    /**
     * Where the sheet has to move so that the point under a double tap stays under the finger
     * once scaled about the centre. [tap] and [size] run along the same axis.
     */
    fun offsetToKeep(tap: Float, size: Float, scale: Float): Float = clampOffset((size / 2f - tap) * (scale - 1f), scale, size)

    /**
     * The largest power of two a picture [sourceWidth] wide can be decoded down by and still be
     * no narrower than [wantedWidth]: a page is 2560 px for the sake of zooming, the unzoomed
     * sheet needs half of that.
     */
    fun sampleSize(sourceWidth: Int, wantedWidth: Int): Int {
        if (sourceWidth <= 0 || wantedWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= wantedWidth) sample *= 2
        return sample
    }
}
