package com.example.violintuner.core.audio.share

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.violintuner.core.audio.backing.BackingMixer
import com.example.violintuner.core.audio.backing.BackingPcmReader
import com.example.violintuner.core.audio.backing.PcmBackingSource
import com.example.violintuner.core.audio.fx.SoundChain
import com.example.violintuner.core.audio.playback.PcmDecoder
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
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

    /**
     * The same for a video take (spec 3.19): [target] is an `.mp4` with the picture of [source]
     * copied as it is — not re-encoded, its turn kept — beside the sound rendered through the chain.
     */
    suspend fun renderVideo(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean

    /** [render] with the backing the take was made under mixed in (spec 3.32): a stereo `.m4a`. */
    suspend fun renderWithBacking(source: File, settings: SoundSettings, backing: RenderBacking, target: File, onProgress: (Float) -> Unit): Boolean = false

    /** [renderVideo] with the backing mixed into its sound. */
    suspend fun renderVideoWithBacking(source: File, settings: SoundSettings, backing: RenderBacking, target: File, onProgress: (Float) -> Unit): Boolean = false
}

/** A backing for the file that is sent: its sound prepared at the recording's rate, and how it is mixed. */
class RenderBacking(val pcm: (sampleRate: Int) -> File?, val offsetMs: Int, val gainDb: Float)

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
     * Two passes: the sound into a temporary `.m4a` by [render] — the tested way — and then one
     * muxer takes the video samples of the source as they are and the new sound beside them.
     * No picture is decoded, so this takes hardly longer than the sound alone.
     */
    private suspend fun renderVideoSound(source: File, target: File, onProgress: (Float) -> Unit, renderSound: suspend (File, (Float) -> Unit) -> Boolean): Boolean = withContext(io) {
        val sound = File(target.parentFile, target.name + SOUND_SUFFIX)
        var whole = false
        try {
            if (!renderSound(sound) { onProgress(it * SOUND_SHARE) }) return@withContext false
            whole = mux(source, sound, target) { onProgress(SOUND_SHARE + it * (1f - SOUND_SHARE)) }
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

    private suspend fun mux(video: File, sound: File, target: File, onProgress: (Float) -> Unit): Boolean {
        val picture = MediaExtractor()
        val audio = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            picture.setDataSource(video.absolutePath)
            audio.setDataSource(sound.absolutePath)
            val pictureTrack = (0 until picture.trackCount).firstOrNull { picture.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true } ?: return false
            val pictureFormat = picture.getTrackFormat(pictureTrack)
            picture.selectTrack(pictureTrack)
            audio.selectTrack(0)
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // the turn of the camera lives in the container, not in the samples
            if (pictureFormat.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(pictureFormat.getInteger(MediaFormat.KEY_ROTATION))
            val toPicture = muxer.addTrack(pictureFormat)
            val toSound = muxer.addTrack(audio.getTrackFormat(0))
            muxer.start()
            started = true

            val maxInput = if (pictureFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) pictureFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(maxOf(maxInput, SAMPLE_BUFFER))
            val info = MediaCodec.BufferInfo()
            val durationUs = pictureFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1)
            // interleaved by time, as a player reads it: whichever track is behind goes next
            var pictureLeft = true
            var soundLeft = true
            while (pictureLeft || soundLeft) {
                coroutineContext.ensureActive()
                val fromPicture = pictureLeft && (!soundLeft || picture.sampleTime <= audio.sampleTime)
                val from = if (fromPicture) picture else audio
                val size = from.readSampleData(buffer, 0)
                if (size < 0) {
                    if (fromPicture) pictureLeft = false else soundLeft = false
                    continue
                }
                info.set(0, size, from.sampleTime, if (from.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(if (fromPicture) toPicture else toSound, buffer, info)
                if (fromPicture) onProgress((from.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                from.advance()
            }
            muxer.stop()
            started = false
            return target.length() > 0
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            picture.release()
            audio.release()
        }
    }

    companion object {
        /** The sound is nearly all of the work of a video: no picture is decoded. */
        private const val SOUND_SHARE = 0.9f
        private const val SOUND_SUFFIX = ".sound.m4a"
        private const val SAMPLE_BUFFER = 2 * 1024 * 1024

        /** AAC-LC mono for the file that is sent (spec 5.11). */
        const val BIT_RATE = 128_000

        /** With the backing: stereo, and a bit rate to carry both sides (spec 5.25). */
        const val STEREO_BIT_RATE = 192_000
        private const val TAG = "SoundFileRenderer"
        private const val CHUNK = 4_096
        private const val FULL_SCALE = 32_768f
    }
}
