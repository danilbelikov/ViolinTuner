package com.example.violintuner.feature.practice

import com.example.violintuner.feature.history.HistoryCard
import java.time.LocalDate
import java.time.YearMonth

/** The three figures above the calendar; null when there has never been a practice (dashes). */
data class PracticeSummary(
    val weekMs: Long,
    val monthMs: Long,
    val streakDays: Int,
)

/** One day of the calendar grid; null cells pad the month to whole weeks. */
data class CalendarCell(
    val date: LocalDate,
    val totalMs: Long,
    /** 0 = no fill, 1–4 = tone of the fill (spec 5.6). */
    val fillLevel: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isFuture: Boolean,
)

data class SelectedDay(
    val date: LocalDate,
    val isToday: Boolean,
    val totalMs: Long,
    /** Sessions recorded on that day, newest first. */
    val sessions: List<HistoryCard>,
)

sealed interface PracticeSheet {
    /**
     * "Закончить занятие": the timed length with a chance to trim it. [minutes] is what the
     * stepper shows; until it is touched the exact [actualMs] is what gets saved.
     */
    data class Summary(
        val startedAtEpochMs: Long,
        val actualMs: Long,
        val minutes: Int,
        val minMinutes: Int,
        val maxMinutes: Int,
        val edited: Boolean,
    ) : PracticeSheet

    /** "Изменить время" of a day: the whole day's time, zero removes the day. */
    data class EditTime(
        val date: LocalDate,
        val minutes: Int,
        val maxMinutes: Int,
    ) : PracticeSheet
}

data class PracticeState(
    /** True until both the entries and the running practice have been read once. */
    val loading: Boolean,
    /** False = the empty state: no practice was ever saved (spec 3.12). */
    val hasHistory: Boolean,
    /** Null when no practice runs; otherwise how long it has been running, refreshed every second. */
    val runningMs: Long?,
    val todayMs: Long,
    val summary: PracticeSummary,
    val month: YearMonth,
    /** The calendar never goes past the current month. */
    val canGoForward: Boolean,
    /** Monday-first grid of whole weeks. */
    val cells: List<CalendarCell?>,
    val selected: SelectedDay,
    val sheet: PracticeSheet?,
    /** Whole minutes of one stepper step, from the config: the sheets word their hint with it. */
    val stepMinutes: Int,
)

sealed interface PracticeIntent {
    data object StartClicked : PracticeIntent

    data object StopClicked : PracticeIntent

    /** Stepper of the summary sheet: +1 or −1 step. */
    data class SummaryStepped(val steps: Int) : PracticeIntent

    data object SummarySaved : PracticeIntent

    data object SummaryDiscarded : PracticeIntent

    data class DaySelected(val date: LocalDate) : PracticeIntent

    data object MonthBack : PracticeIntent

    data object MonthForward : PracticeIntent

    data object EditTimeClicked : PracticeIntent

    /** Stepper of the edit sheet: +1 or −1 step. */
    data class EditTimeStepped(val steps: Int) : PracticeIntent

    /** Chips of the edit sheet: add minutes, or zero to clear. */
    data class EditTimeAdded(val minutes: Int) : PracticeIntent

    data object EditTimeCleared : PracticeIntent

    data object EditTimeSaved : PracticeIntent

    data object EditTimeCancelled : PracticeIntent

    data class SessionClicked(val id: Long) : PracticeIntent
}

sealed interface PracticeEffect {
    /** "Начать занятие" leads to Live: practising means playing. */
    data object OpenLive : PracticeEffect

    data class OpenSession(val id: Long) : PracticeEffect

    data object ShowTooShort : PracticeEffect
}
