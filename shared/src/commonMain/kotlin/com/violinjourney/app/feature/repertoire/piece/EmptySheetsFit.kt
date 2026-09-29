package com.violinjourney.app.feature.repertoire.piece

import com.violinjourney.app.core.ui.components.SegmentFit

/**
 * How the two tiles of an element without pages share their row (spec 3.36.4, 5.29 R4): a word of «Сфотографировать» /
 * «Из галереи» never breaks by the letter. Upright the tiles are [tile] wide (150) while both names keep their widest word in it at
 * their size; otherwise, and lying ([tile] null — they share the column), they take the row: equally while the words fit, else by
 * their words, else the names step down together to [SegmentFit.MIN_SP] ([SegmentFit]). Pure; in the units of the caller.
 */
internal object EmptySheetsFit {
    const val NAME_SP = 13f

    fun plan(row: Float, tile: Float?, gap: Float, around: List<Float>, slack: Float, labelsAt: (sizeSp: Float) -> List<SegmentFit.Label>): SegmentFit.Plan {
        fun planIn(room: Float) = SegmentFit.plan(room, around, slack, maxSp = NAME_SP, share = SegmentFit.Share.Equal, labelsAt = labelsAt)
        val count = around.size
        if (tile != null) {
            val fixed = planIn(tile * count)
            if (fixed.sizeSp == NAME_SP && fixed.widths.all { it == tile }) return fixed
        }
        return planIn((row - gap * (count - 1)).coerceAtLeast(0f))
    }
}
