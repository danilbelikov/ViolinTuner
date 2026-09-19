package com.example.violintuner.core.data.sound

import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.FakeSoundRepository
import com.example.violintuner.core.domain.sound.ReverbSpace
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.domain.sound.SoundRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMapperTest {
    private val config = SoundConfig()

    @Test
    fun `every preset survives the way into columns and back`() {
        BuiltInPreset.entries.forEach { preset ->
            val settings = SoundPresets.settingsOf(preset, config)
            assertEquals(preset.name, settings, SoundMapper.settingsOf(SoundMapper.columnsOf(settings), config))
        }
    }

    @Test
    fun `the knob's place and «своё» are both kept`() {
        val byKnob = SoundPresets.settingsOf(BuiltInPreset.NATURAL, config)
        assertEquals(0.25, SoundMapper.columnsOf(byKnob).compAmount!!, 0.0)
        val byHand = byKnob.copy(compressor = SoundRules.byHand(byKnob.compressor))
        assertNull(SoundMapper.settingsOf(SoundMapper.columnsOf(byHand), config).compressor.amount)
    }

    @Test
    fun `what is read is brought into range, and an unknown space is a hall`() {
        val columns = SoundMapper.columnsOf(SoundRules.off(config)).copy(reverbSpace = "STADIUM", reverbDecaySec = 40.0, outputGainDb = -90.0, bodyQ = 0.0)
        val settings = SoundMapper.settingsOf(columns, config)
        assertEquals(ReverbSpace.HALL, settings.reverb.space)
        assertEquals(3.0, settings.reverb.decaySec, 0.0)
        assertEquals(-12.0, settings.output.gainDb, 0.0)
        assertEquals(0.4, settings.eq.body.q, 0.0)
    }

    @Test
    fun `a recording sounds like everyone until it is given settings of its own, and again once they are cleared`() = runTest {
        val repository = FakeSoundRepository(config)
        val hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
        val warm = SoundPresets.settingsOf(BuiltInPreset.WARM, config)

        assertTrue(SoundRules.isNeutral(repository.effective(7).first().settings))
        repository.setDefault(hall)
        assertEquals(hall, repository.effective(7).first().settings)
        assertFalse(repository.effective(7).first().own)

        repository.setOwn(7, warm)
        assertEquals(warm, repository.effective(7).first().settings)
        assertTrue(repository.effective(7).first().own)
        assertEquals("the others still follow the default", hall, repository.effective(8).first().settings)

        repository.clearOwn(7)
        assertEquals(hall, repository.effective(7).first().settings)
    }
}
