package com.violinjourney.app.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.ScenePicture
import com.violinjourney.app.feature.journey.art.prepare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime

private object HouseArtCache {
    private val arts = HashMap<String, HouseArt>()

    @Synchronized
    fun get(key: String): HouseArt? = arts[key]

    @Synchronized
    fun put(key: String, art: HouseArt) {
        arts[key] = art
    }
}

@Composable
fun rememberHouseArt(house: String, mode: SceneMode): HouseArt? {
    val context = LocalContext.current
    val key = "$house.${mode.suffix}"
    val art by produceState(initialValue = HouseArtCache.get(key), key) {
        value = HouseArtCache.get(key) ?: withContext(Dispatchers.Default) {
            runCatching { context.assets.open("home/$key.scene").bufferedReader().use { it.readText() } }.getOrNull()?.let { HouseArt.parse(it).also { art -> HouseArtCache.put(key, art) } }
        }
    }
    return art
}

/** At home the time of day is the phone's: the lamp and the fire are for the evening (handoff 27a). */
fun homeModeNow(now: LocalTime = SystemWallClock.instant().toLocalDateTime(SystemWallClock.zone).time): SceneMode = if (now.hour in DAY_FROM until DAY_TO) SceneMode.DAY else SceneMode.EVENING

private const val DAY_FROM = 7
private const val DAY_TO = 19

/**
 * The home as it stands (spec 3.24): the room or the home from outside, with everything bought in
 * its place; [ghost] — a thing being tried on, in its dashed frame that breathes.
 */
@Composable
fun HomePicture(
    state: HomeState,
    outside: Boolean,
    mode: SceneMode,
    description: String,
    modifier: Modifier = Modifier,
    house: String = HomeRules.house(state),
    ghost: HomeItem? = null,
    seconds: State<Float>? = null,
    camera: (() -> Triple<Float, Float, Float>)? = null,
) {
    val art = rememberHouseArt(house, mode)
    val composed = remember(art, state, outside, mode, house, ghost) {
        art?.let { HomeComposer.compose(it, HomeRules.standing(state, house, outside, SystemWallClock.today()), outside, mode, ghost?.takeIf { g -> g.outside == outside }, HomeRules.catOnPorch(state, house), HomeRules.placed(state)["curtain"]) }
    }
    val prepared = remember(composed) { composed?.let { prepare(it.scene, mode) } }
    ScenePicture(prepared, description, modifier, seconds, camera, centred = true)
}
