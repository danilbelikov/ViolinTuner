package com.violinjourney.app.ios

import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.time.WallClock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * The files of the iOS app, as the app keeps them on Android: bare names in the database, the files in folders of
 * Application Support. Pictures are read and turned upright by UIKit (HEIC and the orientation of a photo included),
 * scaled down and written as JPEG — first under another name, so a file with the final name is always whole.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosFolders {
    private val files = NSFileManager.defaultManager

    fun folder(name: String): String = "${IosStorage.dataDirectory()}/$name".also {
        files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }

    fun exists(path: String): Boolean = files.fileExistsAtPath(path)

    fun delete(path: String) {
        files.removeItemAtPath(path, null)
    }

    fun names(folder: String): List<String> =
        files.contentsOfDirectoryAtPath(folder, null)?.mapNotNull { it as? String }.orEmpty()

    /** When the file was last written, in ms since the epoch; 0 for a file that is not there. */
    fun modifiedMs(path: String): Long =
        ((files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(MS).toLong()

    fun move(from: String, to: String): Boolean = files.moveItemAtPath(from, to, null)

    private const val MS = 1000.0
}

/** Pictures by UIKit: read upright, drawn at a size of pixels, written as JPEG. */
@OptIn(ExperimentalForeignApi::class)
internal object IosPictures {
    /** Width and height in pixels, upright. */
    fun pixels(image: UIImage): Pair<Double, Double> = image.size.useContents { width * image.scale to height * image.scale }

    /** [image] drawn in a canvas of [width] × [height] pixels at [x], [y] (pixels) and [scale] of its own pixels. */
    fun draw(image: UIImage, width: Int, height: Int, x: Double, y: Double, scale: Double): UIImage {
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
        val (w, h) = pixels(image)
        return renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(x, y, w * scale, h * scale)) }
    }

    /** Scaled so that the long side is at most [maxLongSide] pixels; never enlarged. */
    fun fitted(image: UIImage, maxLongSide: Int): UIImage {
        val (w, h) = pixels(image)
        val scale = minOf(1.0, maxLongSide / maxOf(w, h))
        return draw(image, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), 0.0, 0.0, scale)
    }

    fun writeJpeg(image: UIImage, path: String, quality: Int): Boolean {
        val data = UIImageJPEGRepresentation(image, quality / PERCENT) ?: return false
        val partial = "$path$PARTIAL_SUFFIX"
        if (data.writeToFile(partial, atomically = true) && IosFolders.move(partial, path)) return true
        IosFolders.delete(partial)
        return false
    }

    /** The picture is nearly black — a video fading in: its average over a coarse grid is below [DARK_BELOW] of 255. */
    fun isDark(image: CGImageRef): Boolean {
        val side = DARK_GRID
        val pixels = UByteArray(side * side * RGBA)
        pixels.usePinned { pinned ->
            val space = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0), side.convert(), side.convert(), BITS_PER_BYTE.convert(), (side * RGBA).convert(), space,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            )
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, side.toDouble(), side.toDouble()), image)
            CGContextRelease(context)
            CGColorSpaceRelease(space)
        }
        var sum = 0L
        for (i in 0 until side * side) sum += (pixels[i * RGBA].toInt() + pixels[i * RGBA + 1].toInt() + pixels[i * RGBA + 2].toInt()) / 3
        return sum / (side * side) < DARK_BELOW
    }

    private const val PERCENT = 100.0
    private const val PARTIAL_SUFFIX = ".part"
    private const val DARK_GRID = 16
    private const val DARK_BELOW = 16
    private const val RGBA = 4
    private const val BITS_PER_BYTE = 8
}

/** Session audio in `sessions/<uuid>.m4a` — the videos of takes live here too, with a thumbnail beside them. */
internal class IosSessionAudioFiles(private val repertoireConfig: RepertoireConfig) : SessionAudioFiles {
    private val directory by lazy { IosFolders.folder(DIRECTORY) }

    override fun newFile(): PlatformFile = PlatformFile("$directory/${NSUUID().UUIDString}$EXTENSION")

    // Names come from the database; a name with a path in it is not one of ours.
    override fun existing(name: String): PlatformFile? =
        if ('/' in name || !IosFolders.exists("$directory/$name")) null else PlatformFile("$directory/$name")

    override fun delete(name: String) {
        if ('/' in name) return
        IosFolders.delete("$directory/$name")
        IosFolders.delete("$directory/${thumbNameOf(name)}")
    }

    override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        val videoMinAgeMs = maxOf(minAgeMs, repertoireConfig.orphanVideoMinAgeMs)
        val thumbs = referenced.mapTo(HashSet(), ::thumbNameOf)
        IosFolders.names(directory)
            .filter { it !in referenced && it !in thumbs }
            .filter { nowEpochMs - IosFolders.modifiedMs("$directory/$it") > if (it.endsWith(EXTENSION)) minAgeMs else videoMinAgeMs }
            .forEach { IosFolders.delete("$directory/$it") }
    }

    private fun thumbNameOf(name: String) = name.substringBeforeLast('.') + THUMB_SUFFIX

    private companion object {
        const val DIRECTORY = "sessions"
        const val EXTENSION = ".m4a"
        const val THUMB_SUFFIX = "-thumb.jpg"
    }
}

/** The profile photo in `profile/`, one at a time: a square around the centre, 512 px at most (as on Android). */
@OptIn(ExperimentalForeignApi::class)
internal class IosAvatarFiles(private val io: CoroutineDispatcher, private val clock: WallClock) : AvatarFiles {
    private val directory by lazy { IosFolders.folder(DIRECTORY) }

    override suspend fun import(sourceUri: String): String? = withContext(io) {
        val image = UIImage.imageWithContentsOfFile(sourceUri.removePrefix(FILE_SCHEME)) ?: return@withContext null
        val (w, h) = IosPictures.pixels(image)
        val side = minOf(w, h)
        val scale = minOf(1.0, AVATAR_SIZE_PX / side)
        val size = (side * scale).toInt().coerceAtLeast(1)
        val square = IosPictures.draw(image, size, size, -(w - side) / 2 * scale, -(h - side) / 2 * scale, scale)
        val name = "$PREFIX${clock.millis()}$EXTENSION"
        if (IosPictures.writeJpeg(square, "$directory/$name", JPEG_QUALITY)) name else null
    }

    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf(IosFolders::exists)?.let(::PlatformFile)

    override suspend fun delete(name: String) = withContext(io) { IosFolders.delete("$directory/$name") }

    override suspend fun deleteOrphans(referenced: String?) = withContext(io) {
        IosFolders.names(directory).filter { it != referenced }.forEach { IosFolders.delete("$directory/$it") }
    }

    private companion object {
        const val DIRECTORY = "profile"
        const val PREFIX = "avatar-"
        const val EXTENSION = ".jpg"
        const val JPEG_QUALITY = 90
        const val AVATAR_SIZE_PX = 512.0
        const val FILE_SCHEME = "file://"
    }
}

/** Sheet photos in `repertoire/`, each with a thumbnail; shots of the camera pass through `camera/` (as on Android). */
internal class IosSheetFiles(private val io: CoroutineDispatcher, private val config: RepertoireConfig) : SheetFiles {
    private val directory by lazy { IosFolders.folder(DIRECTORY) }
    private val cameraDirectory by lazy { IosFolders.folder(CAMERA_DIRECTORY) }

    override suspend fun import(sourceUri: String): SheetFiles.Stored? = withContext(io) {
        val image = UIImage.imageWithContentsOfFile(sourceUri.removePrefix(FILE_SCHEME)) ?: return@withContext null
        val page = IosPictures.fitted(image, config.pageMaxSidePx)
        val thumb = IosPictures.fitted(page, config.thumbMaxSidePx)
        val id = NSUUID().UUIDString
        val stored = SheetFiles.Stored(fileName = "$id$EXTENSION", thumbFileName = "$id$THUMB_SUFFIX$EXTENSION")
        val written = IosPictures.writeJpeg(page, "$directory/${stored.fileName}", config.pageJpegQuality) &&
            IosPictures.writeJpeg(thumb, "$directory/${stored.thumbFileName}", config.pageJpegQuality)
        if (written) {
            stored
        } else {
            IosFolders.delete("$directory/${stored.fileName}")
            IosFolders.delete("$directory/${stored.thumbFileName}")
            null
        }
    }

    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf(IosFolders::exists)?.let(::PlatformFile)

    override suspend fun delete(names: Collection<String>) = withContext(io) { names.forEach { IosFolders.delete("$directory/$it") } }

    override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = withContext(io) {
        IosFolders.names(directory)
            .filter { it !in referenced && nowEpochMs - IosFolders.modifiedMs("$directory/$it") >= minAgeMs }
            .forEach { IosFolders.delete("$directory/$it") }
        // A shot the camera wrote and nobody imported: the app died in between.
        IosFolders.names(cameraDirectory)
            .filter { nowEpochMs - IosFolders.modifiedMs("$cameraDirectory/$it") >= minAgeMs }
            .forEach { IosFolders.delete("$cameraDirectory/$it") }
    }

    override fun newCameraFile(): PlatformFile = PlatformFile("$cameraDirectory/${NSUUID().UUIDString}$EXTENSION")

    private companion object {
        const val DIRECTORY = "repertoire"
        const val CAMERA_DIRECTORY = "camera"
        const val EXTENSION = ".jpg"
        const val THUMB_SUFFIX = "-thumb"
        const val FILE_SCHEME = "file://"
    }
}
