package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.backing.BackingSource
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.playback.PlayerSound
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.OutputSettings
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.recording.PcmSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The file of «Поделиться» (spec 3.17, 3.32), made by the one loop both platforms render through. */
class SoundRenderLoopTest {
    private val config = SoundConfig()
    private val off = SoundRules.off(config)

    /** −6 dB at the output: processed — the limiter delays the sound — but no tail. */
    private val quieter = off.copy(output = OutputSettings(enabled = true, gainDb = -6.0))
    private val hall = off.copy(reverb = off.reverb.copy(enabled = true))

    private class TakeSource(private val samples: ShortArray) : PcmSource {
        override val sampleRate = RATE
        override val totalSamples = samples.size.toLong()
        private var at = 0

        override fun read(out: ShortArray): Int {
            if (at >= samples.size) return PcmSource.END
            val count = minOf(out.size, samples.size - at)
            samples.copyInto(out, 0, at, at + count)
            at += count
            return count
        }
    }

    /** A backing with one click on the left, at its own sample [at]. */
    private class ClickBacking(private val at: Long) : BackingSource {
        override fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray) {
            for (i in 0 until count) {
                left[i] = if (position + i == at) CLICK * gain else 0f
                right[i] = 0f
            }
        }
    }

    private fun impulse(length: Int, at: Int) = ShortArray(length).also { it[at] = (CLICK * FULL_SCALE).toInt().toShort() }

    private suspend fun render(take: ShortArray, settings: SoundSettings, mix: RenderMix? = null): ShortArray {
        val written = ArrayList<Short>()
        assertTrue(SoundRenderLoop.run(TakeSource(take), settings, config, mix, write = { samples, count -> written += samples.take(count); true }, onProgress = {}))
        return written.toShortArray()
    }

    @Test
    fun `a neutral render is the recording sample for sample`() = runTest {
        val take = ShortArray(3 * SoundRenderLoop.CHUNK + 123) { ((it * 37) % 20_000 - 10_000).toShort() }
        assertContentEquals(take, render(take, off))
    }

    @Test
    fun `a processed render starts where the recording does and rings on`() = runTest {
        val take = impulse(RATE, at = 1_000)
        val quiet = render(take, quieter)
        assertEquals(take.size, quiet.size, "no tail without a hall, and the limiter's delay is cut off")
        assertEquals(1_000, quiet.indices.maxBy { abs(quiet[it].toInt()) }, "the click where it was recorded")

        val rung = render(take, hall)
        assertEquals(take.size + SoundChain(RATE, config).tailSamples(hall), rung.size, "the hall rings on after the last note")
        assertEquals(1_000, rung.indices.maxBy { abs(rung[it].toInt()) })
    }

    @Test
    fun `a render under a backing is stereo and as long as the violin's`() = runTest {
        val take = impulse(RATE / 2, at = 100)
        val stereo = render(take, off, RenderMix(ClickBacking(at = 10_000), offsetMs = 0, gainDb = 0f))
        assertEquals(take.size * 2, stereo.size)
    }

    @Test
    fun `a refused write stops the render`() = runTest {
        var writes = 0
        val whole = SoundRenderLoop.run(
            TakeSource(ShortArray(5 * SoundRenderLoop.CHUNK)), off, config, null,
            write = { _, _ -> ++writes < 2 }, onProgress = {},
        )
        assertFalse(whole)
        assertEquals(2, writes, "nothing is written after the refusal")
    }

    @Test
    fun `progress goes up to one`() = runTest {
        val progress = mutableListOf<Float>()
        SoundRenderLoop.run(TakeSource(impulse(RATE, at = 0)), hall, config, null, write = { _, _ -> true }, onProgress = { progress += it })
        assertEquals(progress.sorted(), progress, "it never goes back")
        assertEquals(1f, progress.last())
    }

    @Test
    fun `the player mixes the backing where the shared file does`() = runTest {
        // A click of the violin at 0.2 s and one of the backing at 0.3 s of its own, shifted by 40 ms; the violin processed,
        // so that the chain delays it. The player and the file must put the two clicks as far apart (spec 3.17: what is heard
        // is what is sent) — the player once read the backing ahead of the violin by the chain's delay.
        val take = impulse(RATE, at = RATE / 5)
        val backing = ClickBacking(at = RATE * 3L / 10)
        val file = render(take, quieter, RenderMix(backing, offsetMs = 40, gainDb = 0f)).map { it / FULL_SCALE }.toFloatArray()

        val sound = PlayerSound(RATE, config, BackingConfig(), backing, offsetMs = 40, gainDb = 0f, heard = true, settings = quieter, original = false)
        val heard = ArrayList<Float>()
        var at = 0
        while (!sound.ended) {
            val count = sound.next(original = false) { into ->
                val floats = minOf(PlayerSound.CHUNK, take.size - at)
                for (i in 0 until floats) into[i] = take[at + i] / FULL_SCALE
                at += floats
                floats
            }
            for (i in 0 until count * 2) heard += sound.output[i]
        }

        fun gap(stereo: FloatArray): Int {
            val frames = stereo.size / 2
            val violin = (0 until frames).maxBy { abs(stereo[2 * it + 1]) }
            val click = (0 until frames).maxBy { abs(stereo[2 * it] - stereo[2 * it + 1]) }
            return click - violin
        }
        val inFile = gap(file)
        assertTrue(inFile > 0, "the backing's click comes after the violin's in the file: $inFile")
        assertEquals(inFile, gap(heard.toFloatArray()), "the gap between the violin and the backing in the player")
    }

    private companion object {
        const val RATE = 48_000
        const val FULL_SCALE = 32_768f
        const val CLICK = 0.5f
    }
}
