package com.violinjourney.app.core.ui.format

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
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

    /** The same space, put in the place of the spaces of a date that must not wrap ([recordDateWhole]). */
    private const val NO_BREAK_SPACE = '\u00A0'

    /** «45 мин», «45 min», «45분». */
    private fun minutes(n: Long) = "$n${language.unitSpace}${language.minute}"
    private fun hours(n: Any) = "$n${language.unitSpace}${language.hour}"

    /**
     * The length of a recording, a video or a backing: "m:ss" under an hour, "h:mm:ss" from then on (spec 5.15) —
     * 0:07, 12:40, 1:00:00. The same clock as [timer]; a take stopped at its limit of an hour no longer reads 60:00.
     */
    fun duration(ms: Long): String = timer(ms)

    private fun two(value: Long): String = value.toString().padStart(2, '0')

    /** Practice timer: "m:ss" under an hour, "h:mm:ss" from then on (spec 5.6): 12:34, 1:02:34. Lengths ([duration]) read the same. */
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

    /**
     * [recordDate] that never breaks inside — its spaces no-break ones: a date standing in words that wrap, «3 прошло · последнее 24
     * октября» of the row «Выступления» (5.29 R9: «число и слово при этом не разрываются»). The words break before it, never in it.
     */
    fun recordDateWhole(date: LocalDate, withYear: Boolean): String = recordDate(date, withYear).replace(' ', NO_BREAK_SPACE)

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

    /**
     * The heading of the calendar (spec 3.36.2): the month alone in [currentYear] — «Сентябрь», «9月» — and with its year
     * otherwise — «Август 2025».
     */
    fun monthTitle(month: YearMonth, currentYear: Int): String =
        if (month.year == currentYear) date(language.month, month.firstDay).replaceFirstChar { it.titlecase() } else monthAndYear(month)

    /** «пн 5 окт.»: the short weekday, the day and the short month — the chip of the sheet «Дата» (spec 3.36.9). */
    fun weekdayDate(date: LocalDate): String = weekdayInside(date(language.weekdayDate, date))

    /** «Пн, 28 сент.»: the same with a comma, the first letter capital — the row of the date in the form of an event. */
    fun weekdayCommaDate(date: LocalDate): String = date(language.weekdayCommaDate, date).replaceFirstChar { it.titlecase() }

    /** «пн 19»: the short weekday and the day — the plate «что меняется» of a repeat. */
    fun weekdayDay(date: LocalDate): String = weekdayInside(date(language.weekdayDay, date))

    /** «сб 24 октября»: the short weekday, the day and the whole month. */
    fun weekdayDayMonth(date: LocalDate): String = weekdayInside(date(language.weekdayDayMonth, date))

    /**
     * «окт», «мая»: the short month alone, without the language's dot — the tile of a date in «Выступления», under its day; in Russian in
     * the form of a date ([FormatLanguage.shortMonthNames]).
     */
    fun shortMonth(date: LocalDate): String =
        language.shortMonthNames?.get(date.month.number - 1) ?: date(language.shortMonth, date).trimEnd('.')

    /** «понедельник, 28 сентября»: the whole weekday first — what TalkBack says of a date of the form (plan D46). */
    fun weekdayFullDate(date: LocalDate): String = weekdayInside(date(language.weekdayFullDate, date))

    /**
     * A date that begins with its weekday, as it stands inside a sentence — a chip, a plate, a question: the weekday small where the
     * language writes it small ([FormatLanguage.smallWeekday]), on both platforms alike — iOS would make a Russian one «Пн».
     */
    private fun weekdayInside(text: String): String = if (language.smallWeekday) text.replaceFirstChar { it.lowercase() } else text

    /** «26 окт.»: the day and the short month as the language writes them, its dot kept ([dayAndShortMonth] drops it). */
    fun shortDate(date: LocalDate): String = date(language.dayShortMonth, date)

    /**
     * Dates in a list (the answers of the sheet of a repeat, plan D31): of one month its name once — «19, 26 окт.», «19., 26. Okt.»,
     * «Oct 19, 26», «10月19日、26日»; of several months each date whole — «26 окт., 2 нояб.».
     */
    fun dateList(dates: List<LocalDate>): String {
        if (dates.isEmpty()) return ""
        val separator = language.listSeparator
        if (dates.any { it.month != dates.first().month || it.year != dates.first().year }) return dates.joinToString(separator) { shortDate(it) }
        val days = dates.map { date(language.listedDay, it) }
        return if (language.monthFirstList) {
            (listOf(shortDate(dates.first())) + days.drop(1)).joinToString(separator)
        } else {
            (days.dropLast(1) + shortDate(dates.last())).joinToString(separator)
        }
    }

    /**
     * The time of an event from its minutes after midnight (spec 3.36.9): «17:00», «09:05» — the 24-hour clock in every language, as
     * every clock of the app. A minute of the next day is read as of its own day: 25:00 is «01:00».
     */
    fun clockOf(minutes: Int): String {
        val ofDay = minutes.mod(MINUTES_PER_DAY)
        return "${two((ofDay / MINUTES_IN_HOUR).toLong())}:${two((ofDay % MINUTES_IN_HOUR).toLong())}"
    }

    /**
     * A length on a chip of the form (spec 3.36.9, 5.28): the quick chips «30 мин · 45 мин · 1 ч · 1,5 ч» — an hour and a half, the one
     * quick length that is neither minutes nor whole hours, in hours with a decimal — and every other length, one of one's own on its
     * chip with the pencil too, in words, as [minutesInWords] says it: «2 ч», «2 ч 15 мин», «2 ч 30 мин».
     */
    fun quickDuration(minutes: Int): String =
        if (minutes == HOUR_AND_A_HALF) hours(oneDecimal(minutes.toDouble() / MINUTES_IN_HOUR)) else minutesInWords(minutes * MS_PER_MINUTE)

    private const val MINUTES_IN_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_IN_HOUR

    /** The quick chip «1,5 ч» (5.28: 30 · 45 · 60 · 90). */
    private const val HOUR_AND_A_HALF = 90

    /** "18:42" in the given zone. */
    fun timeOfDay(epochMs: Long, zone: TimeZone): String {
        val time = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        return "${two(time.hour.toLong())}:${two(time.minute.toLong())}"
    }

    private fun dateOf(epochMs: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date

    /** The platform writes the names of months and weekdays in the language: the patterns are the same on both. */
    private fun date(pattern: String, date: LocalDate): String = formatDate(pattern, language.tag, date)

    /**
     * Names in the order of the alphabet of the language (spec 3.22 «свои по алфавиту», 5.21 «по названию»): «Ёлочные»
     * among the Е, before «Январь»; «Äpfel» before «Birne»; «b» before «C». Case and accents only break a tie. The
     * platform's collator: a new one on each call, for one sorting — it is not to be shared between threads.
     */
    fun alphabetical(): Comparator<String> = collatorOf(language.tag)
}

/** [date] by the Unicode [pattern] ("d MMMM", "LLLL yyyy") with the month and weekday names of [languageTag]. */
internal expect fun formatDate(pattern: String, languageTag: String, date: LocalDate): String

/** The platform's comparison of texts by the alphabet of [languageTag]. */
internal expect fun collatorOf(languageTag: String): Comparator<String>
