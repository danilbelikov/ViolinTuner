package com.violinjourney.app.ios

import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileUri
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
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
    private val sheets = IosSheetFiles(Dispatchers.Default, RepertoireConfig())
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
    }

    @Test
    fun `a copy of the photo picker is imported as before`() = runTest {
        val copy = jpeg("${NSTemporaryDirectory()}${NSUUID().UUIDString}.jpg")
        assertStored(assertNotNull(sheets.import(PlatformFile(copy).fileUri)))
    }

    @Test
    fun `an avatar is imported by a uri with a space and by a bare path`() = runTest {
        val shot = jpeg("$folder/face.jpg")
        for (source in listOf(PlatformFile(shot).fileUri, shot)) {
            val name = assertNotNull(avatars.import(source), source)
            made += assertNotNull(avatars.existing(name), name).path
        }
    }

    /** The page and its thumbnail are in the folder of the repertoire; both go away after the test. */
    private fun assertStored(stored: SheetFiles.Stored) {
        for (name in listOf(stored.fileName, stored.thumbFileName)) made += assertNotNull(sheets.existing(name), name).path
    }

    /** A picture of 40 × 30 pixels drawn by CoreGraphics, written as JPEG to [path]. */
    private fun jpeg(path: String): String {
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null, WIDTH.convert(), HEIGHT.convert(), BITS_PER_COMPONENT.convert(), 0.convert(), space,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextSetRGBFillColor(context, 0.2, 0.6, 0.3, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, WIDTH.toDouble(), HEIGHT.toDouble()))
        val image = CGBitmapContextCreateImage(context)
        val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), JPEG_QUALITY)
        CGImageRelease(image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
        assertTrue(data?.writeToFile(path, atomically = true) == true, "a JPEG is written to $path")
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
