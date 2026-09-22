package com.example.violintuner.feature.home.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.drawPrepared
import com.example.violintuner.feature.journey.art.prepare
import com.example.violintuner.feature.journey.art.rememberSceneSeconds
import com.example.violintuner.feature.journey.art.watchedBy

/** How much of its picture a thing may take across and up (spec 5.22). */
private const val FIT_WIDTH = 0.8f
private const val FIT_HEIGHT = 0.9f

/** Units of the grid a dp at most on a shelf: a passport is not blown up to a book. */
const val SHELF_MAX_SCALE = 3.2f

/** The same in the card of a thing, where the thing is the whole picture. */
const val CARD_MAX_SCALE = 5f

private val SAMPLE_CORNER = 8.dp

/**
 * A thing as it stands on a shelf of the shop (spec 3.29): the very layers it has in the room, in the
 * evening, fitted into the box and standing on its bottom edge — the board of the shelf; a wall, a
 * floor or a part of the window as a sample of the room with it ([ItemThumbs]). [alive] — the card of
 * a thing: it moves as it will in the room; on a shelf things stand still.
 */
@Composable
fun ItemThumb(item: HomeItem, modifier: Modifier = Modifier, alive: Boolean = false, maxScale: Float = SHELF_MAX_SCALE) {
    val art = rememberHouseArt(ItemThumbs.houseOf(item), SceneMode.EVENING)
    val thumb = remember(art, item.id, alive) { art?.let { ItemThumbs.of(item, it, glows = alive) } }
    val prepared = remember(thumb) { thumb?.let { prepare(it.scene, SceneMode.EVENING) } }
    val seconds = rememberSceneSeconds(enabled = alive)
    Canvas(modifier.fillMaxSize().watchedBy(seconds)) {
        val shown = thumb ?: return@Canvas
        val scene = prepared ?: return@Canvas
        val box = shown.box
        val at = seconds?.value
        if (shown.sample) {
            // a sample card of the room, as wide as the picture and standing on the board
            val k = minOf(size.width / box.width, size.height / box.height)
            val left = (size.width - box.width * k) / 2
            val top = size.height - box.height * k
            val card = Path().apply { addRoundRect(RoundRect(left, top, left + box.width * k, size.height, CornerRadius(SAMPLE_CORNER.toPx()))) }
            clipPath(card) { translate(left - box.left * k, top - box.top * k) { drawPrepared(scene, k, at) } }
        } else {
            val k = minOf(size.width * FIT_WIDTH / box.width, size.height * FIT_HEIGHT / box.height, maxScale * density)
            // within the picture: what goes past it — the chandelier's rod, a halo — is cut by its edge
            clipRect { translate((size.width - box.width * k) / 2 - box.left * k, size.height - box.bottom * k) { drawPrepared(scene, k, at) } }
        }
    }
}
