package com.violinjourney.app.core.audio.share

import android.util.Log
import com.violinjourney.app.core.audio.backing.BackingPcmReader
import com.violinjourney.app.core.audio.backing.PcmBackingSource
import com.violinjourney.app.core.audio.playback.PcmDecoder
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.sound.SoundConfig
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

/**
 * Makes the file that is shared out of a recording and its sound settings: [PcmDecoder] → [SoundRenderLoop] — the very
 * chain the player plays through, without the clock, the same loop as on iOS — → AAC. What was heard in the app is what
 * lands in the file (spec 3.17).
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
            val mix = if (backing != null && reader != null) RenderMix(PcmBackingSource(reader), backing.offsetMs, backing.gainDb) else null
            val aac = if (mix != null) OfflineAacWriter(target, decoder.sampleRate, STEREO_BIT_RATE, channels = 2) else OfflineAacWriter(target, decoder.sampleRate, BIT_RATE)
            writer = aac
            SoundRenderLoop.run(decoder, settings, config, mix, write = { samples, count -> aac.write(samples, count); true }, onProgress = onProgress)
            aac.finish()
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
    }
}
