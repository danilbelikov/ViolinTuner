package com.violinjourney.app.core.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.violinjourney.app.ios.IosPictures
import kotlin.math.ceil
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageFile(path: String): ImageBitmap? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: IllegalArgumentException) {
        null // not a picture: what BitmapFactory answers with null on Android
    }
}

/**
 * The whole picture by Skia when the shown width asks for all of it (a zoomed sheet); a narrower one by ImageIO straight
 * at that width — the JPEG decoder scales as it reads, so a 1920 px page shown 1146 px wide on a 3× iPhone is about 7 MB
 * instead of 19.6. The powers of two of Android would give nothing here: half of 1920 is already narrower than a 3× screen.
 * Both read the picture upright, as the stored pages are.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageFileAtWidth(path: String, wantedWidthPx: Int, loadedWidthPx: Int): ImageBitmap? {
    val (width, height) = IosPictures.uprightSize(path) ?: return null
    val target = minOf(width, wantedWidthPx)
    if (target <= loadedWidthPx) return null
    if (target == width) return decodeImageFile(path)
    val longSide = ceil(maxOf(width, height) * target.toDouble() / width).toInt()
    val picture = IosPictures.downsampledImage(path, longSide) ?: return null
    return try {
        rasterized(picture)
    } finally {
        CGImageRelease(picture)
    }
}

/**
 * [picture] as a sealed Skia bitmap in sRGB (a page in Display P3 is converted — nothing a sheet of music would show):
 * sealed, it is not copied and uploaded again on every record of the layer that draws it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun rasterized(picture: CGImageRef): ImageBitmap? {
    val width = CGImageGetWidth(picture).toInt()
    val height = CGImageGetHeight(picture).toInt()
    if (width <= 0 || height <= 0) return null
    val rowBytes = width * RGBA
    val pixels = ByteArray(rowBytes * height)
    val space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: return null
    val drawn = pixels.usePinned { pinned ->
        // R, G, B, A in this order in memory, alpha premultiplied: RGBA_8888 / PREMUL of Skia
        val context = CGBitmapContextCreate(
            pinned.addressOf(0), width.convert(), height.convert(), BITS_PER_COMPONENT.convert(), rowBytes.convert(), space,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return@usePinned false
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), picture)
        CGContextRelease(context)
        true
    }
    CGColorSpaceRelease(space)
    if (!drawn) return null
    val bitmap = Bitmap()
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB)
    // the bitmap takes a copy of the pixels; the array goes with this frame
    if (!bitmap.installPixels(info, pixels, rowBytes)) {
        bitmap.close()
        return null
    }
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

private const val RGBA = 4
private const val BITS_PER_COMPONENT = 8
