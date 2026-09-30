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

    /**
     * The rows of portrait Live around the ring, top to bottom (spec 5.29 R6): the parts, and the rows they make with the air above
     * them. The ring gets the height they leave ([ringBlockHeight]); the sizes of the screen (`LiveDimens`) are taken from here, so
     * what this model says of the ring is what the screen draws.
     */
    object PortraitRows {
        /** Air above the top row, the string row and the plate of the status line, and above and below the keys. */
        const val AIR = 12f

        /** The row of the switcher and the gear: the touch of the gear holds its height. */
        const val TOP_ROW = 48f
        const val STRING_BUTTON = 58f
        const val STATUS_PLATE = 36f

        /** The row of the record key: the key of 72 on its hard shadow of 4; the key of 76 of R6, whose soft shadow takes no room, the same. */
        const val KEY_ROW = 76f

        /** The word and its cents, [WORD_GAP] under the ring. */
        const val WORD_ROW = 48f
        const val WORD_GAP = 20f

        /** The scale of «Настройка», [SCALE_GAP] under the word and the cents. */
        const val SCALE_ROW = 36f
        const val SCALE_GAP = 8f

        /** The switcher and the gear under their air: 60. */
        const val TOP = AIR + TOP_ROW

        /** The strings of «Настройка» under their air: 70 (the pegs of 3.27 were 76). */
        const val STRINGS = AIR + STRING_BUTTON

        /** The line of the status under its air: 48 (was 40, a plate of 26 in a line of 28). */
        const val STATUS = AIR + STATUS_PLATE

        /** The keys with the air above and below them: 100. */
        const val KEYS = AIR + KEY_ROW + AIR

        /** What stays under the ring inside its own place: the word and the cents with the gap above them, 68. */
        const val WORD = WORD_GAP + WORD_ROW

        /** The scale under the word in «Настройка», 44: inside the place of the ring, no longer over the keys. */
        const val SCALE = SCALE_GAP + SCALE_ROW
    }

    /**
     * The right column of landscape Live (spec 3.36.6, 5.29 R6): [padding] above and below it, [gap] between its rows, [scale] — the
     * height of the scale of «Настройка».
     */
    data class LandscapeColumn(val padding: Float, val gap: Float, val scale: Float)

    private val LANDSCAPE_COLUMN = LandscapeColumn(padding = 16f, gap = 8f, scale = PortraitRows.SCALE_ROW)
    private val LOW_LANDSCAPE_COLUMN = LandscapeColumn(padding = 8f, gap = 4f, scale = 28f)

    /** The air above the keys in the right column of landscape. */
    const val LANDSCAPE_KEYS_AIR = 4f

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

    /**
     * The height of the place of the ring in portrait (spec 5.29 R6): what the rows of [PortraitRows] leave of Live [liveHeightDp]
     * high — the strings only in «Настройка» ([tuning]). The scale is inside this place ([reservedUnderRing]).
     */
    fun ringBlockHeight(liveHeightDp: Float, tuning: Boolean): Float =
        liveHeightDp - PortraitRows.TOP - (if (tuning) PortraitRows.STRINGS else 0f) - PortraitRows.STATUS - PortraitRows.KEYS

    /**
     * What stays under the ring inside its place (spec 3.36.6: the scale right under the word and the cents): the word, and the
     * scale as far as it is shown — [scaleShown] 0…1 follows the mode in 200 ms, so the ring changes as smoothly as before, when the
     * scale unfolded under its place. At the same height of the place the ring is the same wherever the scale stands.
     */
    fun reservedUnderRing(scaleShown: Float): Float = PortraitRows.WORD + PortraitRows.SCALE * scaleShown.coerceIn(0f, 1f)

    /**
     * The right column of landscape in a window [heightDp] high (spec 5.29 R6): padding 16, gaps 8 and the scale 36 — unless that
     * column would leave the word and the cents of «Настройка» less than their whole row ([PortraitRows.WORD_ROW], 48): then the
     * window is low, and the column takes padding 8, gaps 4 and the scale 28. In both modes, so the switcher does not jump when the
     * mode changes. Low is below 378: 640 × 360 (Live 336) and a phone 393 wide lying down with its buttons at the side (Live ≈ 368)
     * are low, 892 × 412 with the buttons at the side (Live 388) is not — and a higher window never leaves the word less of its whole
     * row than a lower one does.
     */
    fun landscapeColumn(heightDp: Float): LandscapeColumn =
        if (wordRoom(LANDSCAPE_COLUMN, heightDp, tuning = true) < PortraitRows.WORD_ROW) LOW_LANDSCAPE_COLUMN else LANDSCAPE_COLUMN

    /**
     * The height the word and the cents get in the right column of landscape [heightDp] high: what the top row, the strings of
     * «Настройка» ([tuning]), the plate of the status line, the scale, the keys and the gaps between them leave (no recording).
     * 640 × 360 (Live 336) in «Настройка» — 50; before R6 it was 3.
     */
    fun landscapeWordRoom(heightDp: Float, tuning: Boolean): Float = wordRoom(landscapeColumn(heightDp), heightDp, tuning)

    private fun wordRoom(column: LandscapeColumn, heightDp: Float, tuning: Boolean): Float {
        // the top row, the plate and the keys, a gap before each of the last two
        var taken = column.padding * 2 + PortraitRows.TOP_ROW + column.gap + PortraitRows.STATUS_PLATE + column.gap +
            column.gap + LANDSCAPE_KEYS_AIR + PortraitRows.KEY_ROW
        // the strings a gap under the top row, the scale a gap under the word
        if (tuning) taken += column.gap + PortraitRows.STRING_BUTTON + column.gap + column.scale
        return heightDp - taken
    }
}
