package com.violinjourney.app.ios

import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.DeviceOnly
import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.isOwnFileName
import com.violinjourney.app.core.io.isRegularFile
import com.violinjourney.app.core.io.pathOfFileUri
import com.violinjourney.app.core.recording.video.VideoThumbs
import com.violinjourney.app.core.time.WallClock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.cinterop.ptr
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
import platform.CoreGraphics.CGImageRelease
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFNumberRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceGetPrimaryImageIndex
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.ImageIO.kCGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
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
import platform.posix.rename

/**
 * The files of the iOS app, as the app keeps them on Android: bare names in the database, the files in folders of
 * Application Support. Pictures are read and turned upright (HEIC and the orientation of a photo included) — sheet pages
 * by ImageIO straight at their size, avatars by UIKit — scaled down and written as JPEG, first under another name, so a
 * file with the final name is always whole. The folders of data go into the backup of the phone; the helpers — what is
 * reckoned again or only passes through — are opened by [deviceOnlyFolder] and stay out of it (spec 5.14).
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosFolders {
    private val files = NSFileManager.defaultManager

    fun folder(name: String): String = "${IosStorage.dataDirectory()}/$name".also {
        files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }

    /**
     * A folder of Application Support that stays out of the backup of the phone ([DeviceOnly]). Marked when it is made —
     * again after a restore or a reset deleted it — and once for a folder made before there were marks; a folder marked
     * already is only looked at.
     */
    fun deviceOnlyFolder(name: String): String = folder(name).also { if (!DeviceOnly.isMarked(it)) DeviceOnly.mark(it) }

    /** There, and not a folder: what a name from the database may point at. */
    fun isFile(path: String): Boolean = PlatformFile(path).isRegularFile()

    /** A file, or an empty folder — never a folder with things in it, whatever name reaches here (as on Android). */
    fun delete(path: String) {
        PlatformFile(path).deleteFile()
    }

    fun names(folder: String): List<String> =
        files.contentsOfDirectoryAtPath(folder, null)?.mapNotNull { it as? String }.orEmpty()

    /** When the file was last written, in ms since the epoch; 0 for a file that is not there. */
    fun modifiedMs(path: String): Long =
        ((files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(MS).toLong()

    fun move(from: String, to: String): Boolean = files.moveItemAtPath(from, to, null)

    /** [from] takes the name [to] in one step, the file already there replaced whole (rename(2)); false when it cannot. */
    fun replace(from: String, to: String): Boolean = rename(from, to) == 0

    private const val MS = 1000.0
}

/**
 * Pictures by UIKit and ImageIO: read upright (sheet pages straight at their size, see [downsampled]), drawn at a size
 * of pixels, written as JPEG.
 */
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

    /**
     * The picture in the file at [path], read by ImageIO straight at a long side of at most [maxLongSide] pixels and
     * turned upright by its EXIF: the whole frame — 24 or 48 Mp from a phone's camera, some hundred megabytes unpacked —
     * is never held in memory. Never enlarged. Null when the file is not a picture.
     */
    fun downsampled(path: String, maxLongSide: Int): UIImage? =
        downsampledImage(path, maxLongSide)?.let { picture -> UIImage.imageWithCGImage(picture).also { CGImageRelease(picture) } }

    /** [downsampled] as the CoreGraphics picture itself; the caller releases it (`CGImageRelease`). */
    fun downsampledImage(path: String, maxLongSide: Int): CGImageRef? {
        val source = sourceOf(path) ?: return null
        val size = CFBridgingRetain(NSNumber(int = maxLongSide))
        val options = CFDictionaryCreateMutable(null, OPTIONS.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceShouldCacheImmediately, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, size)
        // the picture the file stands for — a HEIF may hold more than one
        val picture = CGImageSourceCreateThumbnailAtIndex(source, CGImageSourceGetPrimaryImageIndex(source), options)
        CFRelease(options)
        CFRelease(size)
        CFRelease(source)
        return picture
    }

    /**
     * The size of the picture at [path] in pixels as it is shown — turned by its EXIF, as [downsampled] turns it: an
     * orientation of 5 to 8 lies on its side and swaps the two. Read from the header, nothing is decoded. Null when the
     * file is not a picture.
     */
    fun uprightSize(path: String): Pair<Int, Int>? {
        val source = sourceOf(path) ?: return null
        val properties = CGImageSourceCopyPropertiesAtIndex(source, CGImageSourceGetPrimaryImageIndex(source), null)
        CFRelease(source)
        if (properties == null) return null
        val width = intOf(properties, kCGImagePropertyPixelWidth)
        val height = intOf(properties, kCGImagePropertyPixelHeight)
        val orientation = intOf(properties, kCGImagePropertyOrientation) ?: UPRIGHT
        CFRelease(properties)
        if (width == null || height == null || width <= 0 || height <= 0) return null
        return if (orientation in SIDEWAYS) height to width else width to height
    }

    private fun sourceOf(path: String): CGImageSourceRef? {
        @Suppress("UNCHECKED_CAST")
        val url = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef
        val source = CGImageSourceCreateWithURL(url, null)
        CFRelease(url)
        return source
    }

    private fun intOf(dictionary: CFDictionaryRef, key: CFStringRef?): Int? = memScoped {
        val number: CFNumberRef = CFDictionaryGetValue(dictionary, key)?.reinterpret() ?: return null
        val value = alloc<IntVar>()
        if (CFNumberGetValue(number, kCFNumberIntType, value.ptr)) value.value else null
    }

    fun writeJpeg(image: UIImage, path: String, quality: Int): Boolean {
        val data = UIImageJPEGRepresentation(image, quality / PERCENT) ?: return false
        val partial = "$path$PARTIAL_SUFFIX"
        // a thumbnail made anew takes the place of the old one (spec 5.31): a move would refuse a name that is taken
        if (data.writeToFile(partial, atomically = true) && IosFolders.replace(partial, path)) return true
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
    private const val OPTIONS = 4
    private const val UPRIGHT = 1

    /** EXIF orientations that turn the picture by a quarter: shown, its width is the stored height. */
    private val SIDEWAYS = 5..8
    private const val PARTIAL_SUFFIX = ".part"
    private const val DARK_GRID = 16
    private const val DARK_BELOW = 16
    private const val RGBA = 4
    private const val BITS_PER_BYTE = 8
}

/** Session audio in `sessions/<uuid>.m4a` — the videos of takes live here too, with a thumbnail beside them. */
internal class IosSessionAudioFiles(private val repertoireConfig: RepertoireConfig) : SessionAudioFiles {
    private val directory by lazy { IosFolders.folder(DataLayout.SESSIONS) }

    override fun newFile(): PlatformFile = PlatformFile("$directory/${NSUUID().UUIDString}$EXTENSION")

    // Names come from the database, and a database may come from a copy: a name that leaves the folder is not one of ours.
    override fun existing(name: String): PlatformFile? =
        if (!isOwnFileName(name) || !IosFolders.isFile("$directory/$name")) null else PlatformFile("$directory/$name")

    override fun delete(name: String) {
        if (!isOwnFileName(name)) return
        IosFolders.delete("$directory/$name")
        IosFolders.delete("$directory/${VideoThumbs.nameOf(name)}")
    }

    override fun thumbOf(name: String): PlatformFile? =
        "$directory/${VideoThumbs.nameOf(name)}".takeIf { isOwnFileName(name) && IosFolders.isFile(it) }?.let(::PlatformFile)

    override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        val videoMinAgeMs = maxOf(minAgeMs, repertoireConfig.orphanVideoMinAgeMs)
        val thumbs = referenced.mapTo(HashSet(), VideoThumbs::nameOf)
        IosFolders.names(directory)
            .filter { it !in referenced && it !in thumbs }
            .filter { nowEpochMs - IosFolders.modifiedMs("$directory/$it") > if (it.endsWith(EXTENSION)) minAgeMs else videoMinAgeMs }
            .forEach { IosFolders.delete("$directory/$it") }
    }

    private companion object {
        const val EXTENSION = ".m4a"
    }
}

/** The profile photo in `profile/`, one at a time: a square around the centre, 512 px at most (as on Android). */
@OptIn(ExperimentalForeignApi::class)
internal class IosAvatarFiles(private val io: CoroutineDispatcher, private val clock: WallClock) : AvatarFiles {
    private val directory by lazy { IosFolders.folder(DataLayout.PROFILE) }

    override suspend fun import(sourceUri: String): String? = withContext(io) {
        val path = pathOfFileUri(sourceUri) ?: return@withContext null
        // the picker's copy is the app's own: once read it goes, whatever came of it
        try {
            storeSquare(path)
        } finally {
            PickedCopies.release(path)
        }
    }

    private fun storeSquare(path: String): String? {
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        val (w, h) = IosPictures.pixels(image)
        val side = minOf(w, h)
        val scale = minOf(1.0, AVATAR_SIZE_PX / side)
        val size = (side * scale).toInt().coerceAtLeast(1)
        val square = IosPictures.draw(image, size, size, -(w - side) / 2 * scale, -(h - side) / 2 * scale, scale)
        val name = "$PREFIX${clock.millis()}$EXTENSION"
        return if (IosPictures.writeJpeg(square, "$directory/$name", JPEG_QUALITY)) name else null
    }

    // a name that leaves the folder is not one of ours — a delete by it would reach a file outside it, the database maybe
    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf { isOwnFileName(name) && IosFolders.isFile(it) }?.let(::PlatformFile)

    override suspend fun delete(name: String) = withContext(io) { if (isOwnFileName(name)) IosFolders.delete("$directory/$name") }

    override suspend fun deleteOrphans(referenced: String?) = withContext(io) {
        IosFolders.names(directory).filter { it != referenced }.forEach { IosFolders.delete("$directory/$it") }
    }

    private companion object {
        const val PREFIX = "avatar-"
        const val EXTENSION = ".jpg"
        const val JPEG_QUALITY = 90
        const val AVATAR_SIZE_PX = 512.0
    }
}

/**
 * Sheet photos in `repertoire/`, each with a thumbnail; shots of the camera pass through `camera/` (as on Android), a
 * folder out of the backup of the phone.
 */
internal class IosSheetFiles(private val io: CoroutineDispatcher, private val config: RepertoireConfig) : SheetFiles {
    private val directory by lazy { IosFolders.folder(DataLayout.SHEETS) }
    private val cameraDirectory by lazy { IosFolders.deviceOnlyFolder(DataLayout.CAMERA) }

    // One picture per pool: the pictures and their data are let go when it is done, not when the thread of io next drains.
    override suspend fun import(sourceUri: String): SheetFiles.Stored? = withContext(io) { autoreleasepool { importNow(sourceUri) } }

    private fun importNow(sourceUri: String): SheetFiles.Stored? {
        val path = pathOfFileUri(sourceUri) ?: return null
        // A copy of the photo picker is the app's own: once read it goes, whatever came of it. A shot of the camera is no
        // copy — it lies in `camera/`, and the screen that asked for it deletes it.
        return try {
            store(path)
        } finally {
            PickedCopies.release(path)
        }
    }

    private fun store(path: String): SheetFiles.Stored? {
        // the page straight at its size (spec 5.9): the full frame is never unpacked
        val page = IosPictures.downsampled(path, config.pageMaxSidePx) ?: return null
        val thumb = IosPictures.fitted(page, config.thumbMaxSidePx)
        val id = NSUUID().UUIDString
        val stored = SheetFiles.Stored(fileName = "$id$EXTENSION", thumbFileName = "$id$THUMB_SUFFIX$EXTENSION")
        val written = IosPictures.writeJpeg(page, "$directory/${stored.fileName}", config.pageJpegQuality) &&
            IosPictures.writeJpeg(thumb, "$directory/${stored.thumbFileName}", config.pageJpegQuality)
        if (written) return stored
        IosFolders.delete("$directory/${stored.fileName}")
        IosFolders.delete("$directory/${stored.thumbFileName}")
        return null
    }

    // a name that leaves the folder is not one of ours — a delete by it would reach a file outside it, the database maybe
    override fun existing(name: String): PlatformFile? = "$directory/$name".takeIf { isOwnFileName(name) && IosFolders.isFile(it) }?.let(::PlatformFile)

    override suspend fun delete(names: Collection<String>) = withContext(io) { names.filter(::isOwnFileName).forEach { IosFolders.delete("$directory/$it") } }

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
        const val EXTENSION = ".jpg"
        const val THUMB_SUFFIX = "-thumb"
    }
}
