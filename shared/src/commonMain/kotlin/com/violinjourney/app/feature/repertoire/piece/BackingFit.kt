package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the card of the backing stands beside the notes lying (spec 3.36.4, 5.29 R4): 4 : 3 with a gap of 12 — but only while the
 * column of its name keeps the widest word of the name and of «минусовка 3:40» on its line. Otherwise the two cards stand one under
 * another, each as wide as the right column: in 640 × 360 the column of the name would be ≈ 42 dp and «клавесин» would break by the
 * letter, the card growing into a tall strip. Pure; the fields of the card are here, and the card takes them from here.
 */
internal object BackingFit {
    /** The fields of the card (5.29 R4: 10 / 8 / 10 / 12). */
    val Start = 12.dp
    val End = 8.dp

    /** «слушать» / «пауза» and «⋯» at its sides, and the gaps of 12 between them and the name. */
    val PlayCircle = 44.dp
    val MoreButton = 48.dp
    val Gap = 12.dp

    /** Beside the notes: the shares of the row and the gap between the cards (5.29 R4). */
    const val BACKING_SHARE = 4f
    const val NOTES_SHARE = 3f
    val BesideGap = 12.dp

    /** The row is laid out in whole pixels: a word that fits only by a hair is not trusted. */
    private val Slack = 1.dp

    /** The column of the name in a card [card] wide. */
    fun nameColumn(card: Dp): Dp = card - Start - PlayCircle - Gap * 2 - MoreButton - End

    /** The width of the card beside the notes in a right column [content] wide. */
    fun cardBeside(content: Dp): Dp = (content - BesideGap) * (BACKING_SHARE / (BACKING_SHARE + NOTES_SHARE))

    /** Beside the notes only while the column of the name keeps [widestWord] — of the name and of its length — on its line. */
    fun besideNotes(content: Dp, widestWord: Dp): Boolean = nameColumn(cardBeside(content)) >= widestWord + Slack
}
