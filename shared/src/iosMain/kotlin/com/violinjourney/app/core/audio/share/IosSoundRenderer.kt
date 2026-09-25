package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.recording.AacFile
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.sibling
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.IosPcmFileOpener
import com.violinjourney.app.core.recording.PcmSource
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetExportPresetPassthrough
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableCompositionTrack
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.duration
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreMedia.kCMTimeZero
import platform.Foundation.NSURL

/**
 * [SoundRenderer] of iOS: the very chain the player plays through, without the clock — the sound track read by
 * AVAssetReader → [SoundChain] → AAC, as `SoundFileRenderer` does on Android (spec 3.17). The chain is late by the
 * look-ahead of its limiter: those first samples are dropped, and the hall rings on after the last note. A video take
 * gets its picture back as it was — not re-encoded — beside the rendered sound, by AVAssetExportSession.
 * Backings are not rendered on iOS yet: those variants fail, and the sheet does not offer them without a backing.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSoundRenderer(private val config: SoundConfig, private val io: CoroutineDispatcher) : SoundRenderer {
    override suspend fun render(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean =
        withContext(io) {
            val decoder = IosPcmFileOpener.open(source) ?: return@withContext false
            var whole = false
            try {
                val writer = AacFile(target.path, decoder.sampleRate, channels = 1)
                val chain = SoundChain(decoder.sampleRate, config).apply { set(settings, immediate = true) }
                // everything off — the chain is not called at all: its limiter would still delay the sound
                val neutral = SoundRules.isNeutral(settings)
                val pcm = ShortArray(CHUNK)
                val samples = FloatArray(CHUNK)
                var toDrop = if (neutral) 0 else chain.latencySamples
                val tail = if (neutral) 0 else chain.tailSamples(settings) + chain.latencySamples
                val total = (decoder.totalSamples + tail).coerceAtLeast(1)
                var done = 0L
                var tailLeft = tail
                var written = true
                while (written) {
                    ensureActive()
                    var count = decoder.read(pcm)
                    if (count == PcmSource.END) {
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
                    for (i in from until count) pcm[i - from] = toShort(samples[i])
                    written = writer.write(pcm, count - from)
                    done += count
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
                whole = writer.close() && written && target.sizeBytes() > 0
                whole
            } finally {
                decoder.release()
                if (!whole) target.deleteFile()
            }
        }

    /**
     * Two passes, as on Android: the sound into a temporary `.m4a`, then the picture of the source taken as it is with
     * the new sound beside it. No picture is decoded, so this takes hardly longer than the sound alone.
     */
    override suspend fun renderVideo(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean {
        val sound = target.sibling("${target.path.substringAfterLast('/')}$SOUND_SUFFIX")
        var whole = false
        try {
            if (!render(source, settings, sound) { onProgress(it * SOUND_SHARE) }) return false
            whole = mux(source, sound, target)
            onProgress(1f)
            return whole
        } finally {
            sound.deleteFile()
            if (!whole) target.deleteFile()
        }
    }

    private suspend fun mux(video: PlatformFile, sound: PlatformFile, target: PlatformFile): Boolean {
        val picture = AVURLAsset(uRL = NSURL.fileURLWithPath(video.path), options = null)
        val audio = AVURLAsset(uRL = NSURL.fileURLWithPath(sound.path), options = null)
        val pictureTrack = picture.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return false
        val soundTrack = audio.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return false
        val composition = AVMutableComposition()
        val toPicture = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, kCMPersistentTrackID_Invalid) as? AVMutableCompositionTrack ?: return false
        val toSound = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, kCMPersistentTrackID_Invalid) as? AVMutableCompositionTrack ?: return false
        if (!toPicture.insertTimeRange(pictureTrack.timeRange, pictureTrack, kCMTimeZero.readValue(), null)) return false
        // the rendered sound rings on past the picture: it is cut where the picture ends
        if (!toSound.insertTimeRange(CMTimeRangeMake(kCMTimeZero.readValue(), picture.duration), soundTrack, kCMTimeZero.readValue(), null)) return false
        // the turn of the camera lives in the track, not in the samples
        toPicture.preferredTransform = pictureTrack.preferredTransform
        val export = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetPassthrough) ?: return false
        export.outputURL = NSURL.fileURLWithPath(target.path)
        export.outputFileType = AVFileTypeMPEG4
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { export.cancelExport() }
            export.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(export.status == AVAssetExportSessionStatusCompleted && target.sizeBytes() > 0)
            }
        }
    }

    private fun toShort(sample: Float): Short = (sample * FULL_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val CHUNK = 4_096
        const val FULL_SCALE = 32_768f

        /** The sound is nearly all of the work of a video: no picture is decoded. */
        const val SOUND_SHARE = 0.9f
        const val SOUND_SUFFIX = ".sound.m4a"
    }
}
