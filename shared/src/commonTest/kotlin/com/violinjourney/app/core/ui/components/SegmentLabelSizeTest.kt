package com.violinjourney.app.core.ui.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One size for the words of the tolerance of «Настройки» (spec 3.36.8, 5.29 R8): 14 sp, or all three a step smaller together down to
 * 12; where 12 does not hold a word in its equal third, the thirds give way to the words, and at a large font only the words go on down
 * — a word is never broken while it can stand. Widths in dp from Manrope 800 (CoreText, `labelLarge` with its 0.1 sp of tracking, 14
 * sp, scaled with the size); the second lines at 12 sp, 700, tabular figures, scaled with the size they are asked at — 12, or the size
 * of words under 12; a large font scales the widths as Android's curve does (14 sp at 1.3 — 18.8 dp, 12 sp at 1.5 — 18 dp). The rooms
 * around a label are those of the regular switch: 14 at the ends, 12 in the middle, and 1 of slack.
 */
class SegmentLabelSizeTest {
    private val around = listOf(14f, 12f, 14f)
    private val slack = 1f

    /** The names at 14 sp in dp, scaled with the size and by the [font] (dp per sp of the size). */
    private fun names(vararg at14: Float, font: Float = 1f): (Float) -> List<Float> = { sizeSp -> at14.map { it * sizeSp / 14f * font } }

    /** «±12 ц · ±8 ц · ±3 ц» — a line and its widest word, 12 sp scaled with the size asked — and by the [font]. */
    private fun cents(font: Float = 1f) = scaled(listOf(SegmentFit.Label(32.8f, 22.4f), SegmentFit.Label(25.3f, 17.4f), SegmentFit.Label(25.3f, 17.4f)), font)

    /** Second lines of [at12] at 12 sp, scaled with the size they are asked at and by the [font]. */
    private fun scaled(at12: List<SegmentFit.Label>, font: Float = 1f): (Float) -> List<SegmentFit.Label> = { sizeSp ->
        at12.map { SegmentFit.Label(it.line * font * sizeSp / 12f, it.word * font * sizeSp / 12f) }
    }

    private val russian = floatArrayOf(61.1f, 63.7f, 47.7f)
    private val french = floatArrayOf(66.4f, 95.1f, 23.8f)
    private val spanish = floatArrayOf(85.3f, 77.0f, 23.8f)

    private fun plan(room: Float, words: (Float) -> List<Float>, seconds: (Float) -> List<SegmentFit.Label> = cents(), leastSp: Float = 12f) =
        SegmentLabelSize.plan(room, around, slack, leastSp, seconds, wordsAt = words)

    private fun assertEqualThirds(room: Float, plan: SegmentFit.Plan) =
        assertEquals(List(3) { room / 3 }, plan.widths, "equal thirds")

    private fun assertWordsStand(plan: SegmentFit.Plan, words: (Float) -> List<Float>) {
        val at = words(plan.sizeSp)
        plan.widths.forEachIndexed { i, width -> assertTrue(width >= at[i] + around[i] + slack, "word $i stands whole in its segment: $width of ${plan.widths} at ${plan.sizeSp} sp") }
    }

    private fun assertAddsUp(room: Float, plan: SegmentFit.Plan) = assertTrue(abs(plan.widths.sum() - room) < 0.01f, "the widths fill the row: ${plan.widths}")

    @Test
    fun `the words that stand in their thirds keep 14 sp`() {
        // 412 × 892: the group of 380 less the fields of its row — 348
        val plan = plan(348f, names(*russian))
        assertEquals(14f, plan.sizeSp)
        assertEqualThirds(348f, plan)
    }

    @Test
    fun `one word that does not stand takes all three a step smaller`() {
        // es on 360: «Principiante» needs 85.3 + 15 at 14 sp in a third of 296 — at 13.5 it stands, and «Intermedio» goes down with it
        val plan = plan(296f, names(*spanish))
        assertEquals(13.5f, plan.sizeSp)
        assertEqualThirds(296f, plan)
    }

    @Test
    fun `the words step down as far as 12 sp in equal thirds`() {
        // fr on 360: «Intermédiaire» in the middle third — 95.1 at 14, it stands at 12.5
        val plan = plan(296f, names(*french))
        assertEquals(12.5f, plan.sizeSp)
        assertEqualThirds(296f, plan)
        // a little narrower: down to 12, no further while the thirds hold the words
        val narrow = plan(285f, names(*french))
        assertEquals(12f, narrow.sizeSp)
        assertEqualThirds(285f, narrow)
    }

    @Test
    fun `a word that does not stand at 12 in its third has the row shared by the words and stays whole`() {
        // fr on 360 at the font 1.3: «Intermédiaire» at 12 sp is 106 dp, its third leaves 85.7
        val words = names(*french, font = 1.3f)
        val plan = plan(296f, words, cents(1.3f), leastSp = 9.2f)
        assertEquals(12f, plan.sizeSp, "the size does not go below 12 while the words can share the row")
        assertWordsStand(plan, words)
        assertTrue(plan.widths[1] > 296f / 3, "«Intermédiaire» takes more than a third: ${plan.widths}")
        assertAddsUp(296f, plan)
    }

    @Test
    fun `at a large font the words go on down together under 12 sp but no lower than 12 sp of the default font`() {
        // ru at the font 1.5 in a row of 250 (a phone of 314): by the words they stand only at 11 sp — 16.5 dp, still over 12 on the screen
        val words = names(*russian, font = 1.5f)
        val plan = plan(250f, words, cents(1.5f), leastSp = 8f)
        assertEquals(11f, plan.sizeSp)
        assertWordsStand(plan, words)
        assertAddsUp(250f, plan)
        // where not even 8 sp — 12 dp — holds them, they stay at 8 and a word goes on to a second line
        val tooNarrow = plan(120f, words, cents(1.5f), leastSp = 8f)
        assertEquals(8f, tooNarrow.sizeSp)
        assertAddsUp(120f, tooNarrow)
    }

    @Test
    fun `at the default font the words never go under 12 sp and a word that does not stand goes on to a second line`() {
        // a row too narrow for the words even shared by them: at the default font 12 is the least, the segments share the row by
        // what each needs, and the widest word goes on to a second line
        val words = names(*french)
        val plan = plan(150f, words)
        assertEquals(12f, plan.sizeSp)
        assertAddsUp(150f, plan)
        val needs = words(12f).mapIndexed { i, word -> maxOf(word, cents()(12f)[i].word) + around[i] + slack }
        plan.widths.forEachIndexed { i, width -> assertEquals(150f * needs[i] / needs.sum(), width, 0.01f, "segment $i by what it needs") }
    }

    @Test
    fun `a word that stands at the border of its third stands`() {
        // the word and its room exactly a third: it stands, at 14
        val plan = plan(3 * (61f + 14f + 1f), names(61f, 10f, 10f))
        assertEquals(14f, plan.sizeSp)
        assertEqualThirds(228f, plan)
    }

    @Test
    fun `the widest word of a second line keeps its room and the line wraps where the whole of it does not stand`() {
        // ja at 1.5 on a narrow phone: «初級» is short, «±12 セント» is 92 dp — not in a third of 210; its word «セント» (54) is, and the
        // words keep their 14 sp: the second line wraps at its space
        val ja = names(28.2f, 28.2f, 28.2f, font = 1.5f)
        val katakana = scaled(listOf(SegmentFit.Label(92f, 54f), SegmentFit.Label(85f, 54f), SegmentFit.Label(85f, 54f)))
        val plan = plan(210f, ja, katakana, leastSp = 8f)
        assertEquals(14f, plan.sizeSp, "the second line does not make the words smaller")
        assertEqualThirds(210f, plan)
        // shared by the words, a segment with a short word and a long second line keeps the room of the second line's word
        val words = names(80f, 80f, 20f)
        val seconds = scaled(listOf(SegmentFit.Label(30f, 20f), SegmentFit.Label(30f, 20f), SegmentFit.Label(60f, 50f)))
        val shared = plan(240f, words, seconds)
        assertEquals(12f, shared.sizeSp)
        assertTrue(shared.widths[2] >= 50f + around[2] + slack, "the word of the third second line stands: ${shared.widths}")
        assertTrue(shared.widths[2] < 60f + around[2] + slack, "the whole of it does not, and it wraps at its space: ${shared.widths}")
        assertWordsStand(shared, words)
        assertAddsUp(240f, shared)
        // a row a little wider holds the whole second line too: it gets its line rather than wrap
        val roomy = plan(245f, words, seconds)
        assertEquals(12f, roomy.sizeSp)
        assertTrue(roomy.widths[2] >= 60f + around[2] + slack, "the whole third second line stands: ${roomy.widths}")
        assertWordsStand(roomy, words)
        assertAddsUp(245f, roomy)
    }

    @Test
    fun `the second line is 12 sp and under words smaller than that their own size`() {
        listOf(14f, 13.5f, 12.5f, 12f).forEach { assertEquals(12f, SegmentLabelSize.secondSp(it), "under words of $it sp") }
        listOf(11.5f, 11f, 9.2f, 8f).forEach { assertEquals(it, SegmentLabelSize.secondSp(it), "a number is never larger than its word: $it sp") }
    }

    @Test
    fun `the second lines are measured at the size they are drawn at`() {
        // ru at the font 1.5 in a row of 250: under words of 14 … 12 at 12 (step 1, then the first size of step 2), under the words that
        // go on down at their size — 11.5, then 11, where they stand
        val asked = mutableListOf<Float>()
        val seconds = cents(1.5f)
        val plan = SegmentLabelSize.plan(250f, around, slack, leastSp = 8f, secondsAt = { sizeSp -> seconds(sizeSp).also { asked += sizeSp } }, wordsAt = names(*russian, font = 1.5f))
        assertEquals(11f, plan.sizeSp)
        assertEquals(listOf(12f, 12f, 12f, 12f, 12f, 12f, 11.5f, 11f), asked, "the sizes the second lines were measured at")
    }

    @Test
    fun `a second line wider than its word goes down with the words rather than break`() {
        // short names over long second lines in a narrow row: at 12 sp the second lines want 60 + 15, 60 + 13 and 60 + 15 — 223 of 220;
        // at 11.5 they go down with the words and stand — 215.5
        val shortNames = names(20f, 20f, 20f, font = 1.5f)
        val longSeconds = scaled(listOf(SegmentFit.Label(60f, 60f), SegmentFit.Label(60f, 60f), SegmentFit.Label(60f, 60f)))
        val plan = plan(220f, shortNames, longSeconds, leastSp = 8f)
        assertEquals(11.5f, plan.sizeSp, "the second lines at 11.5 with the words: ${plan.widths}")
        assertAddsUp(220f, plan)
    }

    @Test
    fun `the sizes to try go from the largest down by half points and end at the least`() {
        assertEquals(listOf(14f, 13.5f, 13f, 12.5f, 12f), SegmentLabelSize.sizes(14f, 12f))
        assertEquals(listOf(12f), SegmentLabelSize.sizes(12f, 12f))
        val large = SegmentLabelSize.sizes(12f, 9.2f)
        assertEquals(12f, large.first())
        assertEquals(9.2f, large.last())
        assertEquals(listOf(12f, 11.5f, 11f, 10.5f, 10f, 9.5f, 9.2f), large)
    }
}
