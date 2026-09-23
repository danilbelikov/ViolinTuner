package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.audio.fx.FxSignals.RATE
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.OutputSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SoundChainTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config)

    @Test
    fun `with everything off the chain only delays - which is why it is then not run at all`() {
        val chain = SoundChain(RATE, config)
        chain.set(off)
        val input = FxSignals.noise(0.4, 0.3)
        val output = input.copyOf()
        chain.process(output)
        val delay = chain.latencySamples
        for (index in delay until output.size) assertEquals(input[index - delay], output[index], 0f)
        assertTrue(SoundRules.isNeutral(off))
    }

    @Test
    fun `every preset keeps a loud recording under the ceiling and stays sane`() {
        val ceiling = 10.0.pow(config.limiterCeilingDb / 20)
        for (preset in BuiltInPreset.entries) {
            val chain = SoundChain(RATE, config)
            chain.set(SoundPresets.settingsOf(preset, config))
            val output = FxSignals.sine(660.0, 0.95, 2.0)
            chain.process(output)
            assertTrue("$preset", output.all { it.isFinite() })
            assertTrue("$preset peaks at ${FxSignals.peak(output)}", FxSignals.peak(output) <= ceiling + 1e-6)
        }
    }

    @Test
    fun `the meters tell the level, the squeeze and the limiter, and start anew once taken`() {
        val chain = SoundChain(RATE, config)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config).copy(output = OutputSettings(true, 12.0)))
        val buffer = FxSignals.sine(660.0, 0.9, 1.0)
        chain.process(buffer)
        val loud = chain.takeMeters()
        assertEquals(config.limiterCeilingDb, loud.outputPeakDb, 0.1)
        assertTrue("squeezes by ${loud.reductionDb}", loud.reductionDb > 3.0)
        assertTrue("the limiter works on +12 dB", loud.limiting)

        // long enough for the hall to fall silent after the loud part
        chain.process(FxSignals.sine(660.0, 0.001, 5.0))
        chain.takeMeters()
        chain.process(FxSignals.sine(660.0, 0.001, 0.5))
        val calm = chain.takeMeters()
        assertTrue("a quiet note reads ${calm.outputPeakDb}", calm.outputPeakDb < -40)
        assertFalse(calm.limiting)
        assertEquals(0.0, calm.reductionDb, 0.1)
    }

    @Test
    fun `a preset chosen while the sound plays comes in without a click`() {
        val chain = SoundChain(RATE, config)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.NATURAL, config))
        val buffer = FxSignals.sine(440.0, 0.3, 1.0)
        val half = buffer.size / 2
        val first = buffer.copyOfRange(0, half)
        val second = buffer.copyOfRange(half, buffer.size)
        chain.process(first)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config))
        chain.process(second)
        val joined = first + second
        // Nothing in either preset can make a 440 Hz sine of this level step further than a full-scale one would.
        val fullScaleStep = 2 * Math.PI * 440 / RATE
        assertTrue(FxSignals.largestStep(joined, half - 100, joined.size) < fullScaleStep)
    }

    @Test
    fun `after a seek nothing of the old place rings on`() {
        val chain = SoundChain(RATE, config)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config))
        chain.process(FxSignals.noise(0.8, 1.0))
        chain.reset()
        val silence = FloatArray(RATE / 2)
        chain.process(silence)
        assertEquals(0.0, FxSignals.peak(silence), 0.0)
    }

    @Test
    fun `the file rings on for as long as the hall does`() {
        val chain = SoundChain(RATE, config)
        assertEquals(0, chain.tailSamples(off))
        assertEquals((1.8 * RATE).toInt(), chain.tailSamples(SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)))
    }

    @Test
    fun `an hour of sound is rendered in seconds, not minutes`() {
        val chain = SoundChain(RATE, config)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config).copy(output = OutputSettings(true, 12.0)))
        val minute = FxSignals.sine(440.0, 0.9, 60.0)
        val started = System.nanoTime()
        chain.process(minute)
        val seconds = (System.nanoTime() - started) / 1e9
        // A desktop JVM; a phone is several times slower, and the spec promises about a minute per hour there.
        assertTrue("a minute of sound took $seconds s", seconds < 3.0)
    }
}
