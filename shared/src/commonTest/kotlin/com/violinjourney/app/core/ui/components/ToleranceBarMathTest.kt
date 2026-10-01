package com.violinjourney.app.core.ui.components

import com.violinjourney.app.core.domain.TolerancePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bar of the green zone in the cards of the setup (spec 3.36.8, 5.29 R8): its width, whether it stands beside the words, and where
 * the bars, the numbers and the names of the three cards stand so that no word breaks.
 */
class ToleranceBarMathTest {

    @Test
    fun `the zones of the three cards are 59 40 and 15 dp of the track of 84`() {
        assertEquals(59.3f, ToleranceBarMath.segmentDp(TolerancePreset.BEGINNER.cents), 0.1f)
        assertEquals(39.5f, ToleranceBarMath.segmentDp(TolerancePreset.INTERMEDIATE.cents), 0.1f)
        assertEquals(14.8f, ToleranceBarMath.segmentDp(TolerancePreset.PRO.cents), 0.1f)
    }

    @Test
    fun `a wider tolerance is a wider zone and never wider than the track`() {
        val widths = (0..30).map { ToleranceBarMath.segmentDp(it) }
        widths.zipWithNext().forEach { (a, b) -> assertTrue(b >= a, "$a then $b") }
        assertEquals(0f, ToleranceBarMath.segmentDp(0))
        assertEquals(ToleranceBarMath.TRACK_DP, ToleranceBarMath.segmentDp(17), 0.001f, "±17 cents fill the track")
        assertEquals(ToleranceBarMath.TRACK_DP, ToleranceBarMath.segmentDp(30), "and more cannot leave it")
    }

    @Test
    fun `the bar stands beside the words while the widest name has its room`() {
        // a row of 332 (412 upright) with 193 of radio, gaps, bar and number: «Новичок» of 76 stands beside
        assertTrue(ToleranceBarMath.beside(rowWidth = 332f, widestName = 76f, fixed = 193f))
        // 640 × 360 lying, a column of 269: the row of 237 leaves 44 — under the caption
        assertFalse(ToleranceBarMath.beside(rowWidth = 237f, widestName = 76f, fixed = 193f))
        // «Intermédiaire» of 115 on a phone of 360 (a row of 288, fixed 210): under
        assertFalse(ToleranceBarMath.beside(rowWidth = 288f, widestName = 115f, fixed = 210f))
    }

    @Test
    fun `exactly on the edge the bar stays beside`() {
        assertTrue(ToleranceBarMath.beside(rowWidth = 300f, widestName = 100f, fixed = 200f))
        assertFalse(ToleranceBarMath.beside(rowWidth = 300f, widestName = 100.5f, fixed = 200f))
    }

    // ---- where the bars, the numbers and the names of the three cards stand (the review of stage 120). The widths are dp at a
    // density of 1, measured by CoreText on Manrope (weight 800 and tabular figures for the number, the spacing of titleMedium and
    // bodySmall), the font scale linear, as in the window of the tests.

    @Test
    fun `on a phone of 412 the bars stand beside the words at 17 sp`() {
        // ru: «Средний» 78.1, «±12 ц» 47.2, «чувствуется» 81.4 at 13 sp; a row of 332
        val fit = ToleranceBarMath.fit(row = 332f, parts = PARTS, widestNumber = 47.2f, widestCaptionWord = 81.4f, widestNameAt = perSp(78.1f))
        assertEquals(CardsFit(CardsLayout.BAR_BESIDE, 17f), fit)
    }

    @Test
    fun `a word of a caption that does not fit beside the bar puts the bars under the captions`() {
        // ru on a phone of 347: beside the bar «Средний» (78.1) has 78.8 — it fits — but «чувствуется» (81.4) does not
        val fit = ToleranceBarMath.fit(row = 275f, parts = PARTS, widestNumber = 47.2f, widestCaptionWord = 81.4f, widestNameAt = perSp(78.1f))
        assertEquals(CardsFit(CardsLayout.BAR_UNDER, 17f), fit)
    }

    @Test
    fun `lying on 640 x 360 the bars go under the captions and the numbers stay at the end`() {
        // the same words in the column of 603 × 308: a row of 237.5
        val fit = ToleranceBarMath.fit(row = 237.5f, parts = PARTS, widestNumber = 47.2f, widestCaptionWord = 81.4f, widestNameAt = perSp(78.1f))
        assertEquals(CardsFit(CardsLayout.BAR_UNDER, 17f), fit)
        assertTrue(fit.layout.numberAtEnd)
    }

    @Test
    fun `the names step down together as far as the widest needs and no further`() {
        // en «Intermediate» 167.3 at 17 sp × 1.5, «±12 c» 68.7: in a row of 270 the column beside the number is 150.3 — 15 sp
        val fit = ToleranceBarMath.fit(row = 270f, parts = PARTS, widestNumber = 68.7f, widestCaptionWord = 67.2f, widestNameAt = perSp(167.3f))
        assertEquals(CardsFit(CardsLayout.BAR_UNDER, 15f), fit)
    }

    @Test
    fun `where not even 13 sp keeps the widest name whole beside the number the number goes under to the bar`() {
        // fr at 320 × 544 at the font 1.5 (the review of stage 120): «Intermédiaire» 175.1 at 17 sp, 134.9 at 13 — the column beside
        // «±12 cts» (94.6) is 102.4, and before the stage the word broke there at 13 sp. Under the caption the name has 211: 17 sp again.
        val fit = ToleranceBarMath.fit(row = 248f, parts = PARTS, widestNumber = 94.6f, widestCaptionWord = 67.2f, widestNameAt = perSp(175.1f))
        assertEquals(CardsFit(CardsLayout.NUMBER_BESIDE_BAR, 17f), fit)
        assertFalse(fit.layout.numberAtEnd)
    }

    @Test
    fun `a word of a caption that does not fit beside the number sends the number under too`() {
        // ru at 320 × 544 at the font 2.0 of Android 14: «чувствуется» 157 at 13 sp, the column beside «±12 ц» (80.8) only 116.2 — the
        // names alone would fit there
        val fit = ToleranceBarMath.fit(row = 248f, parts = PARTS, widestNumber = 80.8f, widestCaptionWord = 157f, widestNameAt = perSp(133.6f))
        assertEquals(CardsFit(CardsLayout.NUMBER_BESIDE_BAR, 17f), fit)
    }

    @Test
    fun `where the bar and the number do not stand side by side the number goes under the bar`() {
        // a column of 180 under the caption, the bar 84 and the gap 14: a number of 95 does not stand beside them
        val fit = ToleranceBarMath.fit(row = 217f, parts = PARTS, widestNumber = 95f, widestCaptionWord = 60f, widestNameAt = perSp(175.1f))
        assertEquals(CardsFit(CardsLayout.NUMBER_UNDER_BAR, 17f), fit, "the name of 175.1 stands whole in the column of 180 at 17 sp")
    }

    private companion object {
        /** The radio 22 and its gap 14, the gap 14, the bar 84, the slack 1 (spec 5.29 R8). */
        val PARTS = ToleranceBarMath.Parts(lead = 36f, gap = 14f, bar = ToleranceBarMath.TRACK_DP, slack = 1f)

        /** A name as wide as [at17] at 17 sp and in proportion at other sizes. */
        fun perSp(at17: Float): (Float) -> Float = { sizeSp -> at17 * sizeSp / ToleranceBarMath.NAME_SP }
    }
}
