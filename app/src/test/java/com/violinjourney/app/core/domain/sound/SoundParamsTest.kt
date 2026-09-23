package com.violinjourney.app.core.domain.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundParamsTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config)

    @Test
    fun `every number can be written and read back, within its range`() {
        SoundParam.entries.forEach { param ->
            val range = SoundParams.range(param, off, config)
            val middle = SoundParams.valueAt(param, 0.37f, range)
            assertEquals(param.name, middle, SoundParams.get(param, SoundParams.set(param, middle, off, config))!!, 1e-9)
            assertEquals(param.name, range.max, SoundParams.get(param, SoundParams.set(param, 1e9, off, config))!!, 1e-9)
            assertEquals(param.name, range.min, SoundParams.get(param, SoundParams.set(param, -1e9, off, config))!!, 1e-9)
        }
    }

    @Test
    fun `nothing but the number asked for changes`() {
        val changed = SoundParams.set(SoundParam.AIR_GAIN, 4.0, off, config)
        assertEquals(off.copy(eq = off.eq.copy(air = off.eq.air.copy(gainDb = 4.0))), changed)
        assertEquals("the switch of the block is not the slider's business", false, changed.eq.enabled)
    }

    @Test
    fun `the knob leads its five, and a hand on any of them lets it go`() {
        val byKnob = SoundParams.set(SoundParam.COMP_AMOUNT, 0.85, off, config)
        assertEquals(CompressorAmount.settingsOf(0.85, enabled = false), byKnob.compressor)
        val byHand = SoundParams.set(SoundParam.COMP_RATIO, 8.0, byKnob, config)
        assertNull(SoundParams.get(SoundParam.COMP_AMOUNT, byHand))
        assertEquals(8.0, byHand.compressor.ratio, 0.0)
        assertEquals("the other four stay where the knob left them", byKnob.compressor.thresholdDb, byHand.compressor.thresholdDb, 0.0)
    }

    @Test
    fun `the tail is as long as its space allows`() {
        val room = off.copy(reverb = SoundRules.withSpace(off.reverb, ReverbSpace.ROOM, config))
        assertEquals(ParamRange(0.3, 1.2, 0.6), SoundParams.range(SoundParam.REVERB_DECAY, room, config))
        assertEquals(1.2, SoundParams.set(SoundParam.REVERB_DECAY, 5.0, room, config).reverb.decaySec, 0.0)
        assertEquals("a double tap returns to the pre-delay of the space", 5.0, SoundParams.range(SoundParam.REVERB_PRE_DELAY, room, config).default, 0.0)
    }

    @Test
    fun `frequencies lie on a logarithmic track, the rest on a straight one`() {
        val whole = ParamRange(20.0, 20_000.0, 1_000.0)
        assertEquals("the middle of 20 Hz – 20 kHz", 632.5, SoundParams.valueAt(SoundParam.BODY_HZ, 0.5f, whole), 0.1)
        assertEquals(0.5f, SoundParams.fractionOf(SoundParam.BODY_HZ, 632.46, whole), 1e-3f)
        assertEquals("the middle of the low cut", 109.5, SoundParams.valueAt(SoundParam.LOW_CUT_HZ, 0.5f, config.lowCutHz), 0.1)
        assertEquals(0.5f, SoundParams.fractionOf(SoundParam.LOW_GAIN, 0.0, config.eqGainDb), 0f)
        assertEquals(0.25f, SoundParams.fractionOf(SoundParam.REVERB_MIX, 0.15, config.mix), 1e-6f)
    }

    @Test
    fun `a press of plus or minus is one step of the unit`() {
        val up = true
        assertEquals(466.16, SoundParams.stepped(SoundParam.BODY_HZ, 440.0, up, config.bodyHz), 0.01)
        assertEquals(440.0, SoundParams.stepped(SoundParam.BODY_HZ, 466.16, !up, config.bodyHz), 0.01)
        assertEquals(-3.0, SoundParams.stepped(SoundParam.PRESENCE_GAIN, -3.5, up, config.eqGainDb), 0.0)
        assertEquals("an odd value lands on the grid", 2.5, SoundParams.stepped(SoundParam.OUTPUT_GAIN, 2.2, up, config.outputGainDb), 1e-9)
        assertEquals(16.0, SoundParams.stepped(SoundParam.COMP_ATTACK, 15.0, up, config.attackMs), 0.0)
        assertEquals(25.0, SoundParams.stepped(SoundParam.COMP_ATTACK, 20.0, up, config.attackMs), 0.0)
        assertEquals(19.0, SoundParams.stepped(SoundParam.COMP_ATTACK, 20.0, !up, config.attackMs), 0.0)
        assertEquals(205.0, SoundParams.stepped(SoundParam.COMP_RELEASE, 200.0, up, config.releaseMs), 0.0)
        assertEquals(3.5, SoundParams.stepped(SoundParam.COMP_RATIO, 3.0, up, config.ratio), 0.0)
        assertEquals(0.26, SoundParams.stepped(SoundParam.REVERB_MIX, 0.25, up, config.mix), 1e-9)
        assertEquals(1.9, SoundParams.stepped(SoundParam.REVERB_DECAY, 1.8, up, config.hallDecaySec), 1e-9)
        assertEquals(1.1, SoundParams.stepped(SoundParam.BODY_Q, 1.0, up, config.bellQ), 1e-9)
    }

    @Test
    fun `a value from under a finger lands on the grid it is shown on`() {
        assertEquals(-1.5, SoundParams.snapped(SoundParam.PRESENCE_GAIN, -1.3585), 0.0)
        assertEquals(1_946.0, SoundParams.snapped(SoundParam.PRESENCE_HZ, 1_946.22), 0.0)
        assertEquals(17.0, SoundParams.snapped(SoundParam.COMP_ATTACK, 16.6), 0.0)
        assertEquals(1.8, SoundParams.snapped(SoundParam.REVERB_DECAY, 1.77), 1e-9)
        assertEquals(0.23, SoundParams.snapped(SoundParam.REVERB_MIX, 0.2349), 1e-9)
        assertEquals(3.4, SoundParams.snapped(SoundParam.COMP_RATIO, 3.44), 1e-9)
    }

    @Test
    fun `steps stop at the ends of the range`() {
        assertEquals(12.0, SoundParams.stepped(SoundParam.OUTPUT_GAIN, 12.0, true, config.outputGainDb), 0.0)
        assertEquals(40.0, SoundParams.stepped(SoundParam.LOW_CUT_HZ, 40.0, false, config.lowCutHz), 0.0)
    }

    @Test
    fun `every band has its sliders, and each slider belongs to one block`() {
        assertEquals(listOf(SoundParam.LOW_CUT_HZ), SoundParam.ofBand(EqBand.LOW_CUT))
        assertEquals(3, SoundParam.ofBand(EqBand.PRESENCE).size)
        val inBands = EqBand.entries.flatMap(SoundParam::ofBand)
        assertEquals(inBands.size, inBands.toSet().size)
        assertTrue(inBands.all { it.block == SoundBlock.EQ })
        assertEquals(SoundParam.entries.filter { it.block == SoundBlock.EQ }.toSet(), inBands.toSet())
    }
}
