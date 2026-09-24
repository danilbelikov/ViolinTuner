package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp

/** Scale and drag of the sheet on the stand; the rules are [StandMath]'s. Read in the draw phase. */
@Stable
class StandZoom {
    var scale by mutableFloatStateOf(StandMath.MIN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val zoomed: Boolean get() = StandMath.isZoomed(scale)

    /** A pinch or a drag: followed at once, without animation. */
    fun transform(zoomChange: Float, pan: Offset, size: IntSize) {
        scale = StandMath.clampScale(scale * zoomChange)
        offset = clamped(offset + pan, size)
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
    suspend fun toggle(tap: Offset, size: IntSize) {
        val fromScale = scale
        val fromOffset = offset
        val toScale = if (zoomed) StandMath.MIN_SCALE else StandMath.DOUBLE_TAP_SCALE
        val toOffset = Offset(
            StandMath.offsetToKeep(tap.x, size.width.toFloat(), toScale),
            StandMath.offsetToKeep(tap.y, size.height.toFloat(), toScale),
        )
        animate(0f, 1f, animationSpec = tween(StandMotion.DOUBLE_TAP_MS, easing = FastOutSlowInEasing)) { fraction, _ ->
            scale = lerp(fromScale, toScale, fraction)
            offset = Offset(lerp(fromOffset.x, toOffset.x, fraction), lerp(fromOffset.y, toOffset.y, fraction))
        }
    }

    private fun clamped(value: Offset, size: IntSize) = Offset(
        StandMath.clampOffset(value.x, scale, size.width.toFloat()),
        StandMath.clampOffset(value.y, scale, size.height.toFloat()),
    )
}

/**
 * One detector for everything the stand's sheet is touched for, because the gestures share a
 * screen and must not steal from each other: a one-finger drag over an unzoomed page is left
 * alone (the pager turns the page, the landscape sheet scrolls), two fingers zoom, one finger
 * over a zoomed page drags it, and a touch that went nowhere is a tap. It listens on the
 * initial pass — before the pager and the scroll inside it — and consumes only what it uses.
 */
fun Modifier.standGestures(zoom: StandZoom, onTap: (position: Offset, area: IntSize) -> Unit): Modifier = pointerInput(zoom) {
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
                zoom.transform(event.calculateZoom(), pan, size)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }
        zoom.settle()
        if (!moved && !transformed && upTime - down.uptimeMillis < longPress) onTap(down.position, size)
    }
}
