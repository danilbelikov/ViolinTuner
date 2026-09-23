package com.violinjourney.app.feature.live.venue

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.feature.home.art.HomeComposer
import com.violinjourney.app.feature.home.art.homeModeNow
import com.violinjourney.app.feature.home.art.rememberHouseArt
import com.violinjourney.app.feature.journey.art.PreparedScene
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.SceneMotion
import com.violinjourney.app.feature.journey.art.drawPrepared
import com.violinjourney.app.feature.journey.art.prepare
import com.violinjourney.app.feature.journey.art.rememberPausableSceneSeconds
import com.violinjourney.app.feature.journey.art.rememberScene
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
 * frozen — and over it the veil round the ring and the curtain over the top while the light is on, and
 * the light of the zone while it is out. Everything that changes on every frame is read while drawing: the picture itself
 * is its own layer ([KeptPicture]) and is drawn again only while the light changes or the scene lives.
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
            drawLight(kind, darkness(), glow(), zoneColor(), zoneScale(), ringCenter(), ringDiameter(), surface)
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
    val seconds = rememberPausableSceneSeconds(SceneMotion.LIVE_FRAME_NANOS) { darkness() < 1f }
    val surfaceRgb = remember(surface) { floatArrayOf(surface.red, surface.green, surface.blue) }
    val kept = rememberKeptPicture()
    Canvas(Modifier.fillMaxSize()) {
        val d = darkness()
        val framing = VenueFraming.of(kind, size.width, size.height, landscape)
        val lamps = VenueLook.lightAlpha(d)
        val mark = KeptPicture.Mark(size, framing, seconds?.value, lamps)
        if (kept.mark != mark) {
            kept.mark = mark
            kept.layer.record(size = size.toIntSize()) {
                translate(-framing.left * framing.scale, -framing.top * framing.scale) {
                    drawPrepared(picture, framing.scale, seconds?.value, lamps, seen = framing.seen(size))
                }
            }
        }
        // putting the light out is one affine map of every colour (VenueLook), so it is the layer's own and the picture is not drawn again for it
        if (kept.darkness != d || kept.surface != surface) {
            kept.darkness = d
            kept.surface = surface
            kept.layer.colorFilter = if (d > 0f) ColorFilter.colorMatrix(ColorMatrix(VenueLook.dimMatrix(d, surfaceRgb))) else null
        }
        drawLayer(kept.layer)
    }
}

/** What of the scene's grid a box of [box] pixels shows at this framing: the rest is not drawn. */
private fun Framing.seen(box: Size): Rect = Rect(left, top, left + box.width / scale, top + box.height / scale)

/**
 * The picture of the place as the GPU keeps it (docs/plan-performance.md): it is drawn again only
 * when what it is drawn from has changed — the box, the framing, the second of a living scene, the
 * lamps going out — and a frame otherwise only lays it down. While the violin sounds the picture
 * stands still and the ring above it costs a frame nothing.
 */
private class KeptPicture(val layer: GraphicsLayer) {
    /** Everything the drawn picture depends on. */
    data class Mark(val box: Size, val framing: Framing, val seconds: Float?, val lamps: Float)

    var mark: Mark? = null
    var darkness = Float.NaN
    var surface: Color? = null
}

@Composable
private fun rememberKeptPicture(): KeptPicture {
    val context = LocalGraphicsContext.current
    val kept = remember(context) { KeptPicture(context.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }) }
    DisposableEffect(kept) { onDispose { context.releaseGraphicsLayer(kept.layer) } }
    return kept
}

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

    /** `where.sheet`: the list of places comes up. */
    const val SHEET_MS = 300

    /** The picture fades into the tab bar instead of ending with a knife (handoff `sizes`). */
    val BottomFade = 14.dp

}
