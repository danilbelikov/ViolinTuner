package com.example.violintuner.feature.live.venue

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.feature.home.art.HomeComposer
import com.example.violintuner.feature.home.art.homeModeNow
import com.example.violintuner.feature.home.art.rememberHouseArt
import com.example.violintuner.feature.journey.art.PreparedScene
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.drawPrepared
import com.example.violintuner.feature.journey.art.prepare
import com.example.violintuner.feature.journey.art.rememberPausableSceneSeconds
import com.example.violintuner.feature.journey.art.rememberScene
import java.time.LocalDate

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

@Composable
private fun rememberRoomPicture(home: HomeState?): PreparedScene? {
    val state = home?.takeIf { it.loaded }
    val mode = homeModeNow()
    val house = state?.let(HomeRules::house)
    val art = rememberHouseArt(house ?: HomeRules.house(HomeState.EMPTY), mode)
    return remember(art, state, mode) {
        if (art == null || state == null || house == null) return@remember null
        val composed = HomeComposer.compose(art, HomeRules.standing(state, house, false, LocalDate.now()), outside = false, mode = mode, withViolin = false)
        prepare(composed.scene, mode)
    }
}

/**
 * The picture behind Live (spec 3.27, handoff 29a–29h): the place, framed by [VenueFraming]; the light
 * going down with [darkness] — the colours to the dusk, the lamps to a quarter, the life of the scene
 * frozen — and over it the veil under the controls while the light is on, and the light of the zone
 * while it is out. Everything that changes on every frame is read while drawing: the picture itself
 * is its own layer and is drawn again only while the light changes or the scene lives.
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
        Canvas(Modifier.fillMaxSize()) {
            drawLight(kind, landscape, darkness(), glow(), zoneColor(), zoneScale(), ringCenter(), ringDiameter(), surface)
            if (fadeInto != null) {
                val fade = VenueMotion.BottomFade.toPx()
                drawRect(Brush.verticalGradient(listOf(fadeInto.copy(alpha = 0f), fadeInto), startY = size.height - fade, endY = size.height), topLeft = Offset(0f, size.height - fade))
            }
        }
    }
}

@Composable
private fun PlacePicture(picture: PreparedScene, kind: PictureKind, landscape: Boolean, darkness: () -> Float, surface: Color) {
    // the scene lives while the light is on and stops where it is when it goes out (handoff `light.freeze`)
    val seconds = rememberPausableSceneSeconds { darkness() < 1f }
    val surfaceRgb = remember(surface) { floatArrayOf(surface.red, surface.green, surface.blue) }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val d = darkness()
        val framing = VenueFraming.of(kind, size.width, size.height, landscape)
        val dim = d > 0f
        if (dim) drawIntoCanvas { it.saveLayer(size.toRect(), Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix(VenueLook.dimMatrix(d, surfaceRgb))) }) }
        translate(-framing.left * framing.scale, -framing.top * framing.scale) {
            drawPrepared(picture, framing.scale, seconds?.value, VenueLook.lightAlpha(d))
        }
        if (dim) drawIntoCanvas { it.restore() }
    }
}

private fun DrawScope.drawLight(kind: PictureKind, landscape: Boolean, darkness: Float, glow: Float, zone: Color, zoneScale: Float, center: Offset, diameter: Float, surface: Color) {
    if (!center.isSpecified || diameter <= 0f) return
    // the whole dark picture leans towards the colour of the zone: the light of the ring has fallen on the room
    val tint = VenueLook.tintAlpha(glow, darkness, zoneScale)
    if (tint > 0f) drawRect(zone.copy(alpha = tint))
    val band = VenueLook.bandAlpha(darkness)
    if (band > 0f) {
        val fade = VenueMotion.BandFade.toPx()
        val veiled = surface.copy(alpha = band)
        val clear = surface.copy(alpha = 0f)
        if (landscape) {
            // the column of the controls on the right
            val from = size.width * VenueMotion.LANDSCAPE_PANEL - fade
            drawRect(Brush.horizontalGradient(0f to clear, (2 * fade / (size.width - from)).coerceAtMost(1f) to veiled, 1f to veiled, startX = from, endX = size.width), topLeft = Offset(from, 0f))
        } else {
            // the band of the controls above the ring: dark where they stand, softly gone by the top of the ring
            val end = (center.y - diameter / 2).coerceAtLeast(fade)
            drawRect(Brush.verticalGradient(0f to veiled, BAND_HOLD to veiled, 1f to clear, startY = 0f, endY = end), size = Size(size.width, end))
        }
    }
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

/** How far down the band above the ring keeps its full dark before it fades out. */
private const val BAND_HOLD = 0.45f

/** Durations of the place behind Live (handoff `anims`); the light itself is in LiveMotion. */
object VenueMotion {
    /** `picture.swap`: one place gives way to another. */
    const val PICTURE_SWAP_MS = 320

    /** `where.sheet`: the list of places comes up. */
    const val SHEET_MS = 300

    /** The picture fades into the tab bar instead of ending with a knife (handoff `sizes`). */
    val BottomFade = 14.dp

    /** How softly the veil under the controls ends. */
    val BandFade = 48.dp

    /** Lying down, the controls stand right of this share of the width (LiveDimens.LANDSCAPE_RING_PANEL_FRACTION). */
    const val LANDSCAPE_PANEL = 400f / 892f
}
