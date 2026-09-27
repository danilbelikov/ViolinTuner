package com.violinjourney.app.feature.repertoire.stand

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

    /**
     * How far the scaled layer of the sheet — the part of the stand the sheet is shown in, [size] long, centred on the area
     * of gestures — may be dragged: until its edge comes to where it stands at 1×, the margin of the stand, and no further.
     */
    fun clampOffset(offset: Float, scale: Float, size: Float): Float {
        val limit = (scale - 1f).coerceAtLeast(0f) * size / 2f
        return offset.coerceIn(-limit, limit)
    }

    /**
     * Where the sheet has to move so that the point under a double tap stays under the finger once scaled. The layer is
     * scaled about its centre, which is the centre of the area of gestures ([area] long, where [tap] is measured); it is
     * [layer] long itself, and the move is held to it ([clampOffset]). [tap], [area] and [layer] run along one axis.
     */
    fun offsetToKeep(tap: Float, area: Float, layer: Float, scale: Float): Float = clampOffset((area / 2f - tap) * (scale - 1f), scale, layer)

    /**
     * The offset after one step of a pinch: the point under the fingers' previous centroid ([focus], measured from the
     * centre of the area, which is the centre of the scaled layer) stays under them while the scale changes by
     * [zoomChange], and then follows the fingers by [pan]. Along one axis; unclamped.
     */
    fun offsetAfterPinch(offset: Float, focus: Float, zoomChange: Float, pan: Float): Float = focus * (1f - zoomChange) + zoomChange * offset + pan

    /**
     * The edge to light when the pager comes to rest on [toId] after resting on [fromId] (spec 3.15): the side the sheet
     * came in from — [StandZone.NEXT] forwards, [StandZone.PREVIOUS] back. Nothing when a tap turned the page (the tap
     * has lit its edge already, [byTap]), when there was no page before (the first rest), when the page is the same, and
     * when [fromId] is no longer among [pageIds]: that page was deleted, and the one on its place did not turn in.
     */
    fun edgeOfSettle(pageIds: List<Long>, fromId: Long?, toId: Long?, byTap: Boolean): StandZone? {
        if (byTap || fromId == null || toId == null || fromId == toId) return null
        val from = pageIds.indexOf(fromId)
        val to = pageIds.indexOf(toId)
        if (from < 0 || to < 0) return null
        return if (to > from) StandZone.NEXT else StandZone.PREVIOUS
    }

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
