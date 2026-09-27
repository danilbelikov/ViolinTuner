package com.violinjourney.app.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.ScenePicture
import com.violinjourney.app.feature.journey.art.prepare
import com.violinjourney.app.feature.journey.art.readSceneText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The home files as read, one per home and time of day: bounded by the files themselves, so without a budget. */
private object HouseArtCache {
    private val lock = PlatformLock()
    private val arts = HashMap<String, HouseArt>()

    fun get(key: String): HouseArt? = lock.withLock { arts[key] }

    fun put(key: String, art: HouseArt) = lock.withLock {
        arts[key] = art
    }
}

/**
 * The art of [house] at [mode], read off the main thread; null while it is read. Only the art asked for: while the
 * other time of day is read, the one of the time before is not given out to be drawn in the new time's colours.
 */
@Composable
fun rememberHouseArt(house: String, mode: SceneMode): HouseArt? {
    val key = "$house.${mode.suffix}"
    val kept = remember(key) { HouseArtCache.get(key) }
    val read by produceState<Pair<String, HouseArt?>?>(null, key) {
        value = key to (HouseArtCache.get(key) ?: withContext(Dispatchers.Default) {
            readSceneText("home/$key.scene")?.let { HouseArt.parse(it).also { art -> HouseArtCache.put(key, art) } }
        })
    }
    return kept ?: read?.takeIf { it.first == key }?.second
}

/**
 * The home as it stands (spec 3.24): the room or the home from outside, with everything bought in
 * its place; [ghost] — a thing being tried on, in its dashed frame that breathes. [mode] — day or
 * evening; null — by the phone's clock ([rememberHomeTime]), which also brings the tree in its season.
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
) {
    val time = rememberHomeTime()
    val shown = mode ?: time.mode
    val art = rememberHouseArt(house, shown)
    val standing = remember(state, house, outside, time.date) { HomeRules.standing(state, house, outside, time.date) }
    val composed = remember(art, standing, state, outside, shown, house, ghost) {
        art?.let { HomeComposer.compose(it, standing, outside, shown, ghost?.takeIf { g -> g.outside == outside }, HomeRules.catOnPorch(state, house), HomeRules.placed(state)["curtain"]) }
    }
    val prepared = remember(composed) { composed?.let { prepare(it.scene, shown) } }
    ScenePicture(prepared, description, modifier, seconds, camera, centred = true)
}
