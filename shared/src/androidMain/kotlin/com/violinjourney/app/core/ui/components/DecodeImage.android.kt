package com.violinjourney.app.core.ui.components

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.violinjourney.app.feature.repertoire.stand.StandMath

actual fun decodeImageFile(path: String): ImageBitmap? = BitmapFactory.decodeFile(path)?.asImageBitmap()

actual fun decodeImageFileAtWidth(path: String, wantedWidthPx: Int, loadedWidthPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Returns null by design with inJustDecodeBounds; the answer is in the options.
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || StandMath.sampledWidth(bounds.outWidth, wantedWidthPx) <= loadedWidthPx) return null
    val sample = StandMath.sampleSize(bounds.outWidth, wantedWidthPx)
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
