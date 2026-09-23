package com.violinjourney.app.feature.repertoire.stand

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A sheet for the stand, decoded off the main thread and no larger than it is looked at: a
 * stored page is 2560 px for the sake of zooming (~19 MB decoded), and the pager keeps a
 * neighbour or two alive. Asking for more ([wantedWidthPx] grows on zoom) decodes again and
 * swaps the picture in place; asking for less keeps what is there.
 *
 * Null while loading, without a path, and for a file that is gone or is not a picture.
 */
@Composable
internal fun rememberStandImage(path: String?, wantedWidthPx: Int): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var loadedSample by remember(path) { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(path, wantedWidthPx) {
        if (path == null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Returns null by design with inJustDecodeBounds; the answer is in the options.
            BitmapFactory.decodeFile(path, bounds)
            val sample = StandMath.sampleSize(bounds.outWidth, wantedWidthPx)
            if (sample >= loadedSample) {
                null
            } else {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { it.asImageBitmap() to sample }
            }
        }
        if (decoded != null) {
            image = decoded.first
            loadedSample = decoded.second
        }
    }
    return image
}
