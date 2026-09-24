package com.violinjourney.app.core.audio.fx

import com.violinjourney.app.core.audio.fx.FxSignals.RATE
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.OutputSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import kotlin.test.Test
import kotlin.test.assertTrue

/** The speed of the chain on a desktop JVM (a Kotlin/Native debug build of the tests is no measure of a phone). */
class SoundChainSpeedTest {
    private val config = SoundConfig()

    @Test
    fun `an hour of sound is rendered in seconds — not minutes`() {
        val chain = SoundChain(RATE, config)
        chain.set(SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config).copy(output = OutputSettings(true, 12.0)))
        val minute = FxSignals.sine(440.0, 0.9, 60.0)
        val started = System.nanoTime()
        chain.process(minute)
        val seconds = (System.nanoTime() - started) / 1e9
        // A desktop JVM; a phone is several times slower, and the spec promises about a minute per hour there.
        assertTrue(seconds < 3.0, "a minute of sound took $seconds s")
    }
}
