package com.violinjourney.app.core.audio.share

import android.util.Log
import com.violinjourney.app.core.audio.backing.BackingMixer
import com.violinjourney.app.core.audio.backing.BackingPcmReader
import com.violinjourney.app.core.audio.backing.PcmBackingSource
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.playback.PcmDecoder
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.recording.video.VideoMuxer
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** Makes the file that is shared out of a recording and its sound settings. */

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

    override suspend fun render(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean =
        renderSound(source, settings, null, target, onProgress)

    override suspend fun renderWithBacking(source: File, settings: SoundSettings, backing: RenderBacking, target: File, onProgress: (Float) -> Unit): Boolean =
        renderSound(source, settings, backing, target, onProgress)

    override suspend fun renderVideo(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean =
        renderVideoSound(source, target, onProgress) { sound, progress -> render(source, settings, sound, progress) }

    override suspend fun renderVideoWithBacking(source: File, settings: SoundSettings, backing: RenderBacking, target: File, onProgress: (Float) -> Unit): Boolean =
        renderVideoSound(source, target, onProgress) { sound, progress -> renderWithBacking(source, settings, backing, sound, progress) }

    private suspend fun renderSound(source: File, settings: SoundSettings, backing: RenderBacking?, target: File, onProgress: (Float) -> Unit): Boolean = withContext(io) {
        val decoder = PcmDecoder.open(source) ?: return@withContext false
        var writer: OfflineAacWriter? = null
        var reader: BackingPcmReader? = null
        var whole = false
        try {
            target.parentFile?.mkdirs()
            // the backing at this recording's rate; gone or undecodable — the file cannot be what was asked for
            val backingPcm = backing?.let { it.pcm(decoder.sampleRate) ?: return@withContext false }
            reader = backingPcm?.let(::BackingPcmReader)
            val mixer = if (backing != null && reader != null) {
                BackingMixer(decoder.sampleRate, PcmBackingSource(reader), backing.offsetMs, backing.gainDb, heard = true, fadeSamples = 0, soundConfig = config)
            } else {
                null
            }
            writer = if (mixer != null) OfflineAacWriter(target, decoder.sampleRate, STEREO_BIT_RATE, channels = 2) else OfflineAacWriter(target, decoder.sampleRate, BIT_RATE)
            val chain = SoundChain(decoder.sampleRate, config).apply { set(settings, immediate = true) }
            // Everything off — the chain is not called at all (its limiter would still delay the sound): the
            // sound of a video sent as «Только звук» without processing is the sound as recorded.
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
                if (!neutral) chain.process(samples, count)
                val from = minOf(toDrop, count)
                toDrop -= from
                if (mixer == null) {
                    for (i in from until count) pcm[i - from] = toShort(samples[i])
                    if (count > from) writer.write(pcm, count - from)
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
                    if (kept > skip) writer.write(interleaved, (kept - skip) * 2)
                }
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
        } catch (e: IOException) {
            // the backing's sound gone or cut short, a file the muxer cannot open
            Log.w(TAG, "cannot read or write for ${source.name}", e)
            false
        } finally {
            if (!whole) {
                writer?.abort()
                target.delete()
            }
            decoder.release()
            reader?.close()
        }
    }

    private fun toShort(sample: Float): Short = (sample * FULL_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    /**
     * Two passes: the sound into a temporary `.m4a` by [render] — the tested way — and then
     * [VideoMuxer.splice] takes the video samples of the source as they are and the new sound beside them.
     * No picture is decoded, so this takes hardly longer than the sound alone.
     */
    private suspend fun renderVideoSound(source: File, target: File, onProgress: (Float) -> Unit, renderSound: suspend (File, (Float) -> Unit) -> Boolean): Boolean = withContext(io) {
        val sound = File(target.parentFile, target.name + SOUND_SUFFIX)
        var whole = false
        try {
            if (!renderSound(sound) { onProgress(it * SOUND_SHARE) }) return@withContext false
            val job = coroutineContext.job
            // the picture as it was, beside the new sound: the same splice as the take of the app's camera, without a shift
            whole = VideoMuxer.splice(
                source, sound, target, pictureShiftUs = 0,
                onProgress = { onProgress(SOUND_SHARE + it * (1f - SOUND_SHARE)) },
                keepGoing = { job.ensureActive() },
            )
            whole
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            Log.w(TAG, "muxing ${source.name} failed", e)
            false
        } catch (e: IOException) {
            Log.w(TAG, "cannot read ${source.name}", e)
            false
        } finally {
            sound.delete()
            if (!whole) target.delete()
        }
    }

    companion object {
        /** The sound is nearly all of the work of a video: no picture is decoded. */
        private const val SOUND_SHARE = 0.9f
        private const val SOUND_SUFFIX = ".sound.m4a"

        private const val BIT_RATE = SoundRenderer.BIT_RATE
        private const val STEREO_BIT_RATE = SoundRenderer.STEREO_BIT_RATE
        private const val TAG = "SoundFileRenderer"
        private const val CHUNK = 4_096
        private const val FULL_SCALE = 32_768f
    }
}
