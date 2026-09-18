package com.example.violintuner.core.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number and date formats of the analysis screens. The interface is Russian only for now, so
 * the locale is fixed: month names must not follow the device language while the rest does not.
 */
object Formats {
    val LOCALE: Locale = Locale.forLanguageTag("ru")

    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val MS_PER_MINUTE = MS_PER_SECOND * SECONDS_PER_MINUTE
    private const val MINUS = '−'
    private val DAY_AND_MONTH = DateTimeFormatter.ofPattern("d MMMM", LOCALE)
    private val DAY_AND_SHORT_MONTH = DateTimeFormatter.ofPattern("d MMM", LOCALE)
    private val DAY_WITH_WEEKDAY = DateTimeFormatter.ofPattern("d MMMM, EEEE", LOCALE)
    private val MONTH_AND_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy", LOCALE)
    private val TIME_OF_DAY = DateTimeFormatter.ofPattern("HH:mm", LOCALE)

    /** "m:ss", minutes not padded: 0:07, 12:40, 60:00. */
    fun duration(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return "%d:%02d".format(LOCALE, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    /** Practice timer: "m:ss" under an hour, "h:mm:ss" from then on (spec 5.6): 12:34, 1:02:34. */
    fun timer(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        return if (hours == 0L) {
            "%d:%02d".format(LOCALE, minutes, seconds % SECONDS_PER_MINUTE)
        } else {
            "%d:%02d:%02d".format(LOCALE, hours, minutes % MINUTES_PER_HOUR, seconds % SECONDS_PER_MINUTE)
        }
    }

    /** Practice time in words, rounded to the minute (spec 5.6): "45 мин", "1 ч 25 мин", "2 ч", "0 мин". */
    fun minutesInWords(ms: Long): String {
        val minutes = (ms + MS_PER_MINUTE / 2) / MS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        return when {
            hours == 0L -> "$rest мин"
            rest == 0L -> "$hours ч"
            else -> "$hours ч $rest мин"
        }
    }

    /** Whole cents with an explicit sign and a real minus: +6, −18, 0. */
    fun signedCents(cents: Double): String {
        val rounded = cents.roundToInt()
        return when {
            rounded > 0 -> "+$rounded"
            rounded < 0 -> "$MINUS${abs(rounded)}"
            else -> "0"
        }
    }

    /** One decimal with a comma: 7,3. */
    fun oneDecimal(value: Double): String = "%.1f".format(LOCALE, value)

    /**
     * Russian plural form for [count]: 1 сессия, 2 сессии, 5 сессий, 21 сессия. Done by hand
     * because `plurals` resources follow the device language, and the interface is Russian on
     * an English phone too.
     */
    fun <T> pluralRu(count: Int, one: T, few: T, many: T): T {
        val lastTwo = abs(count) % 100
        val last = lastTwo % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }

    /** "14 сентября" */
    fun dayAndMonth(epochMs: Long, zone: ZoneId): String =
        DAY_AND_MONTH.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "13 сент": the locale's abbreviation without its trailing dot. */
    fun dayAndShortMonth(date: LocalDate): String = DAY_AND_SHORT_MONTH.format(date).trimEnd('.')

    /** "17 сентября, четверг" */
    fun dayWithWeekday(date: LocalDate): String = DAY_WITH_WEEKDAY.format(date)

    /** "Сентябрь 2026": the standalone month name, capitalised for a heading. */
    fun monthAndYear(month: YearMonth): String =
        MONTH_AND_YEAR.format(month).replaceFirstChar { it.titlecase(LOCALE) }

    /** "18:42" in the given zone. */
    fun timeOfDay(epochMs: Long, zone: ZoneId): String = TIME_OF_DAY.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
