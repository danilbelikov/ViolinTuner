package com.violinjourney.app.feature.events

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sizes of the events on the screens (spec 5.29 R9; plan, 9.2): dp and sp in one place, no number in the screens. Those of
 * «Занятия» — the marks of a cell, the legend and the hint, the rows of the sheet of the day, the reminder — since stage 97; the screen
 * of an event, its sheets and the sheet of a repeat since stage 98а; the form, its sheets and the sheet «Вид» since stage 98б; «Выступления»
 * bring theirs with them.
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

    // The form of an event (5.29 R9, «Форма»): its captions and fields are those of the forms of R4 (`FormCaption`, `AppField`, 14 apart).

    /** «Частое»: four starts at most (the domain counts them with this limit as its argument). */
    const val FREQUENT_STARTS = 4

    /** The dates the answer «Этот и следующие» names before «и дальше» (plan D31; events-form.html 6 shows two). */
    const val ANSWER_DATES = 2

    /** The counter of the title appears from 60 characters of its 80, of the teacher or the place from 45 of 60 (decision 36). */
    const val TITLE_COUNTER_FROM = 60
    const val PLACE_COUNTER_FROM = 45

    /**
     * A tile of a kind: 80 wide and at least 80 high, a corner of 18, a frame of 2 (clear, the colour of the kind when chosen), 8 between
     * the tiles; the sign 24, 6 over its name; the name 12 sp / 700 on two lines at most, smaller down to 10 rather than broken inside a
     * word. The floor is in dp, as the floor of the number of the stepper of R3: at a large font 10 sp would not hold «Выступление».
     */
    val TileSize = 80.dp
    val TileCorner = 18.dp
    val TileBorder = 2.dp
    val TileGap = 8.dp
    val TileSign = 24.dp
    val TileSignGap = 6.dp
    val TileText = 12.sp
    val TileTextLeast = 10.dp
    const val TILE_TEXT_LINES = 2
    const val TILE_TEXT_LINE = 1.2f

    /** The dashed frame of «Свой вид»: 2, its plus 24. */
    val TileDash = 2.dp
    val TilePlus = 24.dp

    /** «Своих видов — 20 из 20» in the place of «Свой вид»: 13 sp in the second level of text. */
    val KindsFullText = 13.sp

    /** The row of the kind under the tiles: 48, the pencil 18, 10 to its words of 14 sp / 700, the chevron 18. */
    val KindRowMin = 48.dp
    val KindRowIcon = 18.dp
    val KindRowGap = 10.dp
    val KindRowText = 14.sp
    val KindRowTextHeight = 19.sp

    /** The date and the time side by side, 8 apart, their widths 1.2 : 1; the calendar and the clock 18 in the place of the chevron. */
    val DateTimeGap = 8.dp
    const val DATE_SHARE = 1.2f
    const val TIME_SHARE = 1f
    val DateTimeIcon = 18.dp

    /**
     * The end in the caption of «Длительность», «до 17:45»: 13 sp / 700 in the first level of text, at least 8 from the caption — where
     * the two do not fit one line (past midnight on 320 at 1.3) the end wraps at its right, the caption stays whole.
     */
    val EndText = 13.sp
    val EndGap = 8.dp

    /** The chips of the length: 8 apart, the second row 8 under the first. */
    val ChoiceGap = 8.dp

    /**
     * The line under «Повтор»: 13 sp, 8 under the switch; «до…» in the accent, 700 — a span of the line, its touch of 48 the one Compose
     * gives a small target, the line not grown by it.
     */
    val SummaryTop = 8.dp
    val SummaryText = 13.sp
    val SummaryTextHeight = 18.sp

    // The sheets of the form (5.29 R9, «Листы формы»): the frame of R1, «Готово» at its bottom.

    /** The value of a sheet — «28 сентября, понедельник», «До 31 декабря», «2 ч 30 мин» — 22 sp / 800; its caption 14 sp. */
    val SheetValue = 22.sp
    val SheetValueHeight = 28.sp
    val SheetValueTop = 6.dp
    val SheetCaption = 14.sp
    val SheetCaptionHeight = 20.sp
    val SheetChipsTop = 14.dp

    /** The month of a sheet with its year: 16 sp / 800, 16 over it and 6 under it; the arrows of 44 with a touch of 48. */
    val SheetMonth = 16.sp
    val SheetMonthHeight = 22.sp
    val SheetMonthTop = 16.dp
    val SheetMonthBottom = 6.dp

    /** «Весь день»: a row of 56, the switch 52 × 32, its caption 13 sp / 500. */
    val AllDayRowMin = 56.dp
    val AllDayText = 16.sp
    val AllDayTextHeight = 21.sp
    val AllDayCaption = 13.sp
    val AllDayCaptionHeight = 18.sp
    val AllDayGap = 12.dp

    /**
     * The wheels: two columns 96 × 220, 6 apart, a row of 44 — five seen; the plate of the chosen row 44 at a corner of 14 across them;
     * the chosen value 28 sp / 800, its neighbours 20 sp / 600, the colon 28 sp / 800; a fade to the colour of the sheet over a third
     * of their height at each edge.
     */
    val WheelWidth = 96.dp
    val WheelRow = 44.dp
    const val WHEEL_ROWS = 5
    val WheelGap = 6.dp
    val WheelTop = 8.dp
    val WheelBottom = 4.dp
    val WheelPlateCorner = 14.dp
    val WheelChosen = 28.sp
    val WheelNear = 20.sp
    const val WHEEL_FADE = 0.32f

    /** The wheels are long lists round and round: this many turns, the start in the middle of them. */
    const val WHEEL_TURNS = 400

    /** The end under the wheels, 14 sp; «Частое» 13 sp / 700, 14 over it; its chips not narrower than 60. */
    val WheelEnd = 14.sp
    val WheelEndHeight = 20.sp
    val FrequentTop = 14.dp
    val FrequentChipsTop = 8.dp
    val FrequentChipMin = 60.dp

    /** «Весь день» on: the words in the place of the wheels, 14 sp at 1.45. */
    val AllDayNote = 14.sp
    val AllDayNoteHeight = 20.3.sp
    val AllDayNoteTop = 12.dp

    /** «Длительность»: the stepper's number 32 sp (2 ч 30 мин does not fit 40), its hint 13 sp in the middle, 12 under it. */
    const val DURATION_VALUE_SP = 32
    val DurationStepperTop = 12.dp
    val SheetHint = 13.sp
    val SheetHintHeight = 18.85.sp
    val SheetHintTop = 12.dp

    // The sheet «Вид» (5.29 R9, «Лист «Вид»»).

    /** The preview: a plate of 56 at a corner of 18, the sign 28; the name 22 sp / 800 at −0.01 em, its caption 13 sp; the day 54 × 52. */
    val PreviewPlate = 56.dp
    val PreviewPlateCorner = 18.dp
    val PreviewSign = 28.dp
    val PreviewGap = 12.dp
    const val PREVIEW_TRACKING = -0.01f
    val PreviewCaption = 13.sp
    val PreviewCaptionHeight = 18.sp
    val PreviewCellWidth = 54.dp
    val PreviewCellHeight = 52.dp
    val PreviewTop = 12.dp

    /** «Удалить вид…» in the head of the sheet: 48, the bin 18, 15 sp / 700 in the colour of danger. */
    val KindDeleteMin = 48.dp
    val KindDeleteIcon = 18.dp
    val KindDeleteText = 15.sp

    /** Why a built-in kind is not renamed: 14 sp at 1.45. */
    val KindNote = 14.sp
    val KindNoteHeight = 20.3.sp
    val KindPartTop = 16.dp
    val KindPartBottom = 8.dp

    /**
     * The colours: a grid of 4 × 2, 8 apart in a row and 4 between the rows, a target of 48 with a circle of 36; the chosen one — a ring
     * of 3 in the colour of the sheet and one of 2 in the first level of text around the circle, a tick of 20 in the colour of the ground.
     */
    const val SWATCHES_IN_ROW = 4
    val SwatchTarget = 48.dp
    val SwatchCircle = 36.dp
    val SwatchGapH = 8.dp
    val SwatchGapV = 4.dp
    val SwatchRingInner = 3.dp
    val SwatchRingOuter = 2.dp
    val SwatchTick = 20.dp
    const val SWATCH_TICK_LINE = 2.6f

    /**
     * The signs of one's own: cells of 48 on the ground of the screen at a corner of 14, the sign 24 in the second level; the chosen one —
     * an inner ring of 2 and the sign in the colour of the kind; one another kind wears — a dot of 5, 7 from the bottom right corner.
     * Six in a row 8 apart from a row of 328, 4 apart from 308, else four in a row 8 apart ([com.violinjourney.app.feature.events.form.KindSheetMath]).
     */
    val SignCell = 48.dp
    val SignCellCorner = 14.dp
    val SignIcon = 24.dp
    val SignRing = 2.dp
    val SignDot = 5.dp
    val SignDotInset = 7.dp
    val SignRowWide = 328.dp
    val SignRowTight = 308.dp
    val SignGapWide = 8.dp
    val SignGapTight = 4.dp
    const val SIGNS_WIDE = 6
    const val SIGNS_NARROW = 4

    // The plate «что меняется» of an edit of a repeat (5.29 R9, «Листы повтора»): the ground of the screen, a corner of 14, at least 48,
    // 10 / 14 inside, 10 between its parts, 12 over it; its caption 13 sp / 700, the values 15 sp — the old one 600 in the second level,
    // the arrow 18 in the third, the new one 700 in the first.

    val ChangeMin = 48.dp
    val ChangeCorner = 14.dp
    val ChangePaddingV = 10.dp
    val ChangePaddingH = 14.dp
    val ChangeGap = 10.dp
    val ChangeTop = 12.dp
    val ChangeLabel = 13.sp
    val ChangeValue = 15.sp
    val ChangeValueHeight = 20.sp
    val ChangeArrow = 18.dp
}
