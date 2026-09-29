package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the tab «Репертуар» stands in its window (spec 3.36.4, 5.29 R4). Pure, with a test.
 *
 * - [Kind.Upright] — taller than wide: one column up to 560, the tiles upright, the time under the sections.
 * - [Kind.Columns] — landscape: the sections on the left, «Время по элементам» 360 on the right. Only while the left column holds
 *   what it needs ([leftNeed]): a tile alone in its row, the row of a section of one's own, the button of the bottom zone — so
 *   640 × 360, the narrowest landscape of the spec, keeps its two columns with a cutout of up to 40 at its side too (≈ 600).
 * - [Kind.Wide] — wider than tall, but with no time (an empty repertoire) or no room for the two columns (a split screen): one
 *   column in the middle up to 560 in the look of landscape, the time — if there is any — under the sections, as upright.
 */
internal object SectionsLayout {
    enum class Kind { Upright, Columns, Wide }

    /** Between the edge of the screen and what is on it — the fields of the screen (5.29 R4). */
    val ScreenPadding = 16.dp

    /** «Время по элементам» in landscape (3.36.4): the card itself is 360, the edge of the screen 16 from it. */
    val TimeCard = 360.dp
    val TimeColumn = TimeCard + ScreenPadding

    /** [leftNeed] — what the left column needs inside its fields ([SectionsLayout.leftNeed]); the window gives it [leftContent]. */
    fun of(width: Dp, height: Dp, loading: Boolean, hasTime: Boolean, leftNeed: Dp): Kind = when {
        width <= height -> Kind.Upright
        // while the data is read the time may come: the columns stand, the right one empty (a repertoire is empty more rarely)
        (loading || hasTime) && leftContent(width) >= leftNeed -> Kind.Columns
        else -> Kind.Wide
    }

    /** What is left inside the fields of the left column of a window [width] wide beside the time. */
    fun leftContent(width: Dp): Dp = width - TimeColumn - ScreenPadding * 2

    /**
     * What the left column needs inside its fields (5.29 R4, «Две колонки landscape»), all of it measured: the widest of — a tile
     * alone in its row whose name holds [widestWord] (the widest word of the four names at their smallest size, 13 sp: a lone tile
     * steps its names down as the upright ones do) on its line ([TileFit.oneColumnNeed]); the row of a section of one's own with
     * the least room of its words [ownText] beside the shortest bar ([ownRowNeed]); the button of the bottom zone with each of its
     * words whole ([button]). The title of 24 sp is narrower than the row of one's own in all ten languages.
     */
    fun leftNeed(widestWord: Dp, ownText: Dp, button: Dp): Dp = maxOf(TileFit.oneColumnNeed(widestWord), ownRowNeed(ownText), button)

    /** The row of a section of one's own (3.36.4): its fields of 14, the plate, the bar of 84 and the gaps of 12 between them. */
    val OwnRowSide = 14.dp
    val OwnRowGap = 12.dp
    val OwnRowBar = 84.dp

    /** In a narrow column the bar of that row gives way first — but it is never shorter than the plate beside it. */
    val OwnRowBarMin = TileFit.Plate

    /** Everything of that row but its words and its bar: 14 + 36 + 12 + 12 + 14. */
    private val OwnRowAround: Dp = OwnRowSide * 2 + TileFit.Plate + OwnRowGap * 2

    /**
     * The bar of the row of one's own in a column [content] wide: 84, and in a narrow column less — so that its words keep
     * [ownText], the first letter of the name before the ellipsis and the widest word of its count («выучено», «aprendido:») —
     * down to [OwnRowBarMin].
     */
    fun ownRowBar(content: Dp, ownText: Dp): Dp = (content - OwnRowAround - ownText).coerceIn(OwnRowBarMin, OwnRowBar)

    /** How narrow the row of one's own can be: its words keep [ownText] beside the bar of [OwnRowBarMin]. */
    fun ownRowNeed(ownText: Dp): Dp = OwnRowAround + OwnRowBarMin + ownText

    /**
     * The title and the count of landscape stand on one line when both fit it (5.29 R4, «Шапка вкладки»); otherwise the count goes
     * under the title — the title of the tab is never cut for the count. In the units of the caller, one and the same.
     */
    fun countBesideTitle(title: Int, gap: Int, count: Int, room: Int): Boolean = title + gap + count <= room
}

/**
 * How the tiles of the four built-in sections share a column (spec 3.36.4, 5.29 R4): the name on a tile never breaks inside a
 * word. A name of two words may take two lines; a word wider than its line would be cut by the letter («Произведе / ния»), so:
 *
 * - upright there are always two tiles in a row, and the names step down together — 16, 15.5 … 13 sp — to the largest size at which
 *   the widest word of the four fits its line (360 dp with a large font). Below 13 they do not go: a word still too wide breaks
 *   then, as the labels of the tabs are clipped below 10 sp (5.29 R1);
 * - in landscape (the plate beside the name) the names keep 16 sp and the columns give way: two only in a column not narrower
 *   than [NarrowColumn] (3.36.4) whose tiles hold the widest word on its line at 16 sp, otherwise one under another (740 × 360 of a
 *   Galaxy S9, an iPhone 15 on its side). A tile alone in its row steps its names down as the upright ones do where the column is
 *   narrow (640 × 360 with a cutout at its side) or the font large.
 *
 * Pure: the widths come from the caller's text measurer, in dp. The fields of the tiles are here, and the tiles take them from here.
 */
internal object TileFit {
    const val NAME_SP = 16f
    const val MIN_NAME_SP = 13f
    const val STEP_SP = 0.5f

    /** Between two tiles of a row and between the rows. */
    val Gap = 10.dp

    /** The fields of an upright tile at its sides (5.29 R4: 14 / 14 / 16). */
    val UprightStart = 14.dp
    val UprightEnd = 14.dp

    /** The fields of a tile of landscape at its sides, and its plate with the gap to the name. */
    val BesideStart = 12.dp
    val BesideEnd = 14.dp
    val Plate = 36.dp
    val PlateGap = 10.dp

    /** The rows of tiles are laid out in whole pixels: a word that fits only by a hair is not trusted. */
    private val Slack = 1.dp

    /** The line of a name on a tile of [look] when [columns] tiles share [content]. */
    fun nameRoom(look: TileLook, content: Dp, columns: Int): Dp {
        val tile = (content - Gap * (columns - 1)) / columns
        return tile - when (look) {
            TileLook.Upright -> UprightStart + UprightEnd
            TileLook.Beside -> BesideStart + Plate + PlateGap + BesideEnd
        } - Slack
    }

    /** How wide a column must be for a tile of landscape alone in its row to hold [widestWord] on the line of its name (at any size). */
    fun oneColumnNeed(widestWord: Dp): Dp = BesideStart + Plate + PlateGap + widestWord + BesideEnd + Slack

    /** Upright — two. Beside — two in a column not narrower than [NarrowColumn] whose tiles hold [widestWord] (at [NAME_SP]); one otherwise. */
    fun columns(look: TileLook, content: Dp, widestWord: Dp): Int = when (look) {
        TileLook.Upright -> 2
        TileLook.Beside -> if (content >= NarrowColumn && widestWord <= nameRoom(TileLook.Beside, content, 2)) 2 else 1
    }

    /** The one size of the four names: the largest of 16, 15.5 … 13 sp at which [widestAt] that size fits [room]; 13 when none does. */
    fun nameSize(room: Dp, widestAt: (sizeSp: Float) -> Dp): Float {
        var sizeSp = NAME_SP
        while (sizeSp > MIN_NAME_SP) {
            if (widestAt(sizeSp) <= room) return sizeSp
            sizeSp -= STEP_SP
        }
        return MIN_NAME_SP
    }

    /**
     * The words of the names, each to be measured on one line: split at spaces and breaks only — a no-break space keeps its words
     * together, and a name without spaces (Japanese, Chinese) is measured whole.
     */
    fun words(names: List<String>): List<String> = names.flatMap { it.split(' ', '\n', '\t') }.filter { it.isNotEmpty() }.distinct()
}
