package com.violinjourney.app.feature.live.components

/**
 * The sizes of the word and the cents of Live at one step (spec 3.14, 5.29 R6): the word, the cents, the sign before the word — the
 * arrow of «выше» and «ниже», the dot of «в строе» — and the gap between the three; sp for the words, dp for the rest.
 */
data class StatusStep(val wordSp: Float, val centsSp: Float, val arrowDp: Float, val dotDp: Float, val gapDp: Float)

/**
 * How large the word and the cents stand in the room they get (spec 5.29 R6, «Шкала»): upright they are full size, or compact with a
 * small ring (the ring gives them their row); in the right column of landscape they take the room the column leaves them — the full
 * size where it stands, the compact one, then a point smaller at a time, the cents, the sign and the gap in the proportion of the
 * compact size, down to [LEAST_WORD_SP]; where not even that stands, the two give way whole — their place kept, the glow of the ring,
 * the scale and its slider still say the zone — and a glyph is never clipped. Pure; the lines are measured by the caller.
 */
object StatusFit {
    /** The full size (handoff 12c1): the word 28 sp, the cents 36, the arrow 40, the dot 18, 14 between. */
    val FULL = StatusStep(wordSp = 28f, centsSp = 36f, arrowDp = 40f, dotDp = 18f, gapDp = 14f)

    /** The compact size — a small ring upright, a low column lying down: 24, 30, 32, 16, 12. */
    val COMPACT = StatusStep(wordSp = 24f, centsSp = 30f, arrowDp = 32f, dotDp = 16f, gapDp = 12f)

    /** Below the compact size the word steps down a point at a time… */
    const val STEP_SP = 1f

    /**
     * …to the letters of the strings of «Настройка» (20 sp, spec 5.29 R6), and no further: smaller, the word would be a caption beside
     * the plate of the status line, not a thing the corner of the eye reads.
     */
    const val LEAST_WORD_SP = 20f

    /** The steps, largest first: full, compact, 23, 22, 21, 20. */
    val steps: List<StatusStep> = buildList {
        add(FULL)
        add(COMPACT)
        var word = COMPACT.wordSp - STEP_SP
        while (word >= LEAST_WORD_SP) {
            add(scaled(word / COMPACT.wordSp))
            word -= STEP_SP
        }
    }

    /**
     * The largest of [steps] whose row stands in a room [roomHeight] high and [roomWidth] wide — [heightOf] the row of a step (its
     * tallest line, whole, or its sign), [widthOf] it on one line with the room of the widest cents; units are the caller's. Null —
     * none stands: the word and the cents give way.
     */
    fun fit(roomHeight: Float, roomWidth: Float, heightOf: (StatusStep) -> Float, widthOf: (StatusStep) -> Float): StatusStep? =
        steps.firstOrNull { heightOf(it) <= roomHeight && widthOf(it) <= roomWidth }

    private fun scaled(k: Float) = StatusStep(
        wordSp = COMPACT.wordSp * k,
        centsSp = COMPACT.centsSp * k,
        arrowDp = COMPACT.arrowDp * k,
        dotDp = COMPACT.dotDp * k,
        gapDp = COMPACT.gapDp * k,
    )
}
