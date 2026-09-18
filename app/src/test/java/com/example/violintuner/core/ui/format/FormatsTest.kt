package com.example.violintuner.core.ui.format

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatsTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private fun epoch(dateTime: String) = LocalDateTime.parse(dateTime).atZone(moscow).toInstant().toEpochMilli()

    @Test
    fun `duration is minutes and padded seconds`() {
        assertEquals("0:00", Formats.duration(0))
        assertEquals("0:07", Formats.duration(7_900))
        assertEquals("12:40", Formats.duration(760_000))
        assertEquals("60:00", Formats.duration(3_600_000))
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
        assertEquals("17 сентября, четверг", Formats.dayWithWeekday(LocalDate.of(2026, 9, 17)))
        assertEquals("3 сентября, четверг", Formats.dayWithWeekday(LocalDate.of(2026, 9, 3)))
        assertEquals("Сентябрь 2026", Formats.monthAndYear(YearMonth.of(2026, 9)))
        assertEquals("Май 2027", Formats.monthAndYear(YearMonth.of(2027, 5)))
        assertEquals("18:42", Formats.timeOfDay(epoch("2026-09-17T18:42:10"), moscow))
        assertEquals("08:05", Formats.timeOfDay(epoch("2026-09-17T08:05:00"), moscow))
    }
}
