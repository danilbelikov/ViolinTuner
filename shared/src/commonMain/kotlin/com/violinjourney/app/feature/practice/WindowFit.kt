package com.violinjourney.app.feature.practice

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How big the window of the home is on «Занятия» (spec 3.36.2, «Маленький экран и крупный шрифт»; 5.29 R2), pure. In portrait the
 * picture takes what is left of the first screen, never more than 148 — the whole window fits it: the scroll window less the blocks
 * above the window in the layout without a running practice (the top field, the path row, «Сегодня» and their gaps), less what
 * stands under the picture — the line, 56 at least and higher when its call takes more lines, and the bar to the price with its
 * field while the takts are short — and less 12 under the window. 148 on a usual screen, less on 360 × 640 and with a large font.
 * Less than 72 left — no picture: the window becomes a line of 56 with a still thumbnail. In landscape the window is a row of 96 with
 * the picture on its left: 176 wide, 120 in a column narrower than 400.
 */
object WindowFit {
    val PictureMax = 148.dp
    val PictureMin = 72.dp

    /** The line under the picture at its least, and the whole window when it is a line. */
    val Line = 56.dp

    /** Left under the window on the first screen. */
    val Below = 12.dp

    val BesideWide = 176.dp
    val BesideNarrow = 120.dp
    val BesideWideFrom = 400.dp

    /**
     * The height of the picture in a scroll window of [viewport] with [above] over the window and [under] under the picture — the
     * line and the bar as they are measured; null — a line instead.
     */
    fun picture(viewport: Dp, above: Dp, under: Dp): Dp? {
        val left = viewport - above - under - Below
        return if (left < PictureMin) null else minOf(PictureMax, left)
    }

    /** The width of the picture beside the line in the right column of landscape, [column] wide. */
    fun besideWidth(column: Dp): Dp = if (column >= BesideWideFrom) BesideWide else BesideNarrow
}
