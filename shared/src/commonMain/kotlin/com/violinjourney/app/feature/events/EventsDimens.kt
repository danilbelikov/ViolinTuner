package com.violinjourney.app.feature.events

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes of the events on the screens (spec 5.29 R9; plan, 9.2): dp and sp in one place, no number in the screens. Those of
 * «Занятия» — the marks of a cell, the legend and the hint, the rows of the sheet of the day, the reminder — since stage 97; the screen
 * of an event, its sheets and the sheet of a repeat since stage 98; the form and «Выступления» bring theirs with them.
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

    /** The chevron at the end of a row of an event — of the sheet of the day and of the reminder: 24 in the third level of text. */
    val EventChevron = 24.dp

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

    // The screen of an event (5.29 R9, «Общее», «Экран события»).

    /** The bar: 56 upright, 48 lying; the screen keeps 16 at its sides and a column no wider than 560 upright. */
    val BarHeight = 56.dp
    val BarHeightLying = 48.dp
    val ScreenSide = 16.dp
    val ColumnMax = 560.dp

    /** The bar's name shows once the large one has scrolled past this. */
    val TitleAppearsAfter = 80.dp

    /** The chip of the kind: 32, a capsule, fields 8 / 12, the sign 18 and the name 13 sp, 800 on the kind at 18 %. */
    val ChipHeight = 32.dp
    val ChipPaddingStart = 8.dp
    val ChipPaddingEnd = 12.dp
    val ChipGap = 6.dp
    val ChipSign = 18.dp
    val ChipText = 13.sp
    val ChipTextHeight = 18.sp

    /** The name: 28 sp, 800, −0.02 em, a line of 1.15, 10 above and 8 under it; 24 sp lying. */
    val TitleText = 28.sp
    val TitleTextLying = 24.sp

    /**
     * The name never breaks inside a word (spec 3.36.9: «переносится целиком»): where its longest word does not fit its line it is 0.5 sp
     * smaller at a time, down to 20 sp (18 lying) — «Международного» at 24 sp in the left column of 640 × 360 at the font 1.3.
     */
    const val TITLE_LEAST_SP = 20f
    const val TITLE_LYING_LEAST_SP = 18f
    const val TITLE_LINE = 1.15f
    const val TITLE_TRACKING = -0.02f
    val TitleTop = 10.dp
    val TitleBottom = 8.dp

    /** The lines «когда · повтор · кто или где»: the icon 18, 8 to the words, 15 sp (14 lying), 6 between the lines (4 lying). */
    val LineIcon = 18.dp
    val LineGap = 8.dp
    val LineText = 15.sp
    val LineTextHeight = 21.sp
    val LineTextLying = 14.sp
    val LineTextHeightLying = 19.sp
    val LinesGap = 6.dp
    val LinesGapLying = 4.dp

    /** The title of a section: 15 sp, 800, 22 above and 10 under it; «+ Добавить» a text button of 48. */
    val SectionTop = 22.dp
    val SectionBottom = 10.dp
    val SectionText = 15.sp
    val SectionTextHeight = 20.sp

    /** The notes when there are none: a dashed card of 1.5, its question 15 sp in the second level, 14 / 16 inside. */
    val NotesEmptyPaddingV = 14.dp
    val NotesEmptyPaddingH = 16.dp
    val NotesEmptyText = 15.sp
    val NotesEmptyTextHeight = 21.sp
    val DashCorner = 18.dp

    /** The programme: a card, 0 / 14 inside; a row at least 60 — the number a circle of 26, 15 sp / 700 and 13 sp, the cross 48 / 18. */
    val ProgramPaddingH = 14.dp
    val ProgramRowMin = 60.dp
    val ProgramNumber = 26.dp
    val ProgramNumberText = 13.sp
    val ProgramGap = 12.dp
    val ProgramTitle = 15.sp
    val ProgramTitleHeight = 20.sp
    val ProgramSecond = 13.sp
    val ProgramSecondHeight = 18.sp
    val ProgramCross = 48.dp
    val ProgramCrossIcon = 18.dp

    /** The target of the cross reaches this far into the field of the card (events-views.html 5: `margin-right: -12px`): its icon 17 from the edge. */
    val ProgramCrossOut = 12.dp
    val Rule = 1.dp

    /** Between the cards of the records. */
    val RecordsGap = 8.dp

    /** «Записи появятся с …»: 48, a corner of 14, a dash of 1.5, 0 / 14 inside, 10 to the words, the tape 18, 14 sp, 18 above. */
    val LaterHeight = 48.dp
    val LaterCorner = 14.dp
    val LaterPaddingH = 14.dp
    val LaterGap = 10.dp
    val LaterIcon = 18.dp
    val LaterText = 14.sp
    val LaterTextHeight = 19.sp
    val LaterTop = 18.dp

    /** «Можно добавить»: rows of 60 — a plate of 36 / 12, its icon 18, 12 to the words, 16 sp / 700 and 13 sp / 500, the plus 24. */
    val CanAddRowMin = 60.dp
    val CanAddPlate = 36.dp
    val CanAddPlateCorner = 12.dp
    val CanAddIcon = 18.dp
    val CanAddGap = 12.dp
    val CanAddTitle = 16.sp
    val CanAddTitleHeight = 21.sp
    val CanAddCaption = 13.sp
    val CanAddCaptionHeight = 18.sp
    val CanAddPlus = 24.dp

    /** The end of the column over the bottom zone. */
    val ScrollEndGap = 24.dp

    /** A file on its way in: a spinner of 18, its line 2, in the place of the plus of what adds a recording (D37). */
    val Spinner = 18.dp
    val SpinnerStroke = 2.dp

    /** Lying: the left column min(372, 45 % of the window), a rule of 1 between the columns. */
    val LeftColumnMax = 372.dp
    const val LEFT_COLUMN_SHARE = 0.45f

    // The sheet «Добавить запись» (5.29 R9): rows of the sheet «Что добавить?» of R4 — the plate 40 / 12, its icon 24.

    val AddPlate = 40.dp
    val AddPlateIcon = 24.dp

    // The choice of a programme (5.29 R9, «Выбор программы»).

    /** The line under the label: 14 sp, 2 above it, one line. */
    val ProgramSheetSubtitle = 14.sp
    val ProgramSheetSubtitleHeight = 19.sp
    val ProgramSheetSubtitleTop = 2.dp

    /** The list keeps 8 at its sides: the rows' ground reaches 12 past the words. */
    val ProgramListSide = 8.dp

    /** The empty repertoire: a dashed card of 18, 18 / 16 inside, 16 sp / 800 over 14 sp at 1.45. */
    val EmptyPaddingV = 18.dp
    val EmptyPaddingH = 16.dp
    val EmptyTitle = 16.sp
    val EmptyTitleHeight = 21.sp
    val EmptyText = 14.sp
    val EmptyTextHeight = 20.3.sp
    val EmptyGap = 6.dp

    // The sheets of a repeat (5.29 R9, «Листы повтора»): the question 20 sp / 800 at 1.25, its text 14 sp at 1.45, the answers at
    // least 60 in two lines (16 sp / 800 and 13 sp / 600), 6 apart; the bin of a deletion 44 in a circle of the danger at 16 %.

    val ScopeQuestion = 20.sp
    val ScopeQuestionHeight = 25.sp
    val ScopeQuestionTop = 6.dp
    val ScopeNote = 14.sp
    val ScopeNoteHeight = 20.3.sp
    val ScopeNoteTop = 10.dp
    val ScopeAnswerMin = 60.dp
    val ScopeAnswerText = 16.sp
    val ScopeAnswersTop = 18.dp
    val ScopeAnswersGap = 6.dp
    val ScopeBin = 44.dp
    val ScopeBinIcon = 22.dp
    const val SCOPE_BIN_ALPHA = 0.16f
    val ScopeBinBottom = 12.dp
}
