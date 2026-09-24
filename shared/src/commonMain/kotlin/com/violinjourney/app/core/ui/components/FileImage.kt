package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Small pictures the app made itself (sheet thumbnails), decoded off the main thread and kept
 * for as long as memory is not asked back: a strip scrolled back and forth must not decode the
 * same thirty files over and over. Files are never rewritten — a new picture is a new name —
 * so the path is the whole key.
 */
private object SmallImages {
    private const val MAX_ENTRIES = 48

    // least recently used first: a LinkedHashMap kept in the order of use
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(path: String): ImageBitmap? = cache.remove(path)?.also { cache[path] = it }

    fun put(path: String, image: ImageBitmap) {
        cache.remove(path)
        cache[path] = image
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
    }
}

/** Null while loading, without a path, and for a file that is gone or is not a picture. */
@Composable
fun rememberSmallFileImage(path: String?): ImageBitmap? {
    val image by produceState(initialValue = path?.let(SmallImages::get), key1 = path) {
        if (path == null) {
            value = null
        } else if (value == null) {
            value = withContext(Dispatchers.IO) { decodeImageFile(path) }?.also { SmallImages.put(path, it) }
        }
    }
    return image
}

/** The picture in the file at [path]; null for a file that is gone or is not a picture. Blocking: call off the main thread. */
expect fun decodeImageFile(path: String): ImageBitmap?
