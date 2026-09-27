package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.violinjourney.app.core.ui.components.decodeImageFileAtWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * A sheet for the stand, decoded off the main thread and no larger than it is looked at: a
 * stored page is 2560 px for the sake of zooming (~19 MB decoded in full), and the pager keeps a
 * neighbour or two alive. Asking for more ([wantedWidthPx] grows on zoom — the whole page) decodes
 * again and swaps the picture in place; asking for less keeps what is there.
 *
 * Null while loading, without a path, and for a file that is gone or is not a picture.
 */
@Composable
internal fun rememberStandImage(path: String?, wantedWidthPx: Int): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var loadedWidth by remember(path) { mutableIntStateOf(0) }
    LaunchedEffect(path, wantedWidthPx) {
        if (path == null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { decodeImageFileAtWidth(path, wantedWidthPx, loadedWidth) }
        if (decoded != null) {
            image = decoded
            loadedWidth = decoded.width
        }
    }
    return image
}
