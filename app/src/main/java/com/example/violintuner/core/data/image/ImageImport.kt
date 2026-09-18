// Covers the import too, which an annotation on the object does not; the reason is in its KDoc.
@file:SuppressLint("ExifInterface")

package com.example.violintuner.core.data.image

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.scale
import java.io.InputStream

/**
 * Reading a picked or photographed picture the way the app stores pictures: decoded at a
 * fraction of its size (a 50 MP photo must not be held in memory whole), turned upright by
 * its EXIF orientation, scaled down to what is needed. Shared by the avatar (a small square)
 * and the sheet pages (as large as small print needs). The source is opened more than once
 * because a content stream cannot be rewound.
 *
 * The platform ExifInterface is enough here: it reads a stream since API 24 (minSdk is 26) and
 * only the orientation tag of a JPEG is needed. The androidx one lint asks for would be a new
 * dependency, which this project does not take without the owner's consent.
 */
internal object ImageImport {
    /** A picture decoded small enough, still lying as the camera wrote it; [degrees] turns it upright. */
    class Decoded(val bitmap: Bitmap, val degrees: Int)

    /**
     * [dimensionOf] says which side matters (the short one for a crop, the long one for a fit);
     * the picture is sampled down while that side stays at least [target].
     */
    fun decode(open: () -> InputStream?, target: Int, dimensionOf: (width: Int, height: Int) -> Int): Decoded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds the decoder returns null by design; only the stream can be missing.
        (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(dimensionOf(bounds.outWidth, bounds.outHeight), target)
        }
        val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val degrees = open()?.use { rotationDegrees(ExifInterface(it)) } ?: 0
        return Decoded(bitmap, degrees)
    }

    /** The whole picture, upright, its long side at most [maxLongSide]; never enlarged. */
    fun fitted(open: () -> InputStream?, maxLongSide: Int): Bitmap? {
        val decoded = decode(open, maxLongSide) { width, height -> maxOf(width, height) } ?: return null
        val source = decoded.bitmap
        val scale = minOf(1f, maxLongSide.toFloat() / maxOf(source.width, source.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(decoded.degrees.toFloat())
        }
        val fitted = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (fitted !== source) source.recycle()
        return fitted
    }

    /** A smaller copy of an upright picture, long side at most [maxLongSide]; the same bitmap when it is small already. */
    fun scaledDown(source: Bitmap, maxLongSide: Int): Bitmap {
        val longSide = maxOf(source.width, source.height)
        if (longSide <= maxLongSide) return source
        val scale = maxLongSide.toFloat() / longSide
        return source.scale((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1))
    }

    /** The largest power of two that still leaves [dimension] at least [target]. */
    fun sampleSize(dimension: Int, target: Int): Int {
        var sample = 1
        while (dimension / (sample * 2) >= target) sample *= 2
        return sample
    }

    // Mirrored orientations are what selfie cameras of some phones write; neither a face in a
    // 56 dp circle nor a sheet of music is photographed that way, so they count as their rotation.
    private fun rotationDegrees(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
}
