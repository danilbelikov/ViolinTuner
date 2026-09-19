package com.example.violintuner.core.audio.share

import android.util.Log
import com.example.violintuner.core.audio.fx.SoundChain
import com.example.violintuner.core.audio.playback.PcmDecoder
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundSettings
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Makes the file that is shared out of a recording and its sound settings. */
interface SoundRenderer {
    /**
     * Renders [source] through the chain set to [settings] into [target]. True when the file is
     * whole; false when it could not be made — [target] is then gone. Cancellable: a cancelled
     * render leaves no file either. [onProgress] gets 0…1, from whatever thread renders.
     */
    suspend fun render(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean
}

/**
 * The very chain the player plays through, without the clock: decoder → [SoundChain] → AAC. What
 * was heard in the app is what lands in the file (spec 3.17). The chain is late by the look-ahead
 * of its limiter — those first samples are dropped, so the file starts where the recording does —
 * and rings on after the last note for as long as the hall does.
 */
class SoundFileRenderer @Inject constructor(
    private val config: SoundConfig,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SoundRenderer {

    override suspend fun render(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean = withContext(io) {
        val decoder = PcmDecoder.open(source) ?: return@withContext false
        var writer: OfflineAacWriter? = null
        var whole = false
        try {
            target.parentFile?.mkdirs()
            writer = OfflineAacWriter(target, decoder.sampleRate, BIT_RATE)
            val chain = SoundChain(decoder.sampleRate, config).apply { set(settings, immediate = true) }
            val pcm = ShortArray(CHUNK)
            val samples = FloatArray(CHUNK)
            var toDrop = chain.latencySamples
            val tail = chain.tailSamples(settings) + chain.latencySamples
            val total = (decoder.totalSamples + tail).coerceAtLeast(1)
            var done = 0L
            var tailLeft = tail

            while (true) {
                ensureActive()
                var count = decoder.read(pcm)
                if (count == PcmDecoder.END) {
                    if (tailLeft == 0) break
                    count = minOf(tailLeft, CHUNK)
                    tailLeft -= count
                    samples.fill(0f, 0, count)
                } else {
                    for (i in 0 until count) samples[i] = pcm[i] / FULL_SCALE
                }
                chain.process(samples, count)
                val from = minOf(toDrop, count)
                toDrop -= from
                for (i in from until count) pcm[i - from] = (samples[i] * FULL_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                if (count > from) writer.write(pcm, count - from)
                done += count
                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
            }
            writer.finish()
            whole = target.length() > 0
            whole
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // MediaCodec and MediaMuxer report every failure as a runtime exception; so does a disk that is full.
            Log.w(TAG, "rendering ${source.name} failed", e)
            false
        } finally {
            if (!whole) {
                writer?.abort()
                target.delete()
            }
            decoder.release()
        }
    }

    companion object {
        /** AAC-LC mono for the file that is sent (spec 5.11). */
        const val BIT_RATE = 128_000
        private const val TAG = "SoundFileRenderer"
        private const val CHUNK = 4_096
        private const val FULL_SCALE = 32_768f
    }
}
