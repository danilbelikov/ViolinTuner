package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.backing.BackingSource
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOffset
import com.violinjourney.app.core.domain.sound.OutputSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sound of a take between the decoder and the output, as both players make it (spec 3.17, 3.32). */
class PlayerSoundTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config)
    private val hall = off.copy(reverb = off.reverb.copy(enabled = true))
    private val quieter = off.copy(output = OutputSettings(enabled = true, gainDb = -6.0))
    private val latency = SoundChain(RATE, config).latencySamples

    private val take = FloatArray(RATE / 2) { (0.3 * sin(it * 0.05)).toFloat() }

    /** A silent backing that remembers where it was read from. */
    private class ListeningBacking : BackingSource {
        val reads = mutableListOf<Long>()

        override fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray) {
            reads += position
            left.fill(0f, 0, count)
            right.fill(0f, 0, count)
        }
    }

    private fun sound(settings: SoundSettings, original: Boolean = false, backing: BackingSource? = null) =
        PlayerSound(RATE, config, BackingConfig(), backing, offsetMs = 0, gainDb = 0f, heard = true, settings = settings, original = original)

    /** Plays [take] from [from] to the end of what rings after it; every frame that comes out, interleaved when stereo. */
    private fun playOut(sound: PlayerSound, original: Boolean = false, from: Int = 0): FloatArray {
        val out = ArrayList<Float>()
        var at = from
        var chunks = 0
        while (!sound.ended) {
            val count = sound.next(original) { into ->
                val read = minOf(PlayerSound.CHUNK, take.size - at)
                take.copyInto(into, 0, at, at + read)
                at += read
                read
            }
            for (i in 0 until count * (if (sound.stereo) 2 else 1)) out += sound.output[i]
            check(++chunks < MAX_CHUNKS) { "the sound never ends" }
        }
        return out.toFloatArray()
    }

    @Test
    fun `a take with the sound off passes as it is — as late as the chain would be`() {
        val heard = playOut(sound(off))
        // nothing to ring: after the last sample only the delay empties out
        assertEquals(take.size + latency, heard.size)
        for (i in take.indices) assertEquals(take[i], heard[i + latency], "sample $i")
    }

    @Test
    fun `at the original the take passes untouched while the processing is chosen`() {
        val sound = sound(hall, original = true)
        val heard = playOut(sound, original = true)
        for (i in take.indices) assertEquals(take[i], heard[i + latency], "sample $i")
        assertNull(sound.meters(), "the chain rests: nothing to show (spec 5.11)")
    }

    @Test
    fun `after the last sample the tail rings out and the sound ends`() {
        val sound = sound(hall)
        val heard = playOut(sound)
        assertEquals(take.size + latency + SoundChain(RATE, config).tailSamples(hall), heard.size)
        assertTrue(sound.ended)
        assertEquals(0, sound.next(original = false) { error("an ended take is not read") })
    }

    @Test
    fun `a restart forgets the tail and reads the backing from the new place`() {
        val backing = ListeningBacking()
        val sound = sound(quieter, backing = backing)
        playOut(sound)
        assertTrue(sound.ended)

        sound.restartAt(12_000)
        assertFalse(sound.ended)
        backing.reads.clear()
        sound.next(original = false) { into -> take.copyInto(into, 0, 0, PlayerSound.CHUNK); PlayerSound.CHUNK }
        // the violin heard now left the chain its delay ago: the backing is read as far behind
        assertEquals(BackingOffset.backingSampleAt(12_000L - latency, 0, RATE), backing.reads.first())
    }

    @Test
    fun `a backing makes the chunk stereo with the violin in both channels`() {
        val sound = sound(off, backing = ListeningBacking())
        assertTrue(sound.stereo)
        val count = sound.next(original = false) { into -> take.copyInto(into, 0, 0, PlayerSound.CHUNK); PlayerSound.CHUNK }
        assertEquals(PlayerSound.CHUNK, count)
        for (i in 0 until count) assertEquals(sound.output[2 * i], sound.output[2 * i + 1], "frame $i")
    }

    @Test
    fun `the meters are due thirty times a second and show nothing at the original`() {
        val sound = sound(quieter)
        val frame = RATE / config.metersPerSecond
        assertFalse(sound.metersDue(frame - 1))
        assertTrue(sound.metersDue(1))
        assertFalse(sound.metersDue(frame - 1), "counted anew after each frame of the meters")
        assertTrue(sound.metersDue(1))

        sound.next(original = false) { into -> take.copyInto(into, 0, 0, PlayerSound.CHUNK); PlayerSound.CHUNK }
        assertNotNull(sound.meters(), "the chain works")
        assertNull(sound(quieter, original = true).meters(), "at the original the chain rests")
    }

    private companion object {
        const val RATE = 48_000
        const val MAX_CHUNKS = 10_000
    }
}
