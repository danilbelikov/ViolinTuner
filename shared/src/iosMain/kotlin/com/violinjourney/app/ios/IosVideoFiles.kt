package com.violinjourney.app.ios

import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.availableBytes
import com.violinjourney.app.core.io.isOwnFileName
import com.violinjourney.app.core.io.pathOfFileUri
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
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage

/**
 * Videos of takes on iOS (spec 3.19), where Android keeps them: beside the sound of sessions, `sessions/<uuid>.<ext>`
 * with `<uuid>-thumb.jpg`. The camera writes into `camera/`, a folder out of the backup of the phone; the pickers hand
 * over copies in `tmp/picked/` as `file:` URIs ([PickedCopies]), and every copy goes once it is in or will not come in.
 * The container is kept as it came (`.mov` from the camera of an iPhone). AVAudioFile on iOS opens no file with a picture
 * in it: the sound of a video is read by AVAssetReader (`IosPcmFileOpener`).
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosVideoFiles(private val config: RepertoireConfig, private val io: CoroutineDispatcher) : VideoFiles {
    private val directory by lazy { IosFolders.folder(DataLayout.SESSIONS) }
    private val cameraDirectory by lazy { IosFolders.deviceOnlyFolder(DataLayout.CAMERA) }
    private val files = NSFileManager.defaultManager

    override fun newCameraFile(): PlatformFile = PlatformFile("$cameraDirectory/${NSUUID().UUIDString}$CAMERA_EXTENSION")

    override fun adopt(cameraFile: PlatformFile): PlatformFile? {
        if (sizeOfPath(cameraFile.path) == 0L) return null
        val target = "$directory/${NSUUID().UUIDString}.${extensionOf(cameraFile.path)}"
        return if (IosFolders.move(cameraFile.path, target)) PlatformFile(target) else null
    }

    override fun sizeOf(uri: String): Long? = pathOfFileUri(uri)?.let(::sizeOfPath)

    // what iOS gives a write the person asked for, what it frees on demand included
    override fun freeBytes(): Long = PlatformFile(directory).availableBytes()

    override suspend fun import(uri: String): PlatformFile? = withContext(io) {
        val source = pathOfFileUri(uri) ?: return@withContext null
        try {
            val target = "$directory/${NSUUID().UUIDString}.${extensionOf(source)}"
            // the picker's copy is ours already: it moves in; anything else is copied whole
            val done = IosFolders.move(source, target) || files.copyItemAtPath(source, target, null)
            if (done) PlatformFile(target) else null
        } finally {
            // moved in, the folder of the pick is left empty; copied or not taken at all, the copy goes with it
            PickedCopies.release(source)
        }
    }

    // a copy of the picker that will not come in: gigabytes, maybe, the very room a video found missing
    override fun release(uri: String) {
        pathOfFileUri(uri)?.let { PickedCopies.release(it) }
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
        // A video that fades in from black: the frame a second in says more — it is decoded only when the first one is dark
        // or missing (spec 5.13). Both are the caller's under the Copy rule, and Kotlin/Native frees no CoreGraphics object:
        // each is released here, the one not taken at once, the one taken once UIImage holds its own reference.
        var frame = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, TIMESCALE), null, null)
        if (frame == null || IosPictures.isDark(frame)) {
            val later = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(LATER_FRAME_SECONDS, TIMESCALE), null, null)
            if (later != null) {
                CGImageRelease(frame)
                frame = later
            }
        }
        if (frame == null) return false
        return try {
            IosPictures.writeJpeg(UIImage.imageWithCGImage(frame), thumbPath(file.path.substringAfterLast('/')), THUMB_QUALITY)
        } finally {
            CGImageRelease(frame)
        }
    }

    // names come from the database, and a database may come from a copy: a name that leaves the folder is not one of ours
    override fun thumbOf(name: String): PlatformFile? = thumbPath(name).takeIf { isOwnFileName(name) && IosFolders.isFile(it) }?.let(::PlatformFile)

    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf { isOwnFileName(name) && IosFolders.isFile(it) }?.let(::PlatformFile)

    override fun discard(file: PlatformFile) {
        IosFolders.delete(thumbPath(file.path.substringAfterLast('/')))
        IosFolders.delete(file.path)
    }

    private fun thumbPath(name: String) = "$directory/${name.substringBeforeLast('.')}$THUMB_SUFFIX"

    private fun sizeOfPath(path: String): Long = (files.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0

    // of the name, not of the path: `…/<uuid>.debug-Inbox/clip` has a dot only in its folder
    private fun extensionOf(path: String) = path.substringAfterLast('/').substringAfterLast('.', "").lowercase().ifEmpty { DEFAULT_EXTENSION }

    /** As it is seen, the turn the camera recorded applied. */
    private fun seenSize(track: AVAssetTrack): Pair<Int, Int> = CGRectApplyAffineTransform(
        track.naturalSize.useContents { CGRectMake(0.0, 0.0, width, height) },
        track.preferredTransform,
    ).useContents { abs(size.width).roundToInt() to abs(size.height).roundToInt() }

    private companion object {
        const val CAMERA_EXTENSION = ".mov"
        const val DEFAULT_EXTENSION = "mp4"
        const val THUMB_SUFFIX = "-thumb.jpg"
        const val THUMB_QUALITY = 85
        const val MS_PER_SECOND = 1_000.0
        const val TIMESCALE = 600

        /** The frame a thumbnail takes when the first one is dark (spec 5.13). */
        const val LATER_FRAME_SECONDS = 1.0
    }
}
