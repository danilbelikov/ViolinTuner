package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.PracticeStats
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.feature.history.HistoryReducer
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/** Entries and sessions → the practice screen (spec 3.12). Pure: "today" and the zone come from outside. */
object PracticeReducer {
    fun stateOf(
        entries: List<PracticeEntry>,
        sessions: List<SessionSummary>,
        /** The day the running practice began on; null while none runs. */
        runningSince: LocalDate?,
        month: YearMonth,
        /**
         * The day whose sheet is open (the sheet of the day or «Время за день» over it); null — none is selected. Never later than
         * [today]: a day to come has no sheet, and the view model closes one the date went back under — one rule, in one place.
         */
        selectedDate: LocalDate?,
        sheet: PracticeSheet?,
        today: LocalDate,
        zone: TimeZone,
        config: PracticeConfig,
        /** Takes among the day's recordings are named after their pieces and carry the «лучший» star (spec 3.21). */
        pieces: List<Piece> = emptyList(),
        trophies: List<Trophy>,
        profile: Profile,
        avatarPath: String?,
        progressConfig: ProgressConfig,
        /** The takes made under a backing (spec 3.32): their cards carry its sign, as in «Записи». */
        underBackingIds: Set<Long> = emptySet(),
        /** A practice is being saved and its recap has not opened: the gift waits for it (spec 3.31). */
        recapPending: Boolean = false,
        /** A record opened from the sheet of the day is on the screen: the sheets step aside until it is back. */
        sheetsAway: Boolean = false,
    ): PracticeState {
        val totals = PracticeStats.dayTotals(entries)
        val totalMs = Progress.totalMs(entries)
        val hasHistory = totals.values.any { it > 0 }
        val pieceTitles = pieces.associate { it.id to it.title }
        val bestTakeIds = pieces.mapNotNull { it.bestTakeId }.toSet()
        return PracticeState(
            loading = false,
            hasHistory = hasHistory,
            runningSince = runningSince,
            today = today,
            todayMs = totals[today] ?: 0L,
            summary = PracticeSummary(
                weekMs = PracticeStats.weekTotal(totals, today),
                monthMs = PracticeStats.monthTotal(totals, month),
                streakDays = PracticeStats.streak(totals, today),
                weekDaysMs = PracticeStats.weekDays(totals, today),
                monthDays = PracticeStats.monthDays(totals, month),
            ),
            month = month,
            canGoForward = month < today.yearMonth,
            cells = PracticeStats.calendarCells(month).map { date ->
                date?.let {
                    val total = totals[it] ?: 0L
                    CalendarCell(
                        date = it,
                        totalMs = total,
                        fillLevel = PracticeStats.fillLevel(total, config),
                        isToday = it == today,
                        isSelected = it == selectedDate,
                        isFuture = it > today,
                    )
                }
            },
            selected = selectedDate?.let { date ->
                SelectedDay(
                    date = date,
                    isToday = date == today,
                    totalMs = totals[date] ?: 0L,
                    sessions = sessions
                        .filter { Instant.fromEpochMilliseconds(it.startedAtEpochMs).toLocalDateTime(zone).date == date }
                        .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
                        .map {
                            HistoryReducer.cardOf(
                                it, today, zone, pieceTitle = it.pieceId?.let(pieceTitles::get), best = it.id in bestTakeIds, underBacking = it.id in underBackingIds,
                            )
                        },
                )
            },
            header = ProgressReducer.headerOf(totalMs, trophies, profile.name, avatarPath, progressConfig),
            trophies = ProgressReducer.trophyLines(totalMs, trophies, progressConfig),
            gift = if (sheet == null && !recapPending) ProgressReducer.giftOf(trophies, progressConfig, totalMs) else null,
            sheet = sheet,
            sheetsAway = sheetsAway,
            stepMinutes = config.editStepMinutes,
            weekFloorMinutes = weekFloorOf(config),
            recapPending = recapPending,
        )
    }

    /** The block of the running practice under «Занятие идёт»: minutes left, or «готово» once it reached its goal. */
    fun runningBlockOf(running: RunningPractice?, blocks: PracticeBlocks?, titles: Map<Long, String>, nowEpochMs: Long): RunningBlockLine? {
        val current = BlockRules.ofPractice(running, blocks)?.current ?: return null
        val title = titles[current.pieceId] ?: return null
        return RunningBlockLine(title, minutesLeft = BlockRules.minutesLeft(current, nowEpochMs).takeIf { BlockRules.isRunning(current, nowEpochMs) })
    }

    fun loading(today: LocalDate, config: PracticeConfig, progressConfig: ProgressConfig): PracticeState = PracticeState(
        loading = true,
        hasHistory = false,
        runningSince = null,
        today = today,
        todayMs = 0,
        summary = PracticeSummary(0, 0, 0, weekDaysMs = List(DAYS_PER_WEEK) { 0L }, monthDays = 0),
        month = today.yearMonth,
        canGoForward = false,
        cells = emptyList(),
        selected = null,
        header = ProgressReducer.headerOf(0, emptyList(), name = "", avatarPath = null, progressConfig),
        trophies = emptyList(),
        gift = null,
        sheet = null,
        stepMinutes = config.editStepMinutes,
        weekFloorMinutes = weekFloorOf(config),
    )

    /** The week bars are never scaled below the highest step of the calendar (5.6, 5.29). */
    private fun weekFloorOf(config: PracticeConfig): Int = config.fillLevelMinutes.last()

    private const val DAYS_PER_WEEK = 7

    /**
     * The summary sheet for a practice that ran [actualMs] (spec 3.12: trim from 5 min to the actual length), with
     * «Что играли» — the [blocks] of the practice under the names in [titles] (spec 3.28).
     */
    fun summarySheet(
        startedAtEpochMs: Long,
        actualMs: Long,
        config: PracticeConfig,
        blocks: PracticeBlocks? = null,
        titles: Map<Long, String> = emptyMap(),
    ): PracticeSheet.Summary {
        val actualMinutes = wholeMinutes(actualMs)
        return PracticeSheet.Summary(
            startedAtEpochMs = startedAtEpochMs,
            actualMs = actualMs,
            minutes = actualMinutes,
            minMinutes = minOf(config.minEditableMinutes, actualMinutes),
            maxMinutes = actualMinutes,
            edited = false,
            floorMinutes = config.minEditableMinutes,
            blocks = blocks,
            titles = titles,
        ).withPlayed(config)
    }

    /** The stepper of the summary: back where it started, the sheet saves the exact time again, not its rounding. */
    fun step(sheet: PracticeSheet.Summary, steps: Int, config: PracticeConfig): PracticeSheet.Summary {
        val minutes = (sheet.minutes + steps * config.editStepMinutes).coerceIn(sheet.minMinutes, sheet.maxMinutes)
        return sheet.copy(minutes = minutes, edited = minutes != wholeMinutes(sheet.actualMs)).withPlayed(config)
    }

    /**
     * «Что играли» for the length the sheet would save (spec 5.21, 3.36.3), in the order they were played: the blocks of the whole
     * length, each as the stepper leaves it — one cut short loses its tick, one cut off whole (begun no earlier than the new end, or
     * left shorter than a minute) stays as «не вошёл». A block that would not be kept at the whole length is not listed at all.
     */
    fun playedOf(sheet: PracticeSheet.Summary, config: PracticeConfig): List<PlayedLine> {
        val kept = BlockRules.played(sheet.blocks, sheet.startedAtEpochMs + durationToSave(sheet), config)
            .associateBy { it.startedAtEpochMs to it.pieceId }
        return wholeOf(sheet, config).mapNotNull { block ->
            sheet.titles[block.pieceId]?.let { title ->
                kept[block.startedAtEpochMs to block.pieceId]?.let { lineOf(it, title) }
                    ?: PlayedLine(title, minutes = 0, goalMinutes = goalMinutesOf(block), done = false, dropped = true)
            }
        }
    }

    /** The blocks of the practice at its whole length, the stepper untouched. */
    private fun wholeOf(sheet: PracticeSheet.Summary, config: PracticeConfig) =
        BlockRules.played(sheet.blocks, sheet.startedAtEpochMs + sheet.actualMs, config)

    private fun lineOf(block: BlockRules.Played, title: String) =
        PlayedLine(title, minutes = wholeMinutes(block.durationMs), goalMinutes = goalMinutesOf(block), done = block.done)

    private fun goalMinutesOf(block: BlockRules.Played): Int = (block.goalMs / MS_PER_MINUTE).toInt()

    /** «Что играли» and whether the chosen length changed it from the whole one (spec 3.36.3: the hint about the blocks). */
    private fun PracticeSheet.Summary.withPlayed(config: PracticeConfig): PracticeSheet.Summary {
        val played = playedOf(this, config)
        val whole = wholeOf(this, config).mapNotNull { block -> titles[block.pieceId]?.let { lineOf(block, it) } }
        return copy(played = played, playedCut = played != whole)
    }

    /** «17:55 — 18:42», and «· было 47 мин» once the stepper moved (spec 3.36.3): the end is the start and what the sheet would save. */
    fun spanOf(sheet: PracticeSheet.Summary): SummarySpan = SummarySpan(
        startEpochMs = sheet.startedAtEpochMs,
        endEpochMs = sheet.startedAtEpochMs + durationToSave(sheet),
        wasMs = sheet.actualMs.takeIf { sheet.edited },
    )

    /**
     * The length «Сохранить» names — «Сохранить 37 мин», exactly what goes into the calendar — only while the number is below the
     * one it opened with (spec 3.36.3); null — just «Сохранить».
     */
    fun saveLengthMs(sheet: PracticeSheet.Summary): Long? = durationToSave(sheet).takeIf { sheet.edited }

    /** Which hint stands under the stepper (spec 3.36.3): a practice too short to trim, blocks cut, or the usual one. */
    fun hintOf(sheet: PracticeSheet.Summary): SummaryHint = when {
        sheet.minMinutes >= sheet.maxMinutes -> SummaryHint.TooShort
        sheet.playedCut -> SummaryHint.Cut
        else -> SummaryHint.Forgot
    }

    /**
     * Every hint [hintOf] can give this sheet while its stepper moves (5.29 R3: the stepper does not jump): the sheet keeps the place of
     * the tallest of them. A sheet too short to trim has its one hint; the others the usual one, and the one about the blocks when
     * «Что играли» has lines the stepper can cut.
     */
    fun hintsOf(sheet: PracticeSheet.Summary): List<SummaryHint> = when {
        sheet.minMinutes >= sheet.maxMinutes -> listOf(SummaryHint.TooShort)
        sheet.played.isEmpty() -> listOf(SummaryHint.Forgot)
        else -> listOf(SummaryHint.Forgot, SummaryHint.Cut)
    }

    /**
     * What stands under the number of «Время за день» (spec 3.36.3): an empty day is «занятие без телефона» at any number; a day
     * with time says «было …» — its real sum — once the number moved from the one it opened at.
     */
    fun dayCaptionOf(sheet: PracticeSheet.EditTime): DayCaption = when {
        sheet.dayTotalMs == 0L -> DayCaption.NoPhone
        sheet.minutes != sheet.initialMinutes -> DayCaption.Was(sheet.dayTotalMs)
        else -> DayCaption.None
    }

    /**
     * What the summary sheet saves: the exact timed length unless the stepper moved it — then whole minutes, but never
     * more than was played (spec 3.12: from 5 min to the actual length; the rounding of 47:40 is 48).
     */
    fun durationToSave(sheet: PracticeSheet.Summary): Long =
        if (sheet.edited) minOf(sheet.minutes * MS_PER_MINUTE, sheet.actualMs) else sheet.actualMs

    /** «Время за день» of [date] whose time is [totalMs]: at that time, or at twelve hours when it is more (5.6), knowing the real sum. */
    fun editSheet(date: LocalDate, totalMs: Long, config: PracticeConfig): PracticeSheet.EditTime {
        val minutes = wholeMinutes(totalMs).coerceAtMost(config.maxDayMinutes)
        return PracticeSheet.EditTime(date, minutes = minutes, maxMinutes = config.maxDayMinutes, initialMinutes = minutes, dayTotalMs = totalMs)
    }

    fun step(sheet: PracticeSheet.EditTime, steps: Int, config: PracticeConfig): PracticeSheet.EditTime =
        sheet.copy(minutes = (sheet.minutes + steps * config.editStepMinutes).coerceIn(0, sheet.maxMinutes))

    fun add(sheet: PracticeSheet.EditTime, minutes: Int): PracticeSheet.EditTime =
        sheet.copy(minutes = (sheet.minutes + minutes).coerceIn(0, sheet.maxMinutes))

    /** Rounded to the minute, like [com.violinjourney.app.core.ui.format.Formats.minutesInWords] shows it. */
    fun wholeMinutes(ms: Long): Int = (ms.toDouble() / MS_PER_MINUTE).roundToInt()
}
