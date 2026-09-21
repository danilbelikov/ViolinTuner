package com.example.violintuner.feature.journey.art

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Where the eye stands before a scene shown on the whole screen: read while drawing, moved by the fingers. */
@Stable
class SceneCameraState {
    var zoom by mutableFloatStateOf(SceneCamera.COVER_ZOOM)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    fun read(): Triple<Float, Float, Float> = Triple(zoom, panX, panY)

    fun move(dragX: Float, dragY: Float, change: Float, width: Float, height: Float) {
        zoom = SceneCamera.zoom(zoom, change, width, height)
        settle(panX + dragX, panY + dragY, width, height)
    }

    fun next(width: Float, height: Float) {
        zoom = SceneCamera.nextZoom(zoom, width, height)
        settle(panX, panY, width, height)
    }

    /** Turns to the point [gridX] of the scene, as far as the picture allows: what is tried on stands at the edge of a room as often as in its middle. */
    fun lookAt(gridX: Float, width: Float, height: Float) {
        val k = SceneCamera.cover(width, height) * zoom
        settle((SceneGrid.WIDTH / 2 - gridX) * k, panY, width, height)
    }

    private fun settle(x: Float, y: Float, width: Float, height: Float) {
        val (cx, cy) = SceneCamera.clamp(x, y, zoom, width, height)
        panX = cx
        panY = cy
    }
}

@Composable
fun rememberSceneCamera(): SceneCameraState = remember { SceneCameraState() }

/** Drag looks around, a pinch comes closer or steps back, a double tap walks round the zooms; [onTap] — a single tap. */
fun Modifier.sceneCamera(camera: SceneCameraState, onTap: () -> Unit = {}): Modifier = this
    .pointerInput(camera) { detectTransformGestures { _, drag, change, _ -> camera.move(drag.x, drag.y, change, size.width.toFloat(), size.height.toFloat()) } }
    .pointerInput(camera) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { camera.next(size.width.toFloat(), size.height.toFloat()) }) }
