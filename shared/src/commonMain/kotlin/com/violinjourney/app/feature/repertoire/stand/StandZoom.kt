package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp

/**
 * Scale and drag of the sheet on the stand; the rules are [StandMath]'s. [scale] and [offset] are read in the draw
 * phase; [zoomed] is read in composition and changes only when the scale crosses the threshold, not on every frame of
 * a pinch.
 *
 * The scaled layer is the part of the stand the sheet is shown in — the area of gestures without the stand's
 * [margins](standGestures) — and it is centred on that area: the double tap, the pinch and the clamp count on it.
 */
@Stable
class StandZoom {
    var scale by mutableFloatStateOf(StandMath.MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val zoomed: Boolean by derivedStateOf { StandMath.isZoomed(scale) }

    /**
     * A pinch or a drag: followed at once, without animation. The point under the fingers' previous [centroid] (in the
     * area of gestures, [area] large) stays under them while the scale changes; a one-finger drag only moves the sheet.
     */
    fun transform(zoomChange: Float, pan: Offset, centroid: Offset, area: IntSize, margins: IntSize) {
        val next = StandMath.clampScale(scale * zoomChange)
        val applied = next / scale
        val focus = if (centroid.isSpecified) centroid - Offset(area.width / 2f, area.height / 2f) else Offset.Zero
        scale = next
        offset = clamped(
            Offset(
                StandMath.offsetAfterPinch(offset.x, focus.x, applied, pan.x),
                StandMath.offsetAfterPinch(offset.y, focus.y, applied, pan.y),
            ),
            layerOf(area, margins),
        )
    }

    /** The fingers are gone: a pinch that ended next to 1× is 1×. */
    fun settle() {
        if (!zoomed) reset()
    }

    fun reset() {
        scale = StandMath.MIN_SCALE
        offset = Offset.Zero
    }

    /** Double tap: 1× ↔ 2×, towards the tapped point. */
    suspend fun toggle(tap: Offset, area: IntSize, margins: IntSize) {
        val fromScale = scale
        val fromOffset = offset
        val toScale = if (zoomed) StandMath.MIN_SCALE else StandMath.DOUBLE_TAP_SCALE
        val layer = layerOf(area, margins)
        val toOffset = Offset(
            StandMath.offsetToKeep(tap.x, area.width.toFloat(), layer.width.toFloat(), toScale),
            StandMath.offsetToKeep(tap.y, area.height.toFloat(), layer.height.toFloat(), toScale),
        )
        animate(0f, 1f, animationSpec = tween(StandMotion.DOUBLE_TAP_MS, easing = FastOutSlowInEasing)) { fraction, _ ->
            scale = lerp(fromScale, toScale, fraction)
            offset = Offset(lerp(fromOffset.x, toOffset.x, fraction), lerp(fromOffset.y, toOffset.y, fraction))
        }
    }

    private fun clamped(value: Offset, layer: IntSize) = Offset(
        StandMath.clampOffset(value.x, scale, layer.width.toFloat()),
        StandMath.clampOffset(value.y, scale, layer.height.toFloat()),
    )

    private fun layerOf(area: IntSize, margins: IntSize) =
        IntSize((area.width - margins.width).coerceAtLeast(0), (area.height - margins.height).coerceAtLeast(0))
}

/**
 * One detector for everything the stand's sheet is touched for, because the gestures share a
 * screen and must not steal from each other: a one-finger drag over an unzoomed page is left
 * alone (the pager turns the page, the sheet scrolls), two fingers zoom, one finger
 * over a zoomed page drags it, and a touch that went nowhere is a tap. It listens on the
 * initial pass — before the pager and the scroll inside it — and consumes only what it uses.
 *
 * [margins] are what the stand leaves around the scaled layer of the sheet, both sides of each axis together: the
 * layer is the area of gestures without them, centred in it.
 */
fun Modifier.standGestures(zoom: StandZoom, margins: IntSize, onTap: (position: Offset, area: IntSize) -> Unit): Modifier = pointerInput(zoom, margins) {
    val slop = viewConfiguration.touchSlop
    val longPress = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var travelled = Offset.Zero
        var moved = false
        var transformed = false
        var upTime = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            upTime = event.changes.first().uptimeMillis
            val fingers = event.changes.count { it.pressed }
            if (fingers == 0) break
            val pan = event.calculatePan()
            travelled += pan
            if (travelled.getDistance() > slop) moved = true
            if (fingers > 1 || (zoom.zoomed && moved)) {
                transformed = true
                // the centroid before this event: the point the fingers held is the point to keep under them
                zoom.transform(event.calculateZoom(), pan, event.calculateCentroid(useCurrent = false), size, margins)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }
        zoom.settle()
        if (!moved && !transformed && upTime - down.uptimeMillis < longPress) onTap(down.position, size)
    }
}
