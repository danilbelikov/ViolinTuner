package com.violinjourney.app.feature.home.art

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.feature.home.ArrangeFocus
import com.violinjourney.app.feature.home.ArrangeOutline
import com.violinjourney.app.feature.journey.art.RecentScenes
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.ScenePicture
import com.violinjourney.app.feature.journey.art.readSceneText
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * The home files as read, one per home and time of day: bounded by the files themselves, so without a budget. Each file
 * is read and parsed once and is one instance, however many ask for it at once (pictures that come on the screen in one
 * frame): the keys of the pictures made from it ([HomeSceneKey]) compare it by identity, and a second copy would miss
 * their cache and stay held by it.
 */
internal class HouseArts(private val read: suspend (path: String) -> String?) {
    private val arts = RecentScenes<String, HouseArt>(budget = Int.MAX_VALUE) { 0 }

    /** The art of [key] (`<house>.<mode>`) read before, or null. */
    fun peek(key: String): HouseArt? = arts.peek(key)

    /** The art of [key], read and parsed in [context] by the first who asks, the others waiting for it; null — no such file. */
    suspend fun obtain(key: String, context: CoroutineContext): HouseArt? =
        arts.obtain(key, context) { read("home/$key.scene")?.let(HouseArt::parse) }
}

private val houseArts = HouseArts(::readSceneText)

/**
 * The art of [house] at [mode], read off the main thread; null while it is read. Only the art asked for: while the
 * other time of day is read, the one of the time before is not given out to be drawn in the new time's colours.
 */
@Composable
fun rememberHouseArt(house: String, mode: SceneMode): HouseArt? {
    val key = "$house.${mode.suffix}"
    val kept = remember(key) { houseArts.peek(key) }
    val read by produceState<Pair<String, HouseArt?>?>(null, key) {
        value = key to houseArts.obtain(key, Dispatchers.Default)
    }
    return kept ?: read?.takeIf { it.first == key }?.second
}

/**
 * The home as it stands (spec 3.24): the room or the home from outside, with everything bought in
 * its place; [ghost] — a thing being tried on, in its place and its depth. [mode] — day or
 * evening; null — by the phone's clock ([rememberHomeTime]), which also brings the tree in its season.
 *
 * [outline] — the tile of «Обставить» touched last (spec 3.36.7): the thing it put is outlined, a dashed frame of the accent round its
 * box ([ArrangeOutline]), once the picture shows it there — the frame follows the picture on the screen, not the state, which is a few
 * frames ahead of it while the new room is put together; nothing where nothing of it is seen. It appears and goes at once and does not
 * breathe. Only on a picture seen without a [camera] — «Обставить» has none. [whole] — the picture seen whole by its width, whatever its
 * box (`ScenePicture`): the room of «Обставить» beside its places lying is narrower than covering would leave the sides of the room.
 */
@Composable
fun HomePicture(
    state: HomeState,
    outside: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    mode: SceneMode? = null,
    house: String = HomeRules.house(state),
    ghost: HomeItem? = null,
    seconds: State<Float>? = null,
    camera: (() -> Triple<Float, Float, Float>)? = null,
    outline: ArrangeFocus? = null,
    whole: Boolean = false,
) {
    val time = rememberHomeTime()
    val shownMode = mode ?: time.mode
    val art = rememberHouseArt(house, shownMode)
    val standing = remember(state, house, outside, time.date) { HomeRules.standing(state, house, outside, time.date) }
    // the cat on the porch and our curtains are seen only from outside
    val porchCat = remember(state, house, outside) { if (outside) HomeRules.catOnPorch(state, house) else null }
    val curtains = remember(state, outside) { if (outside) HomeRules.placed(state)["curtain"] else null }
    val shown = rememberShownHome(art, standing, outside, shownMode, ghost, porchCat, curtains)
    // what the picture on the screen was put together of, and its art: the thing outlined and its frame are that picture's
    val shownKey = shown?.key
    val framed = remember(shownKey, outline) { if (outline == null || shownKey == null) null else ArrangeOutline.thingOf(outline, shownKey.standing) }
    val box = framed?.let { shownKey?.art?.items?.get(it.id) }?.takeIf { camera == null }
    val accent = MaterialTheme.colorScheme.primary
    val overlay = remember(box, accent, whole) { box?.let { outlineOf(Rect(it.left, it.top, it.right, it.bottom), accent, whole) } }
    ScenePicture(shown?.prepared, description, modifier, seconds, camera, overlay = overlay, centred = true, whole = whole)
}

/** What [HomePicture] draws over the room for its outline: one dashed frame, the same object at every frame. */
private fun outlineOf(box: Rect, color: Color, whole: Boolean): DrawScope.(seconds: Float?) -> Unit {
    val drawer = OutlineDrawer(box, color, whole)
    return { drawer.draw(this) }
}

// The outline of «Обставить» (spec 5.29 R7): a dashed line of 2 (dashes of 6, gaps of 4) at a corner of 10, 4 round the box of the thing —
// on the screen; in units of the grid each is divided by the scale of the picture.
private val OutlineStroke = 2.dp
private val OutlineDash = 6.dp
private val OutlineGap = 4.dp
private val OutlineCorner = 10.dp
private val OutlinePad = 4.dp

/**
 * Draws the outline of [box] (units of the grid) in [color], over the picture of [HomePicture] — the overlay of [ScenePicture], whose
 * canvas is in units of the grid then. Where the grid stands in the canvas is the picture's own rule without a camera, worked out from
 * the size of the canvas as [ScenePicture] works it out ([ArrangeOutline.gridIn]): covering at the zoom of 1, or seen [whole] by its
 * width; the room in the middle. What is drawn is worked out again only when the size or the density changes: a living room is drawn up
 * to thirty times a second.
 */
private class OutlineDrawer(private val box: Rect, private val color: Color, private val whole: Boolean) {
    private var forSize = Size.Unspecified
    private var forDensity = 0f
    private var frame: Rect? = null
    private var corner = CornerRadius.Zero
    private var line: Stroke? = null

    fun draw(scope: DrawScope) = with(scope) {
        if (size != forSize || density != forDensity) {
            forSize = size
            forDensity = density
            val grid = ArrangeOutline.gridIn(size.width, size.height, whole)
            if (grid == null) {
                frame = null
            } else {
                val k = grid.scale
                val stroke = OutlineStroke.toPx() / k
                // what of the grid the canvas shows, less half the line: a frame cut by the edge keeps all of its line in sight
                frame = ArrangeOutline.frameOf(box, grid.seen(inset = stroke / 2), OutlinePad.toPx() / k)
                corner = CornerRadius(OutlineCorner.toPx() / k)
                line = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(OutlineDash.toPx() / k, OutlineGap.toPx() / k)))
            }
        }
        val shown = frame ?: return@with
        drawRoundRect(color, topLeft = shown.topLeft, size = shown.size, cornerRadius = corner, style = line ?: return@with)
    }
}
