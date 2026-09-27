package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.backing.BackingMixer
import com.violinjourney.app.core.audio.backing.BackingSource
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.recording.PcmSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The backing a take is rendered under (spec 3.32): its sound at the recording's rate, the shift and the level of the take. */
class RenderMix(val source: BackingSource, val offsetMs: Int, val gainDb: Float)

/**
 * The file that is shared, sample for sample, on both platforms: the recording through the very chain the player plays
 * through, without the clock (spec 3.17) — what was heard in the app is what lands in the file. The chain is late by the
 * look-ahead of its limiter: those first samples are dropped, so the file starts where the recording does, and it rings
 * on after the last note for as long as the hall does. Under a backing (spec 3.32) the mix is stereo and late by its own
 * limiter as well: those frames go too. Everything off — the chain is not called at all (its limiter would still delay
 * the sound): a sound sent without processing is the sound as recorded.
 *
 * The decoder and the encoder are the platform's: [SoundRenderer]s open them, hand the samples through here and close them.
 */
object SoundRenderLoop {
    /** Samples read at a time. */
    const val CHUNK = 4_096
    private const val FULL_SCALE = 32_768f

    /**
     * Reads [source] to its end, and [write]s the rendered sound — mono, or interleaved stereo under [mix] — a chunk at a
     * time; [onProgress] goes up to 1. False — [write] refused a chunk, and nothing more was done. [gaveUp] — the reader
     * stopped before the end of the file (iOS): what came is not the take, and no tail follows it. Cancellable between
     * chunks.
     */
    suspend fun run(
        source: PcmSource,
        settings: SoundSettings,
        config: SoundConfig,
        mix: RenderMix?,
        write: (samples: ShortArray, count: Int) -> Boolean,
        onProgress: (Float) -> Unit,
        gaveUp: () -> Boolean = { false },
    ): Boolean {
        val rate = source.sampleRate
        val mixer = mix?.let { BackingMixer(rate, it.source, it.offsetMs, it.gainDb, heard = true, fadeSamples = 0, soundConfig = config) }
        val chain = SoundChain(rate, config).apply { set(settings, immediate = true) }
        val neutral = SoundRules.isNeutral(settings)
        val pcm = ShortArray(CHUNK)
        val samples = FloatArray(CHUNK)
        var toDrop = if (neutral) 0 else chain.latencySamples
        val tail = (if (neutral) 0 else chain.tailSamples(settings) + chain.latencySamples) + (mixer?.latencySamples ?: 0)
        // the mix is late by its limiter's look-ahead, like the chain by its own: those first frames go too
        var mixToDrop = mixer?.latencySamples ?: 0
        var violinPosition = 0L
        val violin = FloatArray(CHUNK)
        val stereo = FloatArray(if (mixer != null) CHUNK * 2 else 0)
        val interleaved = ShortArray(if (mixer != null) CHUNK * 2 else 0)
        val total = (source.totalSamples + tail).coerceAtLeast(1)
        var done = 0L
        var tailLeft = tail

        while (true) {
            currentCoroutineContext().ensureActive()
            var count = source.read(pcm)
            if (count == PcmSource.END) {
                if (tailLeft == 0 || gaveUp()) break
                count = minOf(tailLeft, CHUNK)
                tailLeft -= count
                samples.fill(0f, 0, count)
            } else {
                for (i in 0 until count) samples[i] = pcm[i] / FULL_SCALE
            }
            if (!neutral) chain.process(samples, count)
            val from = minOf(toDrop, count)
            toDrop -= from
            if (mixer == null) {
                for (i in from until count) pcm[i - from] = toShort(samples[i])
                if (count > from && !write(pcm, count - from)) return false
            } else if (count > from) {
                val kept = count - from
                samples.copyInto(violin, 0, from, count)
                mixer.mix(violin, kept, violinPosition, stereo)
                violinPosition += kept
                val skip = minOf(mixToDrop, kept)
                mixToDrop -= skip
                for (i in skip until kept) {
                    interleaved[2 * (i - skip)] = toShort(stereo[2 * i])
                    interleaved[2 * (i - skip) + 1] = toShort(stereo[2 * i + 1])
                }
                if (kept > skip && !write(interleaved, (kept - skip) * 2)) return false
            }
            done += count
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
        return true
    }

    private fun toShort(sample: Float): Short = (sample * FULL_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
