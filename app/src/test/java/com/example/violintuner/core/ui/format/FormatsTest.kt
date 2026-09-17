package com.example.violintuner.core.ui.format

import java.time.LocalDateTime
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
}
