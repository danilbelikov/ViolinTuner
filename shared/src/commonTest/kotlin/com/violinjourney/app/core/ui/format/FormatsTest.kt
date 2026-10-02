package com.violinjourney.app.core.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.assertEquals
import kotlin.test.Test

class FormatsTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private fun epoch(dateTime: String) = LocalDateTime.parse(dateTime).toInstant(moscow).toEpochMilliseconds()

    @Test
    fun `duration is minutes and padded seconds and grows an hour field from an hour on`() {
        assertEquals("0:00", Formats.duration(0))
        assertEquals("0:07", Formats.duration(7_900))
        assertEquals("12:40", Formats.duration(760_000))
        assertEquals("59:59", Formats.duration(3_599_999))
        // from an hour on the length grows an hour field (spec 5.15), as the practice timer does
        assertEquals("1:00:00", Formats.duration(3_600_000))
        assertEquals("1:02:34", Formats.duration(3_754_000))
    }

    @Test
    fun `cents carry an explicit sign and a real minus`() {
        assertEquals("+6", Formats.signedCents(5.6))
        assertEquals("−18", Formats.signedCents(-18.2))
        assertEquals("0", Formats.signedCents(-0.4))
    }

    @Test
    fun `one decimal uses a comma`() {
        assertEquals("7,3", Formats.oneDecimal(7.26))
        assertEquals("0,0", Formats.oneDecimal(0.0))
    }

    @Test
    fun `dates are russian whatever the device language`() {
        assertEquals("14 сентября", Formats.dayAndMonth(epoch("2026-09-14T10:00:00"), moscow))
        assertEquals("3 мая", Formats.dayAndMonth(epoch("2026-05-03T23:59:00"), moscow))
    }

    @Test
    fun `practice timer grows an hour field after sixty minutes`() {
        assertEquals("0:00", Formats.timer(0))
        assertEquals("12:34", Formats.timer(754_000))
        assertEquals("59:59", Formats.timer(3_599_999))
        assertEquals("1:00:00", Formats.timer(3_600_000))
        assertEquals("1:02:34", Formats.timer(3_754_000))
        assertEquals("12:00:00", Formats.timer(43_200_000))
    }

    @Test
    fun `practice time in words drops empty parts and rounds to the minute`() {
        assertEquals("0 мин", Formats.minutesInWords(0))
        assertEquals("45 мин", Formats.minutesInWords(45 * 60_000L))
        assertEquals("1 ч 25 мин", Formats.minutesInWords(85 * 60_000L))
        assertEquals("2 ч", Formats.minutesInWords(120 * 60_000L))
        assertEquals("47 мин", Formats.minutesInWords(47 * 60_000L + 29_999))
        assertEquals("48 мин", Formats.minutesInWords(47 * 60_000L + 30_000))
        assertEquals("1 ч", Formats.minutesInWords(59 * 60_000L + 45_000))
    }

    @Test
    fun `practice dates are russian with the weekday and a capitalised month`() {
        assertEquals("17 сентября, четверг", Formats.dayWithWeekday(LocalDate(2026, 9, 17)))
        assertEquals("3 сентября, четверг", Formats.dayWithWeekday(LocalDate(2026, 9, 3)))
        assertEquals("Сентябрь 2026", Formats.monthAndYear(YearMonth(2026, 9)))
        assertEquals("Май 2027", Formats.monthAndYear(YearMonth(2027, 5)))
        assertEquals("18:42", Formats.timeOfDay(epoch("2026-09-17T18:42:10"), moscow))
        assertEquals("08:05", Formats.timeOfDay(epoch("2026-09-17T08:05:00"), moscow))
    }

    @Test
    fun `the heading of the calendar has the year only in another year`() {
        assertEquals("Сентябрь", Formats.monthTitle(YearMonth(2026, 9), currentYear = 2026))
        assertEquals("Январь", Formats.monthTitle(YearMonth(2026, 1), currentYear = 2026))
        assertEquals("Август 2025", Formats.monthTitle(YearMonth(2025, 8), currentYear = 2026))
        assertEquals("Декабрь 2025", Formats.monthTitle(YearMonth(2025, 12), currentYear = 2026))
        assertEquals("Май 2027", Formats.monthTitle(YearMonth(2027, 5), currentYear = 2026))
    }

    @Test
    fun `the total time rounds down and drops minutes from a hundred hours`() {
        assertEquals("0 мин", Formats.totalTime(0))
        assertEquals("0 мин", Formats.totalTime(59_999))
        assertEquals("59 мин", Formats.totalTime(59 * MINUTE + 59_999))
        assertEquals("16 ч 40 мин", Formats.totalTime(1000 * MINUTE))
        assertEquals("2 ч", Formats.totalTime(120 * MINUTE))
        assertEquals("99 ч 59 мин", Formats.totalTime(100 * HOUR - 1))
        assertEquals("100 ч", Formats.totalTime(100 * HOUR))
        assertEquals("1250 ч", Formats.totalTime(1250 * HOUR + 59 * MINUTE))
        assertEquals("10\u00A0000 ч", Formats.totalTime(10_000 * HOUR))
    }

    @Test
    fun `what is left rounds up — so a mark not reached is never zero`() {
        assertEquals("0 мин", Formats.remainingTime(0))
        assertEquals("1 мин", Formats.remainingTime(1))
        assertEquals("8 ч 20 мин", Formats.remainingTime(500 * MINUTE))
        assertEquals("2 ч", Formats.remainingTime(2 * HOUR))
        assertEquals("1 ч", Formats.remainingTime(59 * MINUTE + 1))
        assertEquals("99 ч 59 мин", Formats.remainingTime(100 * HOUR - MINUTE))
        assertEquals("750 ч", Formats.remainingTime(749 * HOUR + 1))
        assertEquals("750 ч", Formats.remainingTime(750 * HOUR))
    }

    @Test
    fun `marks group their digits from five digits on`() {
        assertEquals("1 ч", Formats.hoursMark(1))
        assertEquals("1000 ч", Formats.hoursMark(1000))
        assertEquals("2500 ч", Formats.hoursMark(2500))
        assertEquals("10\u00A0000 ч", Formats.hoursMark(10_000))
        assertEquals("1\u00A0234\u00A0567", Formats.grouped(1_234_567))
    }

    @Test
    fun `a trophy date is the day and the month in words`() {
        assertEquals("13 сентября", Formats.dayAndMonth(LocalDate(2026, 9, 13)))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }

    @Test
    fun `a file size is said the way people say it`() {
        assertEquals("меньше 1 МБ", Formats.fileSize(300_000))
        assertEquals("1,2 МБ", Formats.fileSize(1_287_395))
        assertEquals("8,5 МБ", Formats.fileSize((8.5 * 1024 * 1024).toLong()))
        assertEquals("214 МБ", Formats.fileSize(214L * 1024 * 1024))
        assertEquals("1,5 ГБ", Formats.fileSize(1536L * 1024 * 1024))
    }

    /** The time of an event from its minutes after midnight (spec 3.36.9): the 24-hour clock in every language; a minute past midnight is of its day. */
    @Test
    fun `the clock of an event is twenty four hours and two digits`() {
        assertEquals("00:00", Formats.clockOf(0))
        assertEquals("00:05", Formats.clockOf(5))
        assertEquals("17:15", Formats.clockOf(17 * 60 + 15))
        assertEquals("23:59", Formats.clockOf(23 * 60 + 59))
        assertEquals("01:00", Formats.clockOf(25 * 60), "the end of 23:30 and an hour and a half")
        Formats.use(FormatLanguage.ENGLISH)
        try {
            assertEquals("17:15", Formats.clockOf(17 * 60 + 15), "no AM and PM: every clock of the app")
        } finally {
            Formats.use(FormatLanguage.RUSSIAN)
        }
    }

    /**
     * The chips of the length of an event (spec 3.36.9): the quick ones «30 мин · 45 мин · 1 ч · 1,5 ч»; a length of one's own on the
     * chip with the pencil in words, as the sheet «Длительность» says it — «2 ч 30 мин» (events-form.html, 1 and 4), not «2,5 ч».
     */
    @Test
    fun `a quick length is the words of the chips and a length of ones own is said in words`() {
        assertEquals(listOf("30 мин", "45 мин", "1 ч", "1,5 ч"), listOf(30, 45, 60, 90).map(Formats::quickDuration))
        assertEquals(listOf("2 ч 30 мин", "2 ч 15 мин", "2 ч", "3 ч 30 мин", "15 мин", "8 ч"), listOf(150, 135, 120, 210, 15, 480).map(Formats::quickDuration))
        Formats.use(FormatLanguage.ENGLISH)
        try {
            assertEquals(listOf("30 min", "45 min", "1 h", "1.5 h", "2 h 30 min", "2 h 15 min"), listOf(30, 45, 60, 90, 150, 135).map(Formats::quickDuration))
        } finally {
            Formats.use(FormatLanguage.RUSSIAN)
        }
    }
}
