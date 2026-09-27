package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Small pictures the app made itself (sheet thumbnails, the profile photo), decoded off the main thread; the last 48
 * are kept, the least recently used goes first: a strip scrolled back and forth must not decode the same thirty files
 * over and over, and a photo seen once is there from the first frame of the next visit. Files are never rewritten —
 * a new picture is a new name — so the path is the whole key. Touched on the main thread only.
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

/**
 * Null while loading, without a path, and for a file that is gone or is not a picture. A new [path] brings its own
 * picture: the one of the old path stays only until the new one is ready.
 */
@Composable
fun rememberSmallFileImage(path: String?): ImageBitmap? {
    val image by produceState(initialValue = path?.let(SmallImages::get), key1 = path) {
        // the state outlives a change of path (the initial value is read once): each path is looked up anew
        value = path?.let { wanted ->
            SmallImages.get(wanted) ?: withContext(Dispatchers.IO) { decodeImageFile(wanted) }?.also { SmallImages.put(wanted, it) }
        }
    }
    return image
}

/** The picture in the file at [path]; null for a file that is gone or is not a picture. Blocking: call off the main thread. */
expect fun decodeImageFile(path: String): ImageBitmap?

/**
 * The picture at [path] decoded about [wantedWidthPx] wide — never wider than the file, and on Android by a
 * power-of-two sample of its pixels, so no narrower than that either; null when it would be no wider than the picture
 * already shown ([loadedWidthPx]), and for a file that is gone or is not a picture. Blocking: call off the main thread.
 */
expect fun decodeImageFileAtWidth(path: String, wantedWidthPx: Int, loadedWidthPx: Int): ImageBitmap?
