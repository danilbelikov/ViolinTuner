package com.violinjourney.app.core.ui.components

/**
 * The bar of the green zone in the cards of the tolerance of the setup (spec 3.36.8, 5.29 R8): a track of [TRACK_DP] that stands for
 * ±[SCALE_CENTS] cents around the note, and on it, in the middle, the zone of the card — as wide as its tolerance (±12, ±8, ±3: about
 * 59, 40 and 15 dp), so the difference is seen before the first note is played. Where the bar, the number and the names of the three
 * cards stand ([fit]). Pure, with a test.
 */
internal object ToleranceBarMath {
    /** The track of the bar, dp. */
    const val TRACK_DP = 84f

    /** The track stands for ±17 cents: ±12 then fills most of it, ±3 a sliver. */
    const val SCALE_CENTS = 17f

    /** The names of the cards: 17 sp, and where the widest has no room, 0.5 sp smaller together — at most down to 13 (5.29 R8). */
    const val NAME_SP = 17f
    const val NAME_LEAST_SP = 13f

    /** How wide the zone of ±[cents] is on the track, dp — never wider than the track, never negative. */
    fun segmentDp(cents: Int): Float = (TRACK_DP * cents / SCALE_CENTS).coerceIn(0f, TRACK_DP)

    /**
     * Whether the bar stands beside the words of a card or goes under its caption: beside where the widest name of the three cards
     * stays on one line in a row [rowWidth] wide that also holds the [fixed] parts — the radio, the gaps, the bar and the number; on
     * the edge — beside. One decision for the three cards, so their bars never stand in different places. The units are the caller's,
     * one and the same.
     */
    fun beside(rowWidth: Float, widestName: Float, fixed: Float): Boolean = widestName <= rowWidth - fixed

    /**
     * The widths of a card's row that its words do not decide, in the caller's units: [lead] — the radio and the gap after it, [gap] —
     * the gap before the bar or the number, [bar] — the bar, [slack] — what a name that fits only by a hair is not trusted with.
     */
    class Parts(val lead: Float, val gap: Float, val bar: Float, val slack: Float)

    /**
     * Where the bar and the number of the three cards stand and how large their names are — one decision for the three (spec 3.36.8,
     * 5.29 R8), in a row [row] wide (the card less its fields), no word broken:
     * 1. the bar beside the words, the names at 17 sp — where the widest name and the widest word of a caption stand beside it;
     * 2. else the bar under the caption and «±8 ц» still at the end — the names 0.5 sp smaller together, down to 13, as far as the
     *    widest needs (a phone of 360, a large font, fr «Intermédiaire», the column of words lying);
     * 3. else — not even 13 sp keeps the widest name whole there (320 dp at the font 1.5, fr; the review of stage 120), or a word of a
     *    caption does not fit — the number goes under the caption too, beside the bar where both fit, else under it, and the names get
     *    the whole row less the radio, at the largest size they fit.
     * [widestNumber] — the widest «±12 ц» of the three; [widestCaptionWord] — the widest single word of the three captions (they
     * wrap at their spaces); [widestNameAt] — the widest name at a size, sp.
     */
    fun fit(row: Float, parts: Parts, widestNumber: Float, widestCaptionWord: Float, widestNameAt: (sizeSp: Float) -> Float): CardsFit {
        // the column of the words with nothing after it, with the number at the end, and with the bar before the number
        val words = row - parts.lead - parts.slack
        val beforeNumber = words - parts.gap - widestNumber
        val beforeBar = beforeNumber - parts.gap - parts.bar
        if (widestNameAt(NAME_SP) <= beforeBar && widestCaptionWord <= beforeBar) return CardsFit(CardsLayout.BAR_BESIDE, NAME_SP)
        if (widestCaptionWord <= beforeNumber) {
            val size = ButtonFit.largest(NAME_SP, NAME_LEAST_SP) { widestNameAt(it) <= beforeNumber }
            if (widestNameAt(size) <= beforeNumber) return CardsFit(CardsLayout.BAR_UNDER, size)
        }
        val size = ButtonFit.largest(NAME_SP, NAME_LEAST_SP) { widestNameAt(it) <= words }
        val layout = if (parts.bar + parts.gap + widestNumber <= words) CardsLayout.NUMBER_BESIDE_BAR else CardsLayout.NUMBER_UNDER_BAR
        return CardsFit(layout, size)
    }
}

/** Where the bar and «±8 ц» of the cards of the tolerance stand ([ToleranceBarMath.fit]). */
internal enum class CardsLayout {
    /** The bar beside the words, the number at the end of the row. */
    BAR_BESIDE,

    /** The bar under the caption, at its start; the number at the end of the row. */
    BAR_UNDER,

    /** The bar under the caption and the number beside it. */
    NUMBER_BESIDE_BAR,

    /** The bar under the caption and the number under the bar. */
    NUMBER_UNDER_BAR,
    ;

    /** «±8 ц» stands at the end of the row, right of the words. */
    val numberAtEnd: Boolean get() = this == BAR_BESIDE || this == BAR_UNDER
}

/** Where the bars and the numbers of the three cards stand and how large their names are: one decision for the three. */
internal data class CardsFit(val layout: CardsLayout, val nameSp: Float)
