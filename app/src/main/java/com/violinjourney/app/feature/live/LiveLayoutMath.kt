package com.violinjourney.app.feature.live

/** Pure sizing rules of the Live screen. All values are in dp. */
object LiveLayoutMath {
    /** Ring diameters of the handoff: portrait play, portrait tuning, landscape, no-mic state. */
    const val RING_PORTRAIT = 300f
    const val RING_TUNING = 260f

    /** 260, not the 320 of v1: the halo reaches 1.46 R and has to fit the ring panel. */
    const val RING_LANDSCAPE = 260f
    const val RING_NO_MIC = 200f

    /** The 176 sp note of the handoff is drawn for this ring; smaller rings scale it down. */
    private const val NOTE_REFERENCE_RING = 300f
    private const val MIN_RING = 96f

    fun isLandscape(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp

    fun designRing(landscape: Boolean, tuning: Boolean, noMic: Boolean): Float = when {
        noMic -> RING_NO_MIC
        landscape -> RING_LANDSCAPE
        tuning -> RING_TUNING
        else -> RING_PORTRAIT
    }

    /**
     * The design diameter, shrunk to what is free: [reservedHeightDp] is what has to stay
     * visible under the ring inside the same block (status row, spacing).
     */
    fun ringDiameter(
        designDp: Float,
        availableWidthDp: Float,
        availableHeightDp: Float,
        reservedHeightDp: Float,
    ): Float = minOf(designDp, availableWidthDp, availableHeightDp - reservedHeightDp).coerceAtLeast(MIN_RING)

    /** Scale of the note relative to the handoff size; never enlarged, not even in the 320 ring. */
    fun noteScale(ringDp: Float): Float = (ringDp / NOTE_REFERENCE_RING).coerceAtMost(1f)
}
