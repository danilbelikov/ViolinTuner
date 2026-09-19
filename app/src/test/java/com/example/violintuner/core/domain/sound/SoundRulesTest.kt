package com.example.violintuner.core.domain.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRulesTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config)

    @Test
    fun `out of the box nothing is on and the sound is the recording`() {
        assertTrue(SoundRules.isNeutral(off))
        assertEquals(0.0, SoundRules.tailSec(off, config), 0.0)
        assertEquals(off, SoundRules.clean(off, config))
    }

    @Test
    fun `a block that is on but set to nothing still leaves the sound alone`() {
        assertTrue(SoundRules.isNeutral(off.copy(eq = off.eq.copy(enabled = true))))
        assertTrue(SoundRules.isNeutral(off.copy(output = OutputSettings(enabled = true, gainDb = 0.0))))
        assertTrue(SoundRules.isNeutral(off.copy(reverb = off.reverb.copy(enabled = true, mix = 0.0))))
        assertTrue("a gain set on a band of a switched-off equalizer", SoundRules.isNeutral(off.copy(eq = off.eq.copy(low = Shelf(200.0, 6.0)))))
    }

    @Test
    fun `any block that does something makes the sound processed`() {
        assertFalse(SoundRules.isNeutral(off.copy(eq = off.eq.copy(enabled = true, lowCut = LowCut(true, 80.0)))))
        assertFalse(SoundRules.isNeutral(off.copy(eq = off.eq.copy(enabled = true, air = Shelf(8_000.0, -2.0)))))
        assertFalse(SoundRules.isNeutral(off.copy(compressor = off.compressor.copy(enabled = true))))
        assertFalse(SoundRules.isNeutral(off.copy(reverb = off.reverb.copy(enabled = true))))
        assertFalse(SoundRules.isNeutral(off.copy(output = OutputSettings(true, 3.0))))
    }

    @Test
    fun `numbers from disk are brought into their ranges`() {
        val wild = off.copy(
            eq = off.eq.copy(lowCut = LowCut(true, 5.0), body = Bell(90_000.0, 40.0, 0.0), air = Shelf(Double.NaN, -99.0)),
            compressor = off.compressor.copy(thresholdDb = 12.0, ratio = 0.2, attackMs = 0.0, releaseMs = 1e9, makeupDb = -3.0, amount = 7.0),
            reverb = off.reverb.copy(space = ReverbSpace.ROOM, decaySec = 5.0, preDelayMs = -1.0, brightness = 2.0, mix = 1.0),
            output = OutputSettings(true, 40.0),
        )
        val clean = SoundRules.clean(wild, config)
        assertEquals(LowCut(true, 40.0), clean.eq.lowCut)
        assertEquals(Bell(2_000.0, 12.0, 0.4), clean.eq.body)
        assertEquals(Shelf(8_000.0, -12.0), clean.eq.air)
        assertEquals(0.0, clean.compressor.thresholdDb, 0.0)
        assertEquals(1.0, clean.compressor.ratio, 0.0)
        assertEquals(1.0, clean.compressor.attackMs, 0.0)
        assertEquals(1_000.0, clean.compressor.releaseMs, 0.0)
        assertEquals(0.0, clean.compressor.makeupDb, 0.0)
        assertEquals(1.0, clean.compressor.amount!!, 0.0)
        assertEquals("a room has no five-second tail", 1.2, clean.reverb.decaySec, 0.0)
        assertEquals(0.0, clean.reverb.preDelayMs, 0.0)
        assertEquals(1.0, clean.reverb.brightness, 0.0)
        assertEquals(0.6, clean.reverb.mix, 0.0)
        assertEquals(12.0, clean.output.gainDb, 0.0)
    }

    @Test
    fun `another space brings its own tail and pre-delay, the same space changes nothing`() {
        val hall = off.reverb.copy(enabled = true, decaySec = 2.7, preDelayMs = 33.0, mix = 0.3)
        assertEquals(hall, SoundRules.withSpace(hall, ReverbSpace.HALL, config))
        val cathedral = SoundRules.withSpace(hall, ReverbSpace.CATHEDRAL, config)
        assertEquals(4.0, cathedral.decaySec, 0.0)
        assertEquals(40.0, cathedral.preDelayMs, 0.0)
        assertEquals("the share is the player's own choice", 0.3, cathedral.mix, 0.0)
        assertTrue(cathedral.enabled)
    }

    @Test
    fun `the one knob leads the five, and a hand on any of them lets it go`() {
        val little = CompressorAmount.settingsOf(CompressorAmount.A_LITTLE)
        assertEquals(-12.0, little.thresholdDb, 1e-9)
        assertEquals(2.25, little.ratio, 1e-9)
        assertEquals(17.0, little.attackMs, 1e-9)
        assertEquals(225.0, little.releaseMs, 1e-9)
        assertEquals(1.5, little.makeupDb, 1e-9)
        assertEquals(0.25, little.amount!!, 0.0)

        val full = CompressorAmount.settingsOf(3.0)
        assertEquals(-30.0, full.thresholdDb, 1e-9)
        assertEquals(6.0, full.ratio, 1e-9)
        assertEquals(full, SoundRules.clean(off.copy(compressor = full), config).compressor)

        val switchedOff = SoundRules.withAmount(off.compressor, 0.5)
        assertFalse("the knob does not flip the switch", switchedOff.enabled)
        assertEquals(0.5, switchedOff.amount!!, 0.0)
        assertNull(SoundRules.byHand(little.copy(ratio = 8.0)).amount)
    }

    @Test
    fun `the file rings on for the length of the tail, six seconds at most, and not at all without the hall`() {
        val hall = off.copy(reverb = off.reverb.copy(enabled = true, decaySec = 1.8))
        assertEquals(1.8, SoundRules.tailSec(hall, config), 0.0)
        val cathedral = off.copy(reverb = off.reverb.copy(enabled = true, space = ReverbSpace.CATHEDRAL, decaySec = 6.0))
        assertEquals(6.0, SoundRules.tailSec(cathedral, config.copy(maxTailSec = 6.0)), 0.0)
        assertEquals(4.0, SoundRules.tailSec(cathedral, config.copy(maxTailSec = 4.0)), 0.0)
        assertEquals(0.0, SoundRules.tailSec(hall.copy(reverb = hall.reverb.copy(mix = 0.0)), config), 0.0)
    }

    @Test
    fun `a preset name is trimmed and capped, and an empty one is no name`() {
        assertEquals("Мой зал", SoundRules.cleanPresetName("  Мой зал ", config))
        assertEquals(24, SoundRules.cleanPresetName("x".repeat(40), config)!!.length)
        assertNull(SoundRules.cleanPresetName("   ", config))
    }
}
