package com.violinjourney.app.core.data.profile

import android.graphics.Bitmap
import android.graphics.Matrix
import com.violinjourney.app.core.data.image.ImageImport
import java.io.InputStream

/**
 * Turns a picture of any size into the square the avatar is stored as: upright, cropped around
 * the centre, scaled down to [AVATAR_SIZE_PX]. Reading and turning is [ImageImport]'s.
 */
internal object AvatarImage {
    const val AVATAR_SIZE_PX = 512

    fun squareOf(open: () -> InputStream?): Bitmap? {
        val decoded = ImageImport.decode(open, AVATAR_SIZE_PX) { width, height -> minOf(width, height) } ?: return null
        val source = decoded.bitmap
        val side = minOf(source.width, source.height)
        val scale = minOf(1f, AVATAR_SIZE_PX.toFloat() / side)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(decoded.degrees.toFloat())
        }
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side, matrix, true)
        if (square !== source) source.recycle()
        return square
    }

    /** The largest power of two that still leaves the short side at least [AVATAR_SIZE_PX]. */
    fun sampleSizeFor(width: Int, height: Int): Int = ImageImport.sampleSize(minOf(width, height), AVATAR_SIZE_PX)
}
