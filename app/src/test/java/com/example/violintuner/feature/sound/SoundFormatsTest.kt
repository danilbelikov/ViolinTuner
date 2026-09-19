package com.example.violintuner.feature.sound

import com.example.violintuner.core.domain.sound.SoundUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFormatsTest {
    @Test
    fun `frequencies read in hertz below a thousand and in kilohertz above`() {
        assertEquals("80 Гц", SoundFormats.hertz(80.2))
        assertEquals("950 Гц", SoundFormats.hertz(949.6))
        assertEquals("1 кГц", SoundFormats.hertz(1_000.0))
        assertEquals("2,4 кГц", SoundFormats.hertz(2_378.0))
        assertEquals("3,2 кГц", SoundFormats.hertz(3_200.0))
        assertEquals("12 кГц", SoundFormats.hertz(12_400.0))
    }

    @Test
    fun `gains carry their sign, a true minus and halves only`() {
        assertEquals("+2,5 дБ", SoundFormats.decibels(2.5, signed = true))
        assertEquals("−18 дБ", SoundFormats.decibels(-18.0, signed = true))
        assertEquals("0 дБ", SoundFormats.decibels(0.1, signed = true))
        assertEquals("−3,5 дБ", SoundFormats.decibels(-3.4, signed = true))
        assertEquals("6 дБ", SoundFormats.decibels(6.0, signed = false))
    }

    @Test
    fun `every unit is written with what it is a number of`() {
        assertEquals("15 мс", SoundFormats.value(SoundUnit.MILLISECOND, 15.0))
        assertEquals("1,8 с", SoundFormats.value(SoundUnit.SECOND, 1.8))
        assertEquals("25 %", SoundFormats.value(SoundUnit.PERCENT, 0.25))
        assertEquals("3:1", SoundFormats.value(SoundUnit.RATIO, 3.0))
        assertEquals("3,5:1", SoundFormats.value(SoundUnit.RATIO, 3.5))
        assertEquals("1,0", SoundFormats.value(SoundUnit.WIDTH, 1.0))
    }

    @Test
    fun `the axis of the curve is labelled shortly`() {
        assertEquals("100", SoundFormats.axis(100.0))
        assertEquals("1 к", SoundFormats.axis(1_000.0))
        assertEquals("10 к", SoundFormats.axis(10_000.0))
    }
}
