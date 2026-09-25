package com.violinjourney.app.ios

import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoInfo
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.creationDate
import platform.AVFoundation.dateValue
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage

/**
 * Videos of takes on iOS (spec 3.19), where Android keeps them: beside the sound of sessions, `sessions/<uuid>.<ext>`
 * with `<uuid>-thumb.jpg`. The camera writes into `camera/`; the pickers hand over copies in the temporary folder as
 * `file:` URIs. The container is kept as it came (`.mov` from the camera of an iPhone). AVAudioFile on iOS opens no file
 * with a picture in it: the sound of a video is read by AVAssetReader (`IosPcmFileOpener`).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosVideoFiles(private val config: RepertoireConfig, private val io: CoroutineDispatcher) : VideoFiles {
    private val directory by lazy { IosFolders.folder(DIRECTORY) }
    private val cameraDirectory by lazy { IosFolders.folder(CAMERA_DIRECTORY) }
    private val files = NSFileManager.defaultManager

    override fun newCameraFile(): PlatformFile = PlatformFile("$cameraDirectory/${NSUUID().UUIDString}$CAMERA_EXTENSION")

    override fun adopt(cameraFile: PlatformFile): PlatformFile? {
        if (sizeOfPath(cameraFile.path) == 0L) return null
        val target = "$directory/${NSUUID().UUIDString}.${extensionOf(cameraFile.path)}"
        return if (IosFolders.move(cameraFile.path, target)) PlatformFile(target) else null
    }

    override fun sizeOf(uri: String): Long? = pathOf(uri)?.let(::sizeOfPath)

    override fun freeBytes(): Long =
        (files.attributesOfFileSystemForPath(directory, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0

    override suspend fun import(uri: String): PlatformFile? = withContext(io) {
        val source = pathOf(uri) ?: return@withContext null
        val target = "$directory/${NSUUID().UUIDString}.${extensionOf(source)}"
        // the picker's copy is ours already: it moves in; anything else is copied whole
        val done = IosFolders.move(source, target) || files.copyItemAtPath(source, target, null)
        if (done) PlatformFile(target) else null
    }

    override fun info(file: PlatformFile): VideoInfo? {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
        val video = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return null
        val (width, height) = seenSize(video)
        return VideoInfo(
            durationMs = (CMTimeGetSeconds(asset.duration) * MS_PER_SECOND).roundToLong(),
            width = width,
            height = height,
            createdAtEpochMs = asset.creationDate?.dateValue?.timeIntervalSince1970?.let { (it * MS_PER_SECOND).roundToLong() },
            hasSound = asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty(),
        )
    }

    override fun makeThumb(file: PlatformFile): Boolean {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
        val generator = AVAssetImageGenerator(asset = asset).apply {
            appliesPreferredTrackTransform = true
            maximumSize = CGSizeMake(config.thumbMaxSidePx.toDouble(), config.thumbMaxSidePx.toDouble())
        }
        // a video that fades in from black: the frame a second in says more — the first one is taken only when it is not dark
        val first = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, TIMESCALE), null, null)
        val later = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(1.0, TIMESCALE), null, null)
        val frame = (if (first != null && !IosPictures.isDark(first)) first else later ?: first) ?: return false
        return IosPictures.writeJpeg(UIImage.imageWithCGImage(frame), thumbPath(file.path.substringAfterLast('/')), THUMB_QUALITY)
    }

    override fun thumbOf(name: String): PlatformFile? = thumbPath(name).takeIf { '/' !in name && IosFolders.exists(it) }?.let(::PlatformFile)

    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf { '/' !in name && IosFolders.exists(it) }?.let(::PlatformFile)

    override fun discard(file: PlatformFile) {
        IosFolders.delete(thumbPath(file.path.substringAfterLast('/')))
        IosFolders.delete(file.path)
    }

    private fun thumbPath(name: String) = "$directory/${name.substringBeforeLast('.')}$THUMB_SUFFIX"

    private fun sizeOfPath(path: String): Long = (files.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0

    private fun pathOf(uri: String): String? = if (uri.startsWith("file:")) NSURL.URLWithString(uri)?.path else uri.takeIf { it.startsWith("/") }

    private fun extensionOf(path: String) = path.substringAfterLast('.', DEFAULT_EXTENSION).lowercase()

    /** As it is seen, the turn the camera recorded applied. */
    private fun seenSize(track: AVAssetTrack): Pair<Int, Int> = CGRectApplyAffineTransform(
        track.naturalSize.useContents { CGRectMake(0.0, 0.0, width, height) },
        track.preferredTransform,
    ).useContents { abs(size.width).roundToInt() to abs(size.height).roundToInt() }

    private companion object {
        const val DIRECTORY = "sessions"
        const val CAMERA_DIRECTORY = "camera"
        const val CAMERA_EXTENSION = ".mov"
        const val DEFAULT_EXTENSION = "mp4"
        const val THUMB_SUFFIX = "-thumb.jpg"
        const val THUMB_QUALITY = 85
        const val MS_PER_SECOND = 1_000.0
        const val TIMESCALE = 600
    }
}
