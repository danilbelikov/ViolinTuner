package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The texts a slider of «Звук» keeps the room of at the right of its name (spec 5.29 R5): the widest its value can take — the ends of
 * its range and a step within each, where a decimal comes in; kilohertz with a decimal where the range reaches a kilohertz. In the
 * language of the formats of the tests, Russian.
 */
class SliderValuesTest {
    private val config = SoundConfig()

    private fun reserve(param: SoundParam) = SliderValues.of(param, when (param) {
        SoundParam.BODY_GAIN -> config.eqGainDb
        SoundParam.BODY_HZ -> config.bodyHz
        SoundParam.LOW_HZ -> config.lowHz
        SoundParam.COMP_RATIO -> config.ratio
        SoundParam.COMP_RELEASE -> config.releaseMs
        else -> error("not asked here")
    })

    @Test
    fun `a gain keeps the room of its ends and of a half decibel within them`() {
        val texts = reserve(SoundParam.BODY_GAIN)
        listOf("−12$NBSP", "+12$NBSP", "−11,5$NBSP", "+11,5$NBSP").forEach { number ->
            assertTrue(texts.contains("${number}дБ"), "«${number}дБ» among $texts")
        }
    }

    @Test
    fun `a ratio keeps the room of a half within its end`() {
        assertTrue(reserve(SoundParam.COMP_RATIO).contains("9,5:1"), "«9,5:1» among ${reserve(SoundParam.COMP_RATIO)}")
    }

    @Test
    fun `a frequency that reaches a kilohertz keeps the room of kilohertz with a decimal`() {
        assertTrue(reserve(SoundParam.BODY_HZ).contains("1,1${NBSP}кГц"), "kilohertz with a decimal among ${reserve(SoundParam.BODY_HZ)}")
        assertTrue(reserve(SoundParam.LOW_HZ).none { it.endsWith("кГц") }, "no kilohertz below them: ${reserve(SoundParam.LOW_HZ)}")
    }

    @Test
    fun `whole milliseconds keep the room of their longest end`() {
        val texts = reserve(SoundParam.COMP_RELEASE)
        assertTrue(texts.contains("1000${NBSP}мс"), "the longest end among $texts")
        assertTrue(texts.none { it.contains(',') }, "no decimal in whole milliseconds: $texts")
    }

    @Test
    fun `the backing keeps the room of its level and of its shift`() {
        val backing = BackingConfig()
        assertTrue(SliderValues.ofBackingGain(backing).contains("−23,5${NBSP}дБ"), "a half decibel within the lowest level: ${SliderValues.ofBackingGain(backing)}")
        assertEquals(listOf("−2000${NBSP}мс", "+2000${NBSP}мс"), SliderValues.ofBackingOffset(backing))
    }

    private companion object {
        const val NBSP = ' '
    }
}
