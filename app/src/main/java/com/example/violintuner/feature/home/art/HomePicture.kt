package com.example.violintuner.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.ScenePicture
import com.example.violintuner.feature.journey.art.prepare
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
fun homeModeNow(now: LocalTime = LocalTime.now()): SceneMode = if (now.hour in DAY_FROM until DAY_TO) SceneMode.DAY else SceneMode.EVENING

private const val DAY_FROM = 7
private const val DAY_TO = 19
private const val GHOST_FRAME_PERIOD_S = 1.6f
private const val GHOST_FRAME_PAD = 5f

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
    frame: Color = Color.Transparent,
    seconds: State<Float>? = null,
    camera: (() -> Triple<Float, Float, Float>)? = null,
) {
    val art = rememberHouseArt(house, mode)
    val composed = remember(art, state, outside, mode, house, ghost) {
        art?.let { HomeComposer.compose(it, HomeRules.standing(state, house, outside, LocalDate.now()), outside, mode, ghost?.takeIf { g -> g.outside == outside }, HomeRules.catOnPorch(state, house), HomeRules.placed(state)["curtain"]) }
    }
    val prepared = remember(composed) { composed?.let { prepare(it.scene, mode) } }
    val box = composed?.ghost
    ScenePicture(
        prepared, description, modifier, seconds, camera,
        overlay = if (box == null) null else { t ->
            val breath = if (t == null) 1f else 0.55f + 0.45f * (0.5f + 0.5f * sin(2 * PI.toFloat() * t / GHOST_FRAME_PERIOD_S))
            drawRoundRect(
                frame.copy(alpha = breath),
                topLeft = Offset(box.left - GHOST_FRAME_PAD, box.top - GHOST_FRAME_PAD),
                size = Size(box.right - box.left + 2 * GHOST_FRAME_PAD, box.bottom - box.top + 2 * GHOST_FRAME_PAD),
                cornerRadius = CornerRadius(6f),
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        },
    )
}
