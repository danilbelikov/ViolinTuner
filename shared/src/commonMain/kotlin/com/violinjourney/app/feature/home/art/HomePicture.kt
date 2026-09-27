package com.violinjourney.app.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
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
    // the cat on the porch and our curtains are seen only from outside
    val porchCat = remember(state, house, outside) { if (outside) HomeRules.catOnPorch(state, house) else null }
    val curtains = remember(state, outside) { if (outside) HomeRules.placed(state)["curtain"] else null }
    val prepared = rememberHomeScene(art, standing, outside, shown, ghost, porchCat, curtains)
    ScenePicture(prepared, description, modifier, seconds, camera, centred = true)
}
