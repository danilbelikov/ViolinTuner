package com.violinjourney.app.core.ui.components

/**
 * One size for the words of a [SegmentedSwitch] whose segments carry a second line — «Новичок» over «±12 ц», the tolerance of
 * «Настройки» (spec 3.36.8, 5.29 R8) — chosen as the labels of the tabs are (5.29 R1, `TabLabels`):
 *
 * 1. equal shares, the words of every segment one size: 14 sp, or, where any of them does not stand on one line in its share (fr
 *    «Intermédiaire» on 360 dp), all of them a step smaller together — 13.5 … [MIN_SP];
 * 2. where not even [MIN_SP] holds a word in its equal share (a large system font: fr, en, es and it on 360 at 1.3), the segments share
 *    the row by their words, still [MIN_SP] and each word on one line — as the status of R4 does ([SegmentFit]); at a large font, and
 *    only there, the words go on down together, half a point at a time, as far as [LEAST_DP] on the screen (12 sp of the default font,
 *    as the tabs go no lower than their 10): ru «Новичок · Средний · Профи» on 320 at 1.5;
 * 3. past that a word goes on to a second line and its segment grows (R1) — the segments then share the row by what they need, so
 *    that as little as can be is broken.
 *
 * The second line is [MIN_SP] while the words are not smaller ([secondSp]): it does not step with them from 14 to 12 and it decides
 * nothing but its own room — its widest word stands whole in its segment, and its line wraps at its spaces where the whole of it does
 * not fit («±12 / セント» on a narrow phone at a large font). Where the words go under 12 (step 2, a large font) it goes down with them,
 * one size with the words: the number under a word is never larger than the word (spec 3.36.8: «слово и под ним число мельче»).
 *
 * Pure: the widths come from the caller's text measurer; the units are the caller's, one and the same (pixels).
 */
internal object SegmentLabelSize {
    const val MAX_SP = 14f
    const val MIN_SP = 12f
    const val STEP_SP = 0.5f

    /** The least the words are drawn on the screen, whatever the system font: 12 sp of the default font. */
    const val LEAST_DP = 12f

    /**
     * The size of the words and the width of each segment (they add up to the [room]). [around] — the room around a label in its
     * segment (the edges and gaps of its pill and its padding); [slack] — per segment, for the row laid out in whole pixels; [leastSp]
     * — the size in sp drawn as [LEAST_DP] at the font of the screen (12 at the default font, 8 at 1.5); [secondsAt] — the second lines
     * at a size in sp ([secondSp] of the size of the words): [SegmentFit.Label.line] the whole line, [SegmentFit.Label.word] its
     * widest word; [wordsAt] — each label on one line at a size in sp.
     */
    fun plan(
        room: Float,
        around: List<Float>,
        slack: Float,
        leastSp: Float,
        secondsAt: (sizeSp: Float) -> List<SegmentFit.Label>,
        maxSp: Float = MAX_SP,
        wordsAt: (sizeSp: Float) -> List<Float>,
    ): SegmentFit.Plan {
        val count = around.size
        val share = room / count
        fun need(index: Int, word: Float, second: Float) = maxOf(word, second) + around[index] + slack

        // 1. equal shares: the largest size at which every word stands on one line in its share
        for (sizeSp in sizes(maxSp, MIN_SP)) {
            val words = wordsAt(sizeSp)
            val seconds = secondsAt(secondSp(sizeSp))
            if (words.indices.all { need(it, words[it], seconds[it].word) <= share }) return SegmentFit.Plan(sizeSp, List(count) { share })
        }
        // 2. by the words: the second lines whole where they can be, else each by its widest word
        var size = MIN_SP
        var needs = emptyList<Float>()
        for (sizeSp in sizes(MIN_SP, minOf(MIN_SP, leastSp))) {
            val words = wordsAt(sizeSp)
            val seconds = secondsAt(secondSp(sizeSp))
            val whole = words.indices.map { need(it, words[it], seconds[it].line) }
            if (whole.sum() <= room) return SegmentFit.Plan(sizeSp, spread(whole, room))
            val tight = words.indices.map { need(it, words[it], seconds[it].word) }
            if (tight.sum() <= room) return SegmentFit.Plan(sizeSp, spread(tight, room))
            size = sizeSp
            needs = tight
        }
        // 3. a word goes on to a second line: the row is shared by what each needs
        val total = needs.sum()
        return SegmentFit.Plan(size, needs.map { room * it / total })
    }

    /** The size of the second line under words of [wordsSp]: 12 sp, and under smaller words their own size — never larger than they are. */
    fun secondSp(wordsSp: Float): Float = minOf(MIN_SP, wordsSp)

    /** The sizes to try, largest first: [from], a half point less, … down to [least] — the last one [least] itself, even off the half points. */
    fun sizes(from: Float, least: Float): List<Float> = buildList {
        var sizeSp = from
        while (sizeSp > least + EPSILON) {
            add(sizeSp)
            sizeSp -= STEP_SP
        }
        add(least)
    }

    /** Each its need, and what is left of the row in proportion. */
    private fun spread(needs: List<Float>, room: Float): List<Float> {
        val sum = needs.sum()
        return needs.map { it + (room - sum) * it / sum }
    }

    /** Half points are exact in a float; a size computed from the font may land a hair off one. */
    private const val EPSILON = 0.001f
}
