package com.violinjourney.app.core.domain.sound

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class SoundPresetsTest {
    private val config = SoundConfig()

    @Test
    fun `«Без обработки» is the recording itself — every other preset does something`() {
        assertTrue(SoundRules.isNeutral(SoundPresets.settingsOf(BuiltInPreset.OFF, config)))
        BuiltInPreset.entries.filter { it != BuiltInPreset.OFF }.forEach {
            assertFalse(SoundRules.isNeutral(SoundPresets.settingsOf(it, config)), it.name)
        }
    }

    @Test
    fun `every preset lies within the ranges and is told apart from the others`() {
        val all = BuiltInPreset.entries.map { SoundPresets.settingsOf(it, config) }
        all.forEach { assertEquals(it, SoundRules.clean(it, config)) }
        assertEquals(all.size, all.toSet().size)
        assertEquals(BuiltInPreset.entries.map { it.id }.size, BuiltInPreset.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `settings are recognised as their preset until a single number moves`() {
        val chamber = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
        assertEquals(BuiltInPreset.CHAMBER_HALL, SoundPresets.matching(chamber, config))
        assertNull(SoundPresets.matching(chamber.copy(output = chamber.output.copy(gainDb = 2.5)), config))
    }

    @Test
    fun `the halls ring as the spec says`() {
        val chamber = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config).reverb
        assertEquals(ReverbSpace.HALL, chamber.space)
        assertEquals(1.8, chamber.decaySec, 0.0)
        assertEquals(0.25, chamber.mix, 0.0)
        val grand = SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config).reverb
        assertEquals(3.0, grand.decaySec, 0.0)
        assertEquals(30.0, grand.preDelayMs, 0.0)
        assertEquals(ReverbSpace.ROOM, SoundPresets.settingsOf(BuiltInPreset.WARM, config).reverb.space)
    }
}
