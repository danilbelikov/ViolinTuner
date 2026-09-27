package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.audio.backing.IosBackingPcmReader
import com.violinjourney.app.core.audio.recording.AacFile
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.sibling
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.IosPcmFileOpener
import com.violinjourney.app.core.recording.PcmFileOpener
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CoroutineDispatcher
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
 * [SoundRenderer] of iOS: the sound track read by AVAssetReader → [SoundRenderLoop] — the very chain the player plays
 * through, without the clock, the same loop as `SoundFileRenderer` on Android (spec 3.17) — → AAC. A video take gets its
 * picture back as it was — not re-encoded — beside the rendered sound, by AVAssetExportSession. A take under a backing is
 * mixed with it into a stereo file (spec 3.32). A sound track the reader gave up on half-way is no file to send: the
 * render fails rather than hand over a cut one.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSoundRenderer(
    private val config: SoundConfig,
    private val io: CoroutineDispatcher,
    /** How the sound track is read; a test gives one that breaks off. */
    private val opener: PcmFileOpener = IosPcmFileOpener,
) : SoundRenderer {
    override suspend fun render(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean =
        renderSound(source, settings, null, target, onProgress)

    override suspend fun renderWithBacking(source: PlatformFile, settings: SoundSettings, backing: RenderBacking, target: PlatformFile, onProgress: (Float) -> Unit): Boolean =
        renderSound(source, settings, backing, target, onProgress)

    override suspend fun renderVideoWithBacking(
        source: PlatformFile,
        settings: SoundSettings,
        backing: RenderBacking,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
    ): Boolean = renderVideoSound(source, target, onProgress) { sound, progress -> renderWithBacking(source, settings, backing, sound, progress) }

    private suspend fun renderSound(source: PlatformFile, settings: SoundSettings, backing: RenderBacking?, target: PlatformFile, onProgress: (Float) -> Unit): Boolean =
        withContext(io) {
            val decoder = opener.open(source) ?: return@withContext false
            var whole = false
            var reader: IosBackingPcmReader? = null
            // What the system refuses here — no room for the file, a rate the encoder will not take, the backing's sound
            // gone — comes out as a throw, and «Поделиться» makes it «Не получилось»; the file is let go of either way.
            var writer: AacFile? = null
            var closed = false
            try {
                // the backing at this recording's rate; gone or undecodable — the file cannot be what was asked for
                reader = backing?.let { IosBackingPcmReader(it.pcm(decoder.sampleRate) ?: return@withContext false) }
                val mix = reader?.let { if (backing != null) RenderMix(it, backing.offsetMs, backing.gainDb) else null }
                val aac = AacFile(target.path, decoder.sampleRate, channels = if (mix != null) 2 else 1).also { writer = it }
                val written = SoundRenderLoop.run(decoder, settings, config, mix, write = aac::write, onProgress = onProgress, gaveUp = { decoder.broken })
                closed = true
                // the reader gave up half-way: what is written is not the take — the file goes below
                whole = aac.close() && written && !decoder.broken && target.sizeBytes() > 0
                whole
            } finally {
                // disposed exactly once: by the close above, or here when something threw before it
                if (!closed) writer?.close()
                decoder.release()
                reader?.close()
                if (!whole) target.deleteFile()
            }
        }

    /**
     * Two passes, as on Android: the sound into a temporary `.m4a`, then the picture of the source taken as it is with
     * the new sound beside it. No picture is decoded, so this takes hardly longer than the sound alone.
     */
    override suspend fun renderVideo(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean =
        renderVideoSound(source, target, onProgress) { sound, progress -> render(source, settings, sound, progress) }

    private suspend fun renderVideoSound(
        source: PlatformFile,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
        renderSound: suspend (PlatformFile, (Float) -> Unit) -> Boolean,
    ): Boolean {
        val sound = target.sibling("${target.path.substringAfterLast('/')}$SOUND_SUFFIX")
        var whole = false
        try {
            if (!renderSound(sound) { onProgress(it * SOUND_SHARE) }) return false
            whole = mux(source, sound, target)
            onProgress(1f)
            return whole
        } finally {
            sound.deleteFile()
            if (!whole) target.deleteFile()
        }
    }

    /** The picture of [video] as it is, with [sound] beside it, into [target]; internal for a test. */
    internal suspend fun mux(video: PlatformFile, sound: PlatformFile, target: PlatformFile): Boolean {
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
        // AVAssetExportSession writes no file over one that is there: whatever an export the system ended with the app
        // left under this name goes first, or every try with the same settings would fail
        target.deleteFile()
        export.outputURL = NSURL.fileURLWithPath(target.path)
        export.outputFileType = AVFileTypeMPEG4
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { export.cancelExport() }
            export.exportAsynchronouslyWithCompletionHandler {
                continuation.resume(export.status == AVAssetExportSessionStatusCompleted && target.sizeBytes() > 0)
            }
        }
    }

    private companion object {
        /** The sound is nearly all of the work of a video: no picture is decoded. */
        const val SOUND_SHARE = 0.9f
        const val SOUND_SUFFIX = ".sound.m4a"
    }
}
