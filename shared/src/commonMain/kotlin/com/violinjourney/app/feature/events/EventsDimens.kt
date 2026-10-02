package com.violinjourney.app.feature.events

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes of the events on the screens (spec 5.29 R9; plan, 9.2): dp and sp in one place, no number in the screens. Those of
 * «Занятия» — the marks of a cell, the legend and the hint, the rows of the sheet of the day, the reminder — since stage 97; the form,
 * the screen of an event and «Выступления» bring theirs with them.
 */
internal object EventsDimens {
    // The marks of a cell (5.29 R9, «Клетка»): a plate of the colour of the ground of the grid over the bottom of the circle, the mini
    // signs on it 10 apart by 2 and «+» after three of them; on a column under 50 they are smaller, and smaller again under 44.

    /** The plate of the marks: a rounded bar of 14, its corner 7, from the top of the marks (38, landscape 36) to the bottom of the cell. */
    val MarksPlateHeight = 14.dp
    val MarksPlateCorner = 7.dp

    /** Where the marks begin, from the top of the cell: the bottom of the cell is theirs (5.29 R2: «низ клетки с 38 (landscape 36)»). */
    val MarksTop = 38.dp
    val MarksTopLying = 36.dp

    /** A column from this wide: mini signs of 10, 2 apart, the plate 3 beyond them at each side; «+» 6 × 10. Three and «+» — 48. */
    val MarksWideFrom = 50.dp
    val MarkSign = 10.dp
    val MarkGap = 2.dp
    val MarkSide = 3.dp
    val MoreWidth = 6.dp
    val MoreHeight = 10.dp

    /** A column from this wide and under [MarksWideFrom] (360 — 47): 9, 1.5 apart, 2.5 at the sides — 42.5. */
    val MarksNarrowFrom = 44.dp
    val MarkSignNarrow = 9.dp
    val MarkGapNarrow = 1.5.dp
    val MarkSideNarrow = 2.5.dp

    /** A column under [MarksNarrowFrom] (320 — 41): 8, 1 apart, 2 at the sides, «+» 5 × 8 — 36. */
    val MarkSignNarrowest = 8.dp
    val MarkGapNarrowest = 1.dp
    val MarkSideNarrowest = 2.dp
    val MoreWidthNarrowest = 5.dp
    val MoreHeightNarrowest = 8.dp

    // The legend of the kinds of the month and the hint (5.29 R9, «Легенда и подсказка»).

    /** Under the grid. */
    val LegendTop = 14.dp
    val LegendSign = 12.dp
    val LegendSignGap = 6.dp
    val LegendText = 13.sp
    val LegendLineHeight = 18.sp

    /** Between the kinds of a line, and between the lines. */
    val LegendGap = 16.dp
    val LegendLineGap = 8.dp

    /** A column beside the grid in landscape: its kinds 10 apart, 14 from the grid. */
    val LegendColumnGap = 10.dp
    val LegendBesideGap = 14.dp

    /** The legend stands beside the grid only where at least this is left of the row for it, after the gap. */
    val LegendBesideMin = 120.dp

    /** The hint until the first event: the calendar 16 in the tertiary text, 8 to its words, 13 sp at 1.45; the icon level with the first line. */
    val HintIcon = 16.dp
    val HintIconTop = 1.dp
    val HintIconGap = 8.dp
    val HintText = 13.sp
    val HintLineHeight = 18.85.sp

    /** The line under the name of a month to come, «17 событий»: as the line of the time of R2. */
    val MonthLineText = 13.sp
    val MonthLineHeight = 18.sp

    // «События» of the sheet of the day (5.29 R9, «Лист дня»).

    /** «События»: as «Записи этого дня», 15 sp, 800, 20 above. */
    val DayEventsTop = 20.dp
    val DayEventsTitle = 15.sp
    val DayEventsTitleHeight = 20.sp

    /** Between the title and the rows, and between the rows. */
    val DayEventsGap = 8.dp

    /** A row: at least 64, the ground of the screen, a corner of 18, fields 10 / 12, 12 between its parts. */
    val DayEventMin = 64.dp
    val DayEventCorner = 18.dp
    val DayEventPaddingV = 10.dp
    val DayEventPaddingH = 12.dp
    val DayEventGap = 12.dp

    /** The plate of the sign: 40, a corner of 12, the sign 20 on it. */
    val DayEventPlate = 40.dp
    val DayEventPlateCorner = 12.dp
    val DayEventSign = 20.dp
    val DayEventFirst = 15.sp
    val DayEventFirstHeight = 20.sp
    val DayEventSecond = 13.sp
    val DayEventSecondHeight = 18.sp

    /** A day to come: «Время появится…» 13 sp, 6 under the date; its rows 4 under it. */
    val TimeLaterTop = 6.dp
    val TimeLaterText = 13.sp
    val TimeLaterHeight = 18.sp
    val TimeLaterRowsTop = 4.dp

    /** A day to come without events: «Событий нет» 22 sp, 800, 10 under the date; its words 14 sp under it. */
    val NoEventsTop = 10.dp
    val NoEventsTitle = 22.sp
    val NoEventsTitleHeight = 28.sp
    val NoEventsTextTop = 4.dp
    val NoEventsText = 14.sp
    val NoEventsTextHeight = 20.sp

    /** «Событие в этот день» dashed: 48, a corner of 14, a dash of 1.5, 15 sp, 700, the plus 18, 16 above. */
    val AddEventHeight = 48.dp
    val AddEventCorner = 14.dp
    val AddEventTop = 16.dp
    val AddEventPlus = 18.dp
    val AddEventPadding = 14.dp
    val AddEventText = 15.sp
    val AddEventTextHeight = 20.sp

    // The reminder on «Занятия» (5.29 R9, «Напоминание»).

    /** The card: the colour of a card, a corner of 18, fields 4 / 14 (landscape 2 / 12). */
    val ReminderCorner = 18.dp
    val ReminderPaddingV = 4.dp
    val ReminderPaddingH = 14.dp
    val ReminderPaddingVLying = 2.dp
    val ReminderPaddingHLying = 12.dp

    /** A row: at least 56, 8 above and below its words; the plate of the sign 32 at a corner of 10, the sign 18, 12 to the words (10 lying). */
    val ReminderRowMin = 56.dp
    val ReminderRowPadding = 8.dp
    val ReminderPlate = 32.dp
    val ReminderPlateCorner = 10.dp
    val ReminderSign = 18.dp
    val ReminderGap = 12.dp
    val ReminderGapLying = 10.dp

    /** The first line 15 sp, 700 at 1.3 — 14 sp in the compact card — the second 13 sp. */
    val ReminderFirst = 15.sp
    val ReminderFirstHeight = 19.5.sp
    val ReminderFirstCompact = 14.sp
    val ReminderFirstCompactHeight = 18.2.sp
    val ReminderSecond = 13.sp
    val ReminderSecondHeight = 18.sp

    /** The rule between the rows. */
    val ReminderRule = 1.dp

    /** «ещё N»: 48 high, its words 44 from the start — under the words of the rows — 13 sp, 700; mini signs of 10, 3 apart; the chevron 20. */
    val MoreRowMin = 48.dp
    val MoreIndent = 44.dp
    val MoreGap = 8.dp
    val MoreText = 13.sp
    val MoreTextHeight = 18.sp
    val MoreSign = 10.dp
    val MoreSignGap = 3.dp
    val MoreChevron = 20.dp

    /** The target of «ещё N» of the compact card: 56 wide (52 lying), the whole height of its row, behind a rule; 5 between its parts. */
    val CompactTarget = 56.dp
    val CompactTargetLying = 52.dp
    val CompactTargetGap = 5.dp
}
