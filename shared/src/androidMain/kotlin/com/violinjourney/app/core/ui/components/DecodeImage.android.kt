package com.violinjourney.app.core.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.violinjourney.app.feature.repertoire.stand.StandMath

actual fun decodeImageFile(path: String): ImageBitmap? = BitmapFactory.decodeFile(path)?.asImageBitmap()

actual fun decodeImageFileSampled(path: String, wantedWidthPx: Int, loadedSample: Int): Pair<ImageBitmap, Int>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Returns null by design with inJustDecodeBounds; the answer is in the options.
    BitmapFactory.decodeFile(path, bounds)
    val sample = StandMath.sampleSize(bounds.outWidth, wantedWidthPx)
    if (sample >= loadedSample) return null
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { it.asImageBitmap() to sample }
}
