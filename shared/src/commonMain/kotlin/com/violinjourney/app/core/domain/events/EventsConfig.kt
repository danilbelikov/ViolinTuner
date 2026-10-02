package com.violinjourney.app.core.domain.events

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Every number of the events, with the starting values of spec 5.28 and what 3.36.9 adds in «Меняет» (plan, section
 * 9.1). Sizes and durations of the screens are not here: they are `EventsDimens` and `EventsMotion` (spec 5.29 R9).
 */
data class EventsConfig(
    /** Lengths in characters as a person counts them; spaces at the edges are cut (spec 5.28). */
    val maxTitleLength: Int = 80,
    val maxPlaceLength: Int = 60,
    val maxNotesLength: Int = 2_000,
    val maxKindNameLength: Int = 24,
    /** Kinds of one's own; the four built-in ones are not counted (spec 5.28). */
    val maxCustomKinds: Int = 20,
    /** The start of an event moves by this many minutes (spec 5.28). */
    val startStepMinutes: Int = 5,
    val durationStepMinutes: Int = 15,
    val minDurationMinutes: Int = 15,
    /** Eight hours (spec 5.28): a whole day is «весь день», not a length. */
    val maxDurationMinutes: Int = 480,
    /** The chips of the form: 30 мин · 45 мин · 1 ч · 1,5 ч (spec 5.28, 3.36.9). */
    val quickDurationsMinutes: List<Int> = listOf(30, 45, 60, 90),
    /** Where the sheet «Длительность» starts for an event without a length (spec 3.36.9). */
    val durationSheetStartMinutes: Int = 60,
    /** How long an event without a length is taken to last: its end is the start and this (spec 5.28). */
    val lengthWithoutDurationMinutes: Int = 60,
    /** Rows of the reminder on «Занятия»: of the full card, and of the compact one (spec 5.28, 3.36.9). */
    val reminderRows: Int = 2,
    val compactReminderRows: Int = 1,
    /** An event comes to the reminder at 00:00 this many days before its own (spec 5.28). */
    val reminderFromDaysBefore: Int = 1,
    /** The events of a repeat are laid ahead up to today and this many weeks (spec 5.28). */
    val seriesHorizonWeeks: Int = 12,
    /** The calendar goes forward this many months from the current one, or further to the month of the farthest event. */
    val calendarMonthsAhead: Int = 12,
    /** The sheet «Дата» of the form goes forward this many months from the current one (spec 3.36.9). */
    val formMonthsAhead: Int = 12,
    /** The chips of the end of a repeat: the end of the year and of the school year, the nearest after its first event. */
    val untilChipDates: List<YearDay> = listOf(YearDay(month = 12, day = 31), YearDay(month = 5, day = 31)),
    /** Marks in a cell of the calendar; further events are «+» (spec 5.28). */
    val cellMarks: Int = 3,
    /** Colours of the set (spec 5.29 R9); a kind keeps a number below this. */
    val colorCount: Int = 8,
    /** The colours of the built-in kinds until they are given others: Синий, Бирюза, Роза, Лайм (spec 5.29 R9). */
    val defaultColors: Map<BuiltInKind, Int> = mapOf(
        BuiltInKind.LESSON to 0,
        BuiltInKind.REHEARSAL to 5,
        BuiltInKind.PERFORMANCE to 2,
        BuiltInKind.OTHER to 7,
    ),
) {
    /** The colour a built-in kind has until it is given another. */
    fun defaultColorOf(kind: BuiltInKind): Int = defaultColors[kind] ?: 0
}

/** A day of every year — 31 December, 31 May; kotlinx-datetime has no `MonthDay`. */
data class YearDay(val month: Int, val day: Int) {
    /** This day in [year]; a day the month does not have in that year — 29 February — is its last day. */
    fun inYear(year: Int): LocalDate {
        val first = LocalDate(year, month, 1)
        val daysInMonth = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
        return LocalDate(year, month, day.coerceIn(1, daysInMonth))
    }

    /** The nearest of these days strictly after [date]. */
    fun nextAfter(date: LocalDate): LocalDate {
        val inSameYear = inYear(date.year)
        return if (inSameYear > date) inSameYear else inYear(date.year + 1)
    }
}
