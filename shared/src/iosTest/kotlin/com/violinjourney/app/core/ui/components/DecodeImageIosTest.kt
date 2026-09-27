package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
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
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * A page of the stand on iOS is decoded at the width it is shown (spec 5.9): by ImageIO below the width of the file, in
 * full only when zoomed, and into a sealed bitmap, which is not copied on every record of the layer that draws it.
 */
@OptIn(ExperimentalForeignApi::class)
class DecodeImageIosTest {
    private val files = NSFileManager.defaultManager
    private val made = mutableListOf<String>()

    @AfterTest
    fun cleanUp() = made.forEach { files.removeItemAtPath(it, null) }

    @Test
    fun `a page is decoded at the width it is shown and keeps its colours`() {
        val page = jpeg()
        val shown = assertNotNull(decodeImageFileAtWidth(page, wantedWidthPx = 800, loadedWidthPx = 0))
        assertTrue(abs(shown.width - 800) <= 1 && abs(shown.height - 1000) <= 2, "${shown.width}×${shown.height}")
        val bitmap = shown.asSkiaBitmap()
        assertTrue(bitmap.isImmutable, "a sealed bitmap")
        // red on the left, blue on the right: the order of the channels is Skia's
        val left = bitmap.getColor(100, 500)
        val right = bitmap.getColor(700, 500)
        assertTrue(red(left) > HIGH && blue(left) < LOW, "left ${left.toUInt().toString(16)}")
        assertTrue(blue(right) > HIGH && red(right) < LOW, "right ${right.toUInt().toString(16)}")
    }

    @Test
    fun `no narrower picture is decoded again — a zoom brings the whole page`() {
        val page = jpeg()
        assertNull(decodeImageFileAtWidth(page, wantedWidthPx = 800, loadedWidthPx = 800))
        assertNull(decodeImageFileAtWidth(page, wantedWidthPx = 600, loadedWidthPx = 800))
        val whole = assertNotNull(decodeImageFileAtWidth(page, wantedWidthPx = Int.MAX_VALUE, loadedWidthPx = 800))
        assertEquals(WIDTH to HEIGHT, whole.width to whole.height)
        assertTrue(whole.asSkiaBitmap().isImmutable, "the whole page is sealed too")
        assertNull(decodeImageFileAtWidth(page, wantedWidthPx = Int.MAX_VALUE, loadedWidthPx = WIDTH))
    }

    @Test
    fun `a file that is gone or is not a picture gives nothing`() {
        assertNull(decodeImageFileAtWidth("${NSTemporaryDirectory()}${NSUUID().UUIDString}.jpg", 800, 0))
        val text = "${NSTemporaryDirectory()}${NSUUID().UUIDString}.jpg"
        assertTrue(("not a picture" as NSString).writeToFile(text, atomically = true, encoding = NSUTF8StringEncoding, error = null))
        made += text
        assertNull(decodeImageFileAtWidth(text, 800, 0))
        assertNull(decodeImageFile(text))
    }

    @Test
    fun `a small picture of the app is sealed as well`() {
        val picture = assertNotNull(decodeImageFile(jpeg()))
        assertEquals(WIDTH, picture.width)
        assertTrue(picture.asSkiaBitmap().isImmutable, "thumbnails and the avatar are not copied on every record either")
    }

    private fun red(argb: Int) = (argb shr 16) and 0xFF

    private fun blue(argb: Int) = argb and 0xFF

    /** A page of [WIDTH] × [HEIGHT] pixels, red on the left half and blue on the right, written as JPEG. */
    private fun jpeg(): String {
        val path = "${NSTemporaryDirectory()}${NSUUID().UUIDString}.jpg"
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null, WIDTH.convert(), HEIGHT.convert(), BITS_PER_COMPONENT.convert(), 0.convert(), space,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        CGContextSetRGBFillColor(context, 1.0, 0.0, 0.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, WIDTH / 2.0, HEIGHT.toDouble()))
        CGContextSetRGBFillColor(context, 0.0, 0.0, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(WIDTH / 2.0, 0.0, WIDTH / 2.0, HEIGHT.toDouble()))
        val image = CGBitmapContextCreateImage(context)
        val written = UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), JPEG_QUALITY)?.writeToFile(path, atomically = true) == true
        CGImageRelease(image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
        assertTrue(written, "a JPEG is written to $path")
        made += path
        return path
    }

    private companion object {
        const val WIDTH = 1600
        const val HEIGHT = 2000
        const val BITS_PER_COMPONENT = 8
        const val JPEG_QUALITY = 0.9
        const val HIGH = 200
        const val LOW = 60
    }
}
