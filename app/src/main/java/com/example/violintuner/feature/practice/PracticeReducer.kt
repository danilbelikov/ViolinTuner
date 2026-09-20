package com.example.violintuner.feature.practice

import com.example.violintuner.core.domain.repertoire.Piece
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.practice.PracticeEntry
import com.example.violintuner.core.domain.practice.PracticeStats
import com.example.violintuner.core.domain.progress.Profile
import com.example.violintuner.core.domain.progress.Progress
import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.Trophy
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.feature.history.HistoryReducer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** Entries and sessions → the practice screen (spec 3.12). Pure: "today" and the zone come from outside. */
object PracticeReducer {
    fun stateOf(
        entries: List<PracticeEntry>,
        sessions: List<SessionSummary>,
        runningMs: Long?,
        month: YearMonth,
        selectedDate: LocalDate,
        sheet: PracticeSheet?,
        today: LocalDate,
        zone: ZoneId,
        config: PracticeConfig,
        /** Takes among the day's recordings are named after their pieces and carry the «лучший» star (spec 3.21). */
        pieces: List<Piece> = emptyList(),
        trophies: List<Trophy>,
        profile: Profile,
        avatarPath: String?,
        progressConfig: ProgressConfig,
    ): PracticeState {
        val totals = PracticeStats.dayTotals(entries)
        val totalMs = Progress.totalMs(entries)
        val hasHistory = totals.values.any { it > 0 }
        val pieceTitles = pieces.associate { it.id to it.title }
        val bestTakeIds = pieces.mapNotNull { it.bestTakeId }.toSet()
        return PracticeState(
            loading = false,
            hasHistory = hasHistory,
            runningMs = runningMs,
            todayMs = totals[today] ?: 0L,
            summary = PracticeSummary(
                weekMs = PracticeStats.weekTotal(totals, today),
                monthMs = PracticeStats.monthTotal(totals, month),
                streakDays = PracticeStats.streak(totals, today),
            ),
            month = month,
            canGoForward = month < YearMonth.from(today),
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
            selected = SelectedDay(
                date = selectedDate,
                isToday = selectedDate == today,
                totalMs = totals[selectedDate] ?: 0L,
                sessions = sessions
                    .filter { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() == selectedDate }
                    .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
                    .map { HistoryReducer.cardOf(it, today, zone, pieceTitle = it.pieceId?.let(pieceTitles::get), best = it.id in bestTakeIds) },
            ),
            header = ProgressReducer.headerOf(totalMs, trophies, profile.name, avatarPath, progressConfig),
            trophies = ProgressReducer.trophyLines(totalMs, trophies, progressConfig),
            gift = if (sheet == null) ProgressReducer.giftOf(trophies, progressConfig) else null,
            sheet = sheet,
            stepMinutes = config.editStepMinutes,
        )
    }

    fun loading(today: LocalDate, config: PracticeConfig, progressConfig: ProgressConfig): PracticeState = PracticeState(
        loading = true,
        hasHistory = false,
        runningMs = null,
        todayMs = 0,
        summary = PracticeSummary(0, 0, 0),
        month = YearMonth.from(today),
        canGoForward = false,
        cells = emptyList(),
        selected = SelectedDay(today, isToday = true, totalMs = 0, sessions = emptyList()),
        header = ProgressReducer.headerOf(0, emptyList(), name = "", avatarPath = null, progressConfig),
        trophies = emptyList(),
        gift = null,
        sheet = null,
        stepMinutes = config.editStepMinutes,
    )

    /** The summary sheet for a practice that ran [actualMs] (spec 3.12: trim from 5 min to the actual length). */
    fun summarySheet(startedAtEpochMs: Long, actualMs: Long, config: PracticeConfig): PracticeSheet.Summary {
        val actualMinutes = wholeMinutes(actualMs)
        return PracticeSheet.Summary(
            startedAtEpochMs = startedAtEpochMs,
            actualMs = actualMs,
            minutes = actualMinutes,
            minMinutes = minOf(config.minEditableMinutes, actualMinutes),
            maxMinutes = actualMinutes,
            edited = false,
        )
    }

    fun step(sheet: PracticeSheet.Summary, steps: Int, config: PracticeConfig): PracticeSheet.Summary {
        val minutes = (sheet.minutes + steps * config.editStepMinutes).coerceIn(sheet.minMinutes, sheet.maxMinutes)
        return sheet.copy(minutes = minutes, edited = true)
    }

    /** What the summary sheet saves: the exact timed length unless the stepper was used. */
    fun durationToSave(sheet: PracticeSheet.Summary): Long =
        if (sheet.edited) sheet.minutes * MS_PER_MINUTE else sheet.actualMs

    fun editSheet(date: LocalDate, totalMs: Long, config: PracticeConfig): PracticeSheet.EditTime =
        PracticeSheet.EditTime(date, minutes = wholeMinutes(totalMs).coerceAtMost(config.maxDayMinutes), maxMinutes = config.maxDayMinutes)

    fun step(sheet: PracticeSheet.EditTime, steps: Int, config: PracticeConfig): PracticeSheet.EditTime =
        sheet.copy(minutes = (sheet.minutes + steps * config.editStepMinutes).coerceIn(0, sheet.maxMinutes))

    fun add(sheet: PracticeSheet.EditTime, minutes: Int): PracticeSheet.EditTime =
        sheet.copy(minutes = (sheet.minutes + minutes).coerceIn(0, sheet.maxMinutes))

    /** Rounded to the minute, like [com.example.violintuner.core.ui.format.Formats.minutesInWords] shows it. */
    fun wholeMinutes(ms: Long): Int = (ms.toDouble() / MS_PER_MINUTE).roundToInt()
}
