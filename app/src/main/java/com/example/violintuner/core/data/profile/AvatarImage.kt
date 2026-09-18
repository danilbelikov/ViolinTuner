// Covers the import too, which an annotation on the object does not; the reason is in its KDoc.
@file:SuppressLint("ExifInterface")

package com.example.violintuner.core.data.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.InputStream

/**
 * Turns a picture of any size into the square the avatar is stored as: turned upright by its
 * EXIF orientation, cropped around the centre, scaled down to [AVATAR_SIZE_PX]. The source is
 * opened more than once because a content stream cannot be rewound.
 *
 * The platform ExifInterface is enough here: it reads a stream since API 24 (minSdk is 26) and
 * only the orientation tag of a JPEG is needed. The androidx one lint asks for would be a new
 * dependency, which this project does not take without the owner's consent.
 */
internal object AvatarImage {
    const val AVATAR_SIZE_PX = 512

    fun squareOf(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns null by design; only the stream can be missing.
        (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decoded at a fraction of its size: a 50 MP photo must not be held in memory whole.
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val degrees = open()?.use { rotationDegrees(ExifInterface(it)) } ?: 0

        val side = minOf(decoded.width, decoded.height)
        val scale = minOf(1f, AVATAR_SIZE_PX.toFloat() / side)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(degrees.toFloat())
        }
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
        if (square !== decoded) decoded.recycle()
        return square
    }

    /** The largest power of two that still leaves the short side at least [AVATAR_SIZE_PX]. */
    fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= AVATAR_SIZE_PX) sample *= 2
        return sample
    }

    // Mirrored orientations are what selfie cameras of some phones write; a mirrored face in a
    // 56 dp circle is not worth the extra cases, so they are treated as their rotation alone.
    private fun rotationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
}
