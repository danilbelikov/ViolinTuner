package com.violinjourney.app.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.feature.journey.art.PreparedScene
import com.violinjourney.app.feature.journey.art.RecentScenes
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.prepare
import kotlinx.coroutines.Dispatchers

/**
 * Everything a composed home depends on: the same key is the same picture. [art] is compared by identity —
 * [HouseArts] hands out one per file. What only the outside shows ([porchCat], [curtains]) is null inside, so one room
 * is one key whatever the cat and the curtains are.
 */
internal data class HomeSceneKey(
    val art: HouseArt,
    val standing: List<HomeItem>,
    val outside: Boolean,
    val mode: SceneMode,
    val ghost: HomeItem?,
    val porchCat: HomeItem?,
    val curtains: HomeItem?,
    val withViolin: Boolean,
)

/**
 * Composed homes made ready to draw, the latest seen kept within [HOME_LAYER_BUDGET] layers: the room and the outside
 * at both times of day, Live's room without the violin, a try-on or two.
 */
private val homeScenes = RecentScenes<HomeSceneKey, PreparedScene>(HOME_LAYER_BUDGET) { it.scene.layers.size }

/** A composed room is some hundreds of layers: this is about ten of them. */
private const val HOME_LAYER_BUDGET = 6_000

/**
 * The home put together ([HomeComposer.compose]) and made ready to draw ([prepare]) off the main thread, as the
 * scenes of the journey are: the plan of what is baked is square in the layers of the room, and a purchase, a tap in
 * «Обставить» or the turn of the day would make the main thread wait for it. A home made before is there in the first
 * frame; while a new one is made — or its file read, [art] null — the picture shown before stays. Null only until the
 * first one is made. Never call [prepare] of a home in composition.
 */
@Composable
internal fun rememberHomeScene(
    art: HouseArt?,
    standing: List<HomeItem>?,
    outside: Boolean,
    mode: SceneMode,
    ghost: HomeItem? = null,
    porchCat: HomeItem? = null,
    curtains: HomeItem? = null,
    withViolin: Boolean = true,
): PreparedScene? = rememberShownHome(art, standing, outside, mode, ghost, porchCat, curtains, withViolin)?.prepared

/**
 * A home made ready to draw and what it was put together of: what is drawn over it — the outline of «Обставить» — follows the picture
 * on the screen, which may still be the one made before ([rememberShownHome]), not the state that asked for the next one.
 */
internal class ShownHome(val key: HomeSceneKey, val prepared: PreparedScene)

/** [rememberHomeScene] with the key of the home it shows: while a new home is made, the one shown before — with its own key. */
@Composable
internal fun rememberShownHome(
    art: HouseArt?,
    standing: List<HomeItem>?,
    outside: Boolean,
    mode: SceneMode,
    ghost: HomeItem? = null,
    porchCat: HomeItem? = null,
    curtains: HomeItem? = null,
    withViolin: Boolean = true,
): ShownHome? {
    val key = if (art == null || standing == null) null else {
        HomeSceneKey(art, standing, outside, mode, ghost?.takeIf { it.outside == outside }, porchCat.takeIf { outside }, curtains.takeIf { outside }, withViolin)
    }
    val initial = remember(key) { key?.let { kept -> homeScenes.peek(kept)?.let { ShownHome(kept, it) } } }
    val made by produceState(initial, key) {
        // nothing to make yet: the picture shown stays
        if (key == null) return@produceState
        value = homeScenes.obtain(key, Dispatchers.Default) {
            val composed = HomeComposer.compose(key.art, key.standing, key.outside, key.mode, key.ghost, key.porchCat, key.curtains, key.withViolin)
            prepare(composed.scene, key.mode)
        }?.let { ShownHome(key, it) }
    }
    return initial ?: made
}

/** A thing as it stands on a shelf, in its card, in «Обставить»: what [ItemThumbs] shows and that scene made ready to draw. */
internal class ThumbPicture(val thumb: ItemThumbScene, val prepared: PreparedScene)

private data class ThumbKey(val art: HouseArt, val itemId: String, val glows: Boolean)

/** The pictures of things, the latest seen kept within [THUMB_LAYER_BUDGET] layers: a thing is a few layers, a sample of the room some tens. */
private val thumbScenes = RecentScenes<ThumbKey, ThumbPicture>(THUMB_LAYER_BUDGET) { it.prepared.scene.layers.size }

/** Every thing of the catalogue on its shelf is about 2 500 layers: all of them fit, with their cards. */
private const val THUMB_LAYER_BUDGET = 6_000

/**
 * The picture of [item] from [art], made off the main thread and kept: the shop does not wait for a hundred of them
 * when it opens. Only the picture of this very thing — never the one shown before in its place: in a row of «Обставить»
 * a purchase moves the things along, and a slot must not show its neighbour while its own is made. Null while it is made.
 */
@Composable
internal fun rememberThumbPicture(item: HomeItem, art: HouseArt?, glows: Boolean): ThumbPicture? {
    val key = art?.let { ThumbKey(it, item.id, glows) }
    val kept = remember(key) { key?.let(thumbScenes::peek) }
    val made by produceState<Pair<ThumbKey, ThumbPicture?>?>(null, key) {
        if (key == null) return@produceState
        value = key to thumbScenes.obtain(key, Dispatchers.Default) {
            ItemThumbs.of(item, key.art, glows)?.let { ThumbPicture(it, prepare(it.scene, SceneMode.EVENING)) }
        }
    }
    return kept ?: made?.takeIf { it.first == key }?.second
}
