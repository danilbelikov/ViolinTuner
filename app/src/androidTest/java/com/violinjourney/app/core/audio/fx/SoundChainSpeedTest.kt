package com.violinjourney.app.core.audio.fx

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How fast the chain runs on a device, in a debuggable build — the slowest it will ever be.
 * A player needs more than 1× with room to spare; a shared hour should not take an hour.
 */
@RunWith(AndroidJUnit4::class)
class SoundChainSpeedTest {
    private val rate = 48_000
    private val config = SoundConfig()

    private fun timesRealTime(name: String, block: (FloatArray) -> Unit): Double {
        val seconds = 10
        val sound = FloatArray(rate * seconds) { (0.5 * sin(2 * PI * 440 * it / rate)).toFloat() }
        block(sound.copyOf(rate)) // warm-up: let the JIT see the loop
        val started = System.nanoTime()
        block(sound)
        val took = (System.nanoTime() - started) / 1e9
        Log.i("SoundChainSpeed", "$name: ${"%.1f".format(seconds / took)}x real time")
        return seconds / took
    }

    @Test
    fun theWholeChainIsManyTimesFasterThanTheSound() {
        val hall = SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config)
        val parts = mapOf(
            "equalizer" to SoundRules.off(config).copy(eq = hall.eq),
            "compressor" to SoundRules.off(config).copy(compressor = hall.compressor),
            "hall" to SoundRules.off(config).copy(reverb = hall.reverb),
            "limiter only" to SoundRules.off(config),
        )
        parts.forEach { (name, settings) ->
            timesRealTime(name) { SoundChain(rate, config).apply { set(settings) }.process(it) }
        }
        val whole = timesRealTime("whole chain") { SoundChain(rate, config).apply { set(hall) }.process(it) }
        assertTrue("the whole chain runs at ${"%.1f".format(whole)}x", whole > 4.0)
    }
}
