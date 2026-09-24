package com.violinjourney.app.feature.journey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.feature.home.art.HomePicture
import com.violinjourney.app.feature.home.art.homeModeNow
import com.violinjourney.app.feature.journey.art.Postcard
import com.violinjourney.app.feature.journey.art.SceneMode

/** The home as it stands now, for whoever draws the stop «Дом»; null — not known here, the postcard of the handoff is drawn. */
val LocalHomeLook = compositionLocalOf<HomeState?> { null }

/**
 * The postcard of a stop. Home is not a postcard any more (spec 3.24): it is the player's own room
 * with everything bought in it, at the phone's time of day.
 */
@Composable
fun StopPostcard(
    stop: JourneyStop,
    description: String,
    modifier: Modifier = Modifier,
    mode: SceneMode = SceneMode.EVENING,
    inside: Boolean = false,
    seconds: State<Float>? = null,
    /** Home seen from the street: on the journey's own screen home is a stop like the others, a building (handoff 28c2). */
    homeOutside: Boolean = false,
) {
    val home = LocalHomeLook.current
    if (stop.id == JourneyRoute.HOME && home != null && home.loaded) {
        HomePicture(home, outside = homeOutside, mode = homeModeNow(), description = description, modifier = modifier, seconds = seconds)
    } else {
        Postcard(stop, description, modifier, mode, inside, seconds)
    }
}
