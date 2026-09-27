package com.violinjourney.app.feature.live.venue

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.feature.home.art.rememberHomeScene
import com.violinjourney.app.feature.home.art.rememberHomeTime
import com.violinjourney.app.feature.home.art.rememberHouseArt
import com.violinjourney.app.feature.journey.art.PreparedScene
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.SceneMotion
import com.violinjourney.app.feature.journey.art.drawPrepared
import com.violinjourney.app.feature.journey.art.rememberPausableSceneSeconds
import com.violinjourney.app.feature.journey.art.rememberScene

/** The scene a place is drawn from: the room is composed of what stands in it, a hall is read from its file (tools/journey/stage-scenes.js). */
object VenueScenes {
    fun stageOf(stopId: String): String = "${stopId}Stage"

    fun kindOf(venue: Venue): PictureKind = if (venue is Venue.Hall) PictureKind.HALL else PictureKind.ROOM
}

/**
 * The picture of a place, ready to draw; null while it is being read. The room is the home one lives
 * in with everything in its place, at the phone's time of day — without the violin: it is in the
 * player's hands (spec 3.27).
 */
@Composable
fun rememberVenuePicture(venue: Venue?, home: HomeState?): PreparedScene? = when (venue) {
    null -> null
    Venue.Home -> rememberRoomPicture(home)
    is Venue.Hall -> rememberScene(VenueScenes.stageOf(venue.stopId), SceneMode.EVENING)
}

/**
 * The room lived in, composed of what stands in it at the phone's time of day ([rememberHomeTime]): the clock is state,
 * looked at on entry, on coming back to the front and at 07:00, 19:00 and midnight, so a practice that runs through
 * 19:00 with Live open sees the room turn in silence too, and no frame of Live reads the time zone.
 */
@Composable
private fun rememberRoomPicture(home: HomeState?): PreparedScene? {
    val state = home?.takeIf { it.loaded }
    val time = rememberHomeTime()
    val house = remember(state) { state?.let(HomeRules::house) }
    val art = rememberHouseArt(house ?: HomeCatalog.START_HOUSE, time.mode)
    val standing = remember(state, house, time.date) { if (state != null && house != null) HomeRules.standing(state, house, false, time.date) else null }
    // composed off the main thread; the first entry of a process fades the room in, as a hall read from its file does
    return rememberHomeScene(art, standing, outside = false, mode = time.mode, withViolin = false)
}

/**
 * The picture behind Live (spec 3.27, handoff 29a–29h): the place, framed by [VenueFraming]; the light
 * going down with [darkness] — the colours to the dusk, the lamps to a quarter, the life of the scene
 * frozen — and over it the veil round the ring and the curtain over the top while the light is on, and
 * the light of the zone while it is out. Everything that changes on every frame is read while drawing: the picture itself
 * is kept ([KeptPicture]) and is drawn again only while the light changes or the scene lives; standing still on iOS, it is
 * one image the GPU keeps.
 *
 * [ringCenter] and [ringDiameter] are in pixels of this box; [fadeInto] — the colour of the tab bar
 * under the picture, so that the picture does not end with a knife; null when there is no bar.
 */
@Composable
fun VenueBackdrop(
    venue: Venue?,
    picture: PreparedScene?,
    landscape: Boolean,
    darkness: () -> Float,
    glow: () -> Float,
    zoneColor: () -> Color,
    zoneScale: () -> Float,
    ringCenter: () -> Offset,
    ringDiameter: () -> Float,
    fadeInto: Color?,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val kind = venue?.let(VenueScenes::kindOf) ?: PictureKind.ROOM
    // clipped: the light of the zone is a circle wider than the screen, and the status bar above Live keeps its own colour
    Box(modifier.fillMaxSize().clipToBounds().background(surface)) {
        Crossfade(targetState = venue to picture, animationSpec = tween(VenueMotion.PICTURE_SWAP_MS), label = "venuePicture", modifier = Modifier.fillMaxSize()) { (_, shown) ->
            if (shown != null) PlacePicture(shown, kind, landscape, darkness, surface)
        }
        // the light changes on every frame; the fade into the tab bar only with the size, so its brush is kept
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val fade = fadeInto?.let { colour ->
                    val height = VenueMotion.BottomFade.toPx()
                    Brush.verticalGradient(listOf(colour.copy(alpha = 0f), colour), startY = size.height - height, endY = size.height) to height
                }
                onDrawBehind {
                    drawLight(kind, darkness(), glow(), zoneColor(), zoneScale(), ringCenter(), ringDiameter(), surface)
                    fade?.let { (brush, height) -> drawRect(brush, topLeft = Offset(0f, size.height - height)) }
                }
            },
        )
    }
}

@Composable
private fun PlacePicture(picture: PreparedScene, kind: PictureKind, landscape: Boolean, darkness: () -> Float, surface: Color) {
    // a place with nothing alive in it has no time: its clock never starts, and its picture stands still with the light on as well
    val lives = picture.lives
    // the scene lives while the light is on and stops where it is when it goes out (handoff `light.freeze`)
    val seconds = rememberPausableSceneSeconds(SceneMotion.LIVE_FRAME_NANOS) { lives && darkness() < 1f }
    val surfaceRgb = remember(surface) { floatArrayOf(surface.red, surface.green, surface.blue) }
    val kept = rememberKeptPicture()
    val lampClock = remember { LampClock() }
    Canvas(Modifier.fillMaxSize()) {
        val d = darkness()
        val t = if (lives) seconds?.value else null
        val framing = VenueFraming.of(kind, size.width, size.height, landscape)
        val lamps = lampClock.lampsAt(d, t)
        // it will stay as it is: the light fully out (the scene's time stops with it), or on with no time running
        val still = d >= 1f || (t == null && d <= 0f)
        with(kept) {
            // putting the light out is one affine map of every colour (VenueLook), so it is a filter over the picture, not a new drawing of it
            dim(d, surface) { if (d > 0f) ColorFilter.colorMatrix(ColorMatrix(VenueLook.dimMatrix(d, surfaceRgb))) else null }
            lay(KeptPicture.Mark(size, framing, t, lamps), still) {
                translate(-framing.left * framing.scale, -framing.top * framing.scale) {
                    drawPrepared(picture, framing.scale, t, lamps, seen = framing.seen(size))
                }
            }
        }
    }
}

/** What of the scene's grid a box of [box] pixels shows at this framing: the rest is not drawn. */
private fun Framing.seen(box: Size): Rect = Rect(left, top, left + box.width / scale, top + box.height / scale)

private fun DrawScope.drawLight(kind: PictureKind, darkness: Float, glow: Float, zone: Color, zoneScale: Float, center: Offset, diameter: Float, surface: Color) {
    // the whole dark picture leans towards the colour of the zone: the light of the ring has fallen on the room
    val tint = VenueLook.tintAlpha(glow, darkness, zoneScale)
    if (tint > 0f) drawRect(zone.copy(alpha = tint))
    // the curtain over the top quarter: the switcher, the tag and the status line stand on it (handoff venue, second version)
    val curtain = VenueLook.curtainAlpha(darkness)
    if (curtain > 0f) {
        val end = size.height * VenueLook.CURTAIN_HEIGHT
        drawRect(Brush.verticalGradient(listOf(surface.copy(alpha = curtain), surface.copy(alpha = 0f)), startY = 0f, endY = end), size = Size(size.width, end))
    }
    if (!center.isSpecified || diameter <= 0f) return
    val veil = diameter * if (kind == PictureKind.HALL) VenueLook.VEIL_HALL else VenueLook.VEIL_ROOM
    val (veilCentre, veilMiddle) = VenueLook.veilAlphas(darkness)
    if (veilCentre > 0f) {
        drawCircle(
            Brush.radialGradient(0f to surface.copy(alpha = veilCentre), VenueLook.VEIL_MID_STOP to surface.copy(alpha = veilMiddle), 1f to surface.copy(alpha = 0f), center = center, radius = veil),
            radius = veil, center = center,
        )
    }
    val (lightCentre, lightMiddle) = VenueLook.zoneLightAlphas(glow, darkness, zoneScale)
    if (lightCentre > 0f) {
        val reach = veil * VenueLook.ZONE_LIGHT_REACH
        drawCircle(
            Brush.radialGradient(0f to zone.copy(alpha = lightCentre), VenueLook.ZONE_LIGHT_MID_STOP to zone.copy(alpha = lightMiddle), 1f to zone.copy(alpha = 0f), center = center, radius = reach),
            radius = reach, center = center, blendMode = BlendMode.Screen,
        )
    }
}

/** Durations of the place behind Live (handoff `anims`); the light itself is in LiveMotion. */
object VenueMotion {
    /** `picture.swap`: one place gives way to another. */
    const val PICTURE_SWAP_MS = 320

    /** The picture fades into the tab bar instead of ending with a knife (handoff `sizes`). */
    val BottomFade = 14.dp

}
