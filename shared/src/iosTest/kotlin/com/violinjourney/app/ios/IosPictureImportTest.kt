package com.violinjourney.app.ios

import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileUri
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithURL
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.kCGImagePropertyOrientation
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * Pictures come into the app by the URI the platform hands over (`fileUri` on iOS: `file:`, escapes and all). The shot of
 * the camera lies in «Application Support» — a path with a space — and has to be read like the copy of the photo picker
 * in the temporary folder.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPictureImportTest {
    private val files = NSFileManager.defaultManager
    private val folder = "${NSTemporaryDirectory()}${NSUUID().UUIDString} shots"
    private val config = RepertoireConfig()
    private val sheets = IosSheetFiles(Dispatchers.Default, config)
    private val avatars = IosAvatarFiles(Dispatchers.Default, TickingClock())
    private val made = mutableListOf<String>()

    init {
        files.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun cleanUp() {
        made.forEach { files.removeItemAtPath(it, null) }
        files.removeItemAtPath(folder, null)
    }

    @Test
    fun `a page is imported from a folder with a space in its name`() = runTest {
        val shot = jpeg("$folder/shot.jpg")
        assertStored(assertNotNull(sheets.import(PlatformFile(shot).fileUri), "the uri of a path with a space is read"))
    }

    @Test
    fun `the shot of the camera is imported by its uri`() = runTest {
        val shot = sheets.newCameraFile()
        assertTrue(' ' in shot.path, "the camera writes into Application Support: ${shot.path}")
        jpeg(shot.path)
        assertStored(assertNotNull(sheets.import(shot.fileUri), "the shot of the camera is read"))
        assertTrue(files.fileExistsAtPath(shot.path), "a shot is no pick: the screen that asked for it deletes it")
    }

    @Test
    fun `a copy of the photo picker is imported and then goes with its folder`() = runTest {
        val copy = picked(jpeg("$folder/lent.jpg"))
        assertStored(assertNotNull(sheets.import(PlatformFile(copy).fileUri)))
        assertFalse(files.fileExistsAtPath(copy.substringBeforeLast('/')), "the pick is let go once read")
    }

    @Test
    fun `a copy of the photo picker that is no picture goes too`() = runTest {
        val text = "$folder/lent.jpg"
        assertTrue(("not a picture" as NSString).writeToFile(text, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        made += text
        val copy = picked(text)
        assertNull(sheets.import(PlatformFile(copy).fileUri))
        assertFalse(files.fileExistsAtPath(copy), "a refused pick is not left in tmp")
    }

    @Test
    fun `the copy of the avatar picker goes once read whether a picture or not`() = runTest {
        val photo = picked(jpeg("$folder/face.jpg"))
        made += assertNotNull(avatars.existing(assertNotNull(avatars.import(photo)))).path
        assertFalse(files.fileExistsAtPath(photo), "an avatar taken in")
        val text = "$folder/notes.jpg"
        assertTrue(("not a picture" as NSString).writeToFile(text, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        made += text
        val notPicture = picked(text)
        assertNull(avatars.import(notPicture))
        assertFalse(files.fileExistsAtPath(notPicture), "an avatar refused")
    }

    @Test
    fun `an avatar is imported by a uri with a space and by a bare path`() = runTest {
        val shot = jpeg("$folder/face.jpg")
        for (source in listOf(PlatformFile(shot).fileUri, shot)) {
            val name = assertNotNull(avatars.import(source), source)
            made += assertNotNull(avatars.existing(name), name).path
        }
    }

    // The page is read by ImageIO straight at its size (spec 5.9). These guard what the reading must keep — the size,
    // the turn of the EXIF, no enlarging; what it saves, the memory of a 24–48 Mp frame, shows only on a device.
    @Test
    fun `a large photo becomes a page of 2560 px and a thumbnail of 320`() = runTest {
        val stored = assertNotNull(sheets.import(PlatformFile(jpeg("$folder/large.jpg", width = 4000, height = 3000)).fileUri))
        assertStored(stored)
        assertSize(config.pageMaxSidePx, 1920, stored.fileName)
        assertSize(config.thumbMaxSidePx, 240, stored.thumbFileName)
    }

    @Test
    fun `a photo turned by its EXIF comes out upright`() = runTest {
        // stored lying on its side, 400 × 300, with «turn a quarter clockwise» for whoever shows it
        val stored = assertNotNull(sheets.import(PlatformFile(jpeg("$folder/turned.jpg", width = 400, height = 300, orientation = 6)).fileUri))
        assertStored(stored)
        assertSize(300, 400, stored.fileName)
    }

    @Test
    fun `a small picture is not enlarged`() = runTest {
        val stored = assertNotNull(sheets.import(PlatformFile(jpeg("$folder/small.jpg")).fileUri))
        assertStored(stored)
        assertSize(WIDTH, HEIGHT, stored.fileName)
    }

    @Test
    fun `a file that is not a picture is not a page`() = runTest {
        val text = "$folder/notes.jpg"
        assertTrue(("not a picture" as NSString).writeToFile(text, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        made += text
        assertNull(sheets.import(PlatformFile(text).fileUri))
    }

    /** What a picker hands over: a copy of [lent] in a pick of its own in `tmp/picked/`; gone after the test whatever happens. */
    private fun picked(lent: String): String {
        val copy = assertNotNull(PickedCopies.copy(NSURL.fileURLWithPath(lent), "${NSUUID().UUIDString}.jpg"))
        made += copy.substringBeforeLast('/')
        return copy
    }

    /** The page and its thumbnail are in the folder of the repertoire; both go away after the test. */
    private fun assertStored(stored: SheetFiles.Stored) {
        for (name in listOf(stored.fileName, stored.thumbFileName)) made += assertNotNull(sheets.existing(name), name).path
    }

    /** The stored picture [name] is [width] × [height] pixels as shown, give or take one of rounding. */
    private fun assertSize(width: Int, height: Int, name: String) {
        val image = assertNotNull(UIImage.imageWithContentsOfFile(assertNotNull(sheets.existing(name)).path), name)
        val (w, h) = IosPictures.pixels(image)
        assertTrue(abs(w - width) <= 1 && abs(h - height) <= 1, "$name is ${w}×$h, wanted ${width}×$height")
    }

    /**
     * A picture of [width] × [height] pixels drawn by CoreGraphics, written as JPEG to [path]; with [orientation], the EXIF
     * orientation of the file (ImageIO writes it, UIKit would not).
     */
    private fun jpeg(path: String, width: Int = WIDTH, height: Int = HEIGHT, orientation: Int? = null): String {
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null, width.convert(), height.convert(), BITS_PER_COMPONENT.convert(), 0.convert(), space,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextSetRGBFillColor(context, 0.2, 0.6, 0.3, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        val image = CGBitmapContextCreateImage(context)
        val written = if (orientation == null) {
            UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), JPEG_QUALITY)?.writeToFile(path, atomically = true) == true
        } else {
            @Suppress("UNCHECKED_CAST")
            val url = CFBridgingRetain(NSURL.fileURLWithPath(path)) as CFURLRef
            val type = CFStringCreateWithCString(null, "public.jpeg", kCFStringEncodingUTF8)
            val destination = CGImageDestinationCreateWithURL(url, type, 1u, null)
            val turn = CFBridgingRetain(NSNumber(int = orientation))
            val properties = CFDictionaryCreateMutable(null, 1, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
            CFDictionarySetValue(properties, kCGImagePropertyOrientation, turn)
            CGImageDestinationAddImage(destination, image, properties)
            val done = CGImageDestinationFinalize(destination)
            listOf(properties, turn, destination, type, url).forEach { CFRelease(it) }
            done
        }
        CGImageRelease(image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
        assertTrue(written, "a JPEG is written to $path")
        made += path
        return path
    }

    /**
     * A clock a millisecond later at each question: two avatars in a row never get one name. It starts at the real time, so
     * a run that died before its clean-up leaves no file a later run would collide with.
     */
    private class TickingClock : WallClock {
        private var ms = SystemWallClock.millis()
        override fun instant(): Instant = Instant.fromEpochMilliseconds(ms++)
        override val zone: TimeZone = TimeZone.UTC
    }

    private companion object {
        const val WIDTH = 40
        const val HEIGHT = 30
        const val BITS_PER_COMPONENT = 8
        const val JPEG_QUALITY = 0.9
    }
}
