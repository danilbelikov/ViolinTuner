package com.violinjourney.app.core.ui.format

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime

/**
 * Number and date formats of the app. They speak the language of the interface ([language]), not
 * of the device: the two differ when the device is set to a language the app has no words for —
 * month names must not come in Italian under English captions. The app sets the language when it
 * starts and when the configuration changes ([use]); on the JVM, in tests, it is Russian — the
 * language the formats were written and specified in.
 */
object Formats {
    @Volatile
    var language: FormatLanguage = FormatLanguage.RUSSIAN
        private set

    /** Follows the language the resources were resolved for: "ru", "de-AT", "zh-Hans-CN". */
    fun use(languageTag: String) {
        language = FormatLanguage.of(languageTag)
    }

    /** For tests of another language; give the Russian back afterwards. */
    fun use(language: FormatLanguage) {
        this.language = language
    }

    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val MS_PER_MINUTE = MS_PER_SECOND * SECONDS_PER_MINUTE
    private const val MINUS = '−'

    /** From this many hours on, progress times drop the minutes (spec 5.7). */
    private const val HOURS_ONLY_FROM = 100L
    private const val MIN_GROUPED_DIGITS = 5
    private const val GROUP_SIZE = 3

    /** No-break space: a grouped number never wraps in the middle. */
    private const val GROUP_SEPARATOR = "\u00A0"
    /** «45 мин», «45 min», «45분». */
    private fun minutes(n: Long) = "$n${language.unitSpace}${language.minute}"
    private fun hours(n: Any) = "$n${language.unitSpace}${language.hour}"

    /** "m:ss", minutes not padded: 0:07, 12:40, 60:00. */
    fun duration(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return "${seconds / SECONDS_PER_MINUTE}:${two(seconds % SECONDS_PER_MINUTE)}"
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')

    /** Practice timer: "m:ss" under an hour, "h:mm:ss" from then on (spec 5.6): 12:34, 1:02:34. */
    fun timer(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        val minutes = seconds / SECONDS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        return if (hours == 0L) {
            "$minutes:${two(seconds % SECONDS_PER_MINUTE)}"
        } else {
            "$hours:${two(minutes % MINUTES_PER_HOUR)}:${two(seconds % SECONDS_PER_MINUTE)}"
        }
    }

    /** Practice time in words, rounded to the minute (spec 5.6): "45 мин", "1 ч 25 мин", "2 ч", "0 мин". */
    fun minutesInWords(ms: Long): String {
        val minutes = (ms + MS_PER_MINUTE / 2) / MS_PER_MINUTE
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        return when {
            hours == 0L -> minutes(rest)
            rest == 0L -> hours(hours)
            else -> "${hours(hours)} ${minutes(rest)}"
        }
    }

    /**
     * Total practice time of the progress header (spec 5.7), rounded down: "0 мин", "16 ч 40 мин",
     * and hours alone from 100 h on: "1250 ч", "10 000 ч".
     */
    fun totalTime(ms: Long): String = wordsOf(ms.coerceAtLeast(0) / MS_PER_MINUTE, roundHoursUp = false)

    /**
     * Time left to a level or a trophy (spec 5.7), rounded up so that a mark not yet reached
     * never reads "0 мин"; hours alone from 100 h on.
     */
    fun remainingTime(ms: Long): String =
        wordsOf((ms.coerceAtLeast(0) + MS_PER_MINUTE - 1) / MS_PER_MINUTE, roundHoursUp = true)

    /** A mark in hours: "50 ч", "1000 ч", "10 000 ч". */
    fun hoursMark(hours: Int): String = hours(grouped(hours.toLong()))

    /** Digits in groups of three from five digits on, as Russian typography has it: 1250, 10 000. */
    fun grouped(value: Long): String {
        val digits = value.toString()
        if (digits.length < MIN_GROUPED_DIGITS) return digits
        return digits.reversed().chunked(GROUP_SIZE).joinToString(GROUP_SEPARATOR).reversed()
    }

    private fun wordsOf(minutes: Long, roundHoursUp: Boolean): String {
        val hours = minutes / MINUTES_PER_HOUR
        val rest = minutes % MINUTES_PER_HOUR
        return when {
            hours >= HOURS_ONLY_FROM -> hours(grouped(if (roundHoursUp && rest > 0) hours + 1 else hours))
            hours == 0L -> minutes(rest)
            rest == 0L -> hours(hours)
            else -> "${hours(hours)} ${minutes(rest)}"
        }
    }

    /** Whole cents with an explicit sign and a real minus: +6, −18, 0. */
    /**
     * The size of a file the way people say it (spec 3.19): «214 МБ», «1,2 ГБ», «8,5 МБ» — a
     * decimal only below ten, and nothing below a megabyte is worth more than «меньше 1 МБ».
     */
    fun fileSize(bytes: Long): String {
        val mb = bytes / BYTES_PER_MB
        return when {
            mb < 1 -> language.underMegabyte
            mb < DECIMAL_BELOW -> "${decimal(mb, 1)} ${language.megabyte}"
            mb < MB_PER_GB -> "${decimal(mb, 0)} ${language.megabyte}"
            else -> "${decimal(mb / MB_PER_GB, 1)} ${language.gigabyte}"
        }
    }

    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val MB_PER_GB = 1024.0
    private const val DECIMAL_BELOW = 10.0

    fun signedCents(cents: Double): String = CentsFormat.signed(cents)

    /** One decimal with the separator of the language: 7,3 / 7.3. */
    fun oneDecimal(value: Double): String = decimal(value, 1)

    /** [value] rounded half up to [decimals] digits, as `%.Nf` writes it, with the separator of the language. */
    fun decimal(value: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = (abs(value) * scale).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        if (decimals == 0) return "$sign$scaled"
        return "$sign${scaled / scale}${language.decimalSeparator}${(scaled % scale).toString().padStart(decimals, '0')}"
    }

    /**
     * The word for [count] out of three forms — Russian needs all three: 1 запись, 2 записи, 5 записей, 21 запись;
     * most languages of the app take [one] for a single thing and [many] otherwise (their [few] is never asked
     * for), Korean, Chinese and Japanese always [many]. Done by hand because `plurals` resources follow the
     * device, and the interface may speak another language than the device does.
     */
    fun <T> plural(count: Int, one: T, few: T, many: T): T = when (language.plural) {
        PluralRule.SLAVIC -> {
            val lastTwo = abs(count) % 100
            val last = lastTwo % 10
            when {
                lastTwo in 11..14 -> many
                last == 1 -> one
                last in 2..4 -> few
                else -> many
            }
        }
        PluralRule.ONE_OTHER -> if (abs(count) == 1) one else many
        PluralRule.ONE_UP_TO_TWO -> if (abs(count) < 2) one else many
        PluralRule.NONE -> many
    }

    /** "14 сентября" */
    fun dayAndMonth(epochMs: Long, zone: TimeZone): String = dayAndMonth(dateOf(epochMs, zone))

    /** "13 сентября": the date a trophy was given on. */
    fun dayAndMonth(date: LocalDate): String = date(language.dayMonth, date)

    /** "13 сент": the language's abbreviation without its trailing dot. */
    fun dayAndShortMonth(date: LocalDate): String = date(language.dayShortMonth, date).trimEnd('.')

    /** "17 сентября, четверг" */
    fun dayWithWeekday(date: LocalDate): String = date(language.dayMonthWeekday, date)

    /** "20 сентября"; a date of another year says which: "20 сентября 2025" (spec 3.21). */
    fun recordDate(date: LocalDate, withYear: Boolean): String = date(if (withYear) language.dayMonthYear else language.dayMonth, date)

    /** The header of a day in «Записи»: "20 сентября, воскресенье" / "20 сентября 2025, суббота". */
    fun recordDayHeader(date: LocalDate, withYear: Boolean): String =
        date(if (withYear) language.dayMonthYearWeekday else language.dayMonthWeekday, date)

    /** Takts of the journey: digits in groups of three from four digits on — «1 640», «15 000» — the way the handoff writes prices. */
    fun takts(value: Long): String {
        val digits = abs(value).toString()
        val grouped = if (digits.length < 4) digits else digits.reversed().chunked(GROUP_SIZE).joinToString(GROUP_SEPARATOR).reversed()
        return if (value < 0) "$MINUS$grouped" else grouped
    }

    /** "Сентябрь 2026": the standalone month name, capitalised for a heading. */
    fun monthAndYear(month: YearMonth): String =
        date(language.monthYear, month.firstDay).replaceFirstChar { it.titlecase() }

    /** "18:42" in the given zone. */
    fun timeOfDay(epochMs: Long, zone: TimeZone): String {
        val time = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        return "${two(time.hour.toLong())}:${two(time.minute.toLong())}"
    }

    private fun dateOf(epochMs: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date

    /** The platform writes the names of months and weekdays in the language: the patterns are the same on both. */
    private fun date(pattern: String, date: LocalDate): String = formatDate(pattern, language.tag, date)
}

/** [date] by the Unicode [pattern] ("d MMMM", "LLLL yyyy") with the month and weekday names of [languageTag]. */
internal expect fun formatDate(pattern: String, languageTag: String, date: LocalDate): String
