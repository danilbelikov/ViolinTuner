package com.violinjourney.app.core.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small pictures the app made itself (sheet thumbnails), decoded off the main thread and kept
 * for as long as memory is not asked back: a strip scrolled back and forth must not decode the
 * same thirty files over and over. Files are never rewritten — a new picture is a new name —
 * so the path is the whole key.
 */
private object SmallImages {
    private const val MAX_ENTRIES = 48
    val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)
}

/** Null while loading, without a path, and for a file that is gone or is not a picture. */
@Composable
fun rememberSmallFileImage(path: String?): ImageBitmap? {
    val image by produceState(initialValue = path?.let(SmallImages.cache::get), key1 = path) {
        if (path == null) {
            value = null
        } else if (value == null) {
            value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                ?.also { SmallImages.cache.put(path, it) }
        }
    }
    return image
}
