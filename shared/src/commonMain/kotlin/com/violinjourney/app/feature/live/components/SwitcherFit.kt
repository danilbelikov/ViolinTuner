package com.violinjourney.app.feature.live.components

/**
 * How «Игра | Настройка» fits the top row of Live (spec 3.36.6, 5.29 R6): both segments as wide as the wider word at 15 sp with 16
 * on either side and not narrower than 106 where the room allows it — the switcher standing in the middle of its row, clear of the
 * touch of the gear and of as much on the other side.
 *
 * Upright, where the words do not fit the middle, they step down to 12 sp, 0.5 at a time; then the padding around them gives, down
 * to its least; only then the switcher leaves the middle for the room up to the touch of the gear, where the same steps are taken
 * again (plan Р18: the middle of the screen is the axis of the ring). In the right column of landscape the words keep their size
 * instead ([plan]'s `keepSize`; spec 3.36.6: the controls do not shrink): the size is what the room up to the gear gives, and the
 * switcher stands in the middle only where it fits there at that size — else as near to it as the gear's touch lets it ([start]).
 * Either way it never reaches under the touch of the gear, and no word breaks.
 *
 * Pure: [plan] is given the rooms and asks only how wide the wider word is at a size; the units are the caller's, one and the same.
 */
object SwitcherFit {
    const val MAX_SP = 15f
    const val MIN_SP = 12f
    const val STEP_SP = 0.5f

    /** The words at [sizeSp]; each of the two segments [segment] wide; [centered] — the switcher stands in the middle of its row. */
    data class Plan(val sizeSp: Float, val segment: Float, val centered: Boolean)

    /**
     * [centered] — the widest the switcher may be standing in the middle of its row; [beside] — the widest it may be off the middle,
     * up to the touch of the gear; [inset] — between the capsule and its segments; [minSegment] — the least segment where the room
     * allows it; [padding] and [minPadding] — the room on either side of a word; [keepSize] — the words keep the size [beside] gives
     * them rather than step down for the middle (landscape); [wordAt] — the width of the wider of the two words at a size in sp.
     */
    fun plan(
        centered: Float,
        beside: Float,
        inset: Float,
        minSegment: Float,
        padding: Float,
        minPadding: Float,
        keepSize: Boolean = false,
        wordAt: (sizeSp: Float) -> Float,
    ): Plan {
        if (keepSize) {
            val sized = fit(beside, inset, minSegment, padding, minPadding, wordAt) ?: return cut(beside, inset)
            // the middle at that size, 106 giving way to it as upright; the words and their padding as they are
            val need = wordAt(sized.sizeSp) + 2 * padding
            val most = (centered - 2 * inset) / 2
            return if (need <= most) Plan(sized.sizeSp, minOf(maxOf(need, minSegment), most), centered = true) else sized
        }
        fit(centered, inset, minSegment, padding, minPadding, wordAt)?.let { return it.copy(centered = true) }
        return fit(beside, inset, minSegment, padding, minPadding, wordAt) ?: cut(beside, inset)
    }

    /**
     * Where a switcher [switcher] wide starts in its row [row] wide, the touch of the gear beginning at [gearStart] (in pixels): in
     * the middle where the plan is [centered]; else off the middle only as far as it must — its end at the start of the gear's
     * touch, or in the middle if it fits there after all — and never before the start of the row.
     */
    fun start(row: Int, switcher: Int, gearStart: Int, centered: Boolean): Int {
        val middle = (row - switcher) / 2
        return if (centered) middle else minOf(middle, gearStart - switcher).coerceAtLeast(0)
    }

    /** Not even the least size and padding hold the word beside the gear: it is cut at its end, the gear keeps its touch. */
    private fun cut(beside: Float, inset: Float) = Plan(MIN_SP, ((beside - 2 * inset) / 2).coerceAtLeast(0f), centered = false)

    private fun fit(room: Float, inset: Float, minSegment: Float, padding: Float, minPadding: Float, wordAt: (Float) -> Float): Plan? {
        val most = (room - 2 * inset) / 2
        if (most <= 0f) return null
        var sizeSp = MAX_SP
        while (true) {
            val need = wordAt(sizeSp) + 2 * padding
            if (need <= most) return Plan(sizeSp, minOf(maxOf(need, minSegment), most), centered = false)
            if (sizeSp <= MIN_SP) break
            sizeSp = maxOf(MIN_SP, sizeSp - STEP_SP)
        }
        return if (wordAt(MIN_SP) + 2 * minPadding <= most) Plan(MIN_SP, most, centered = false) else null
    }
}
