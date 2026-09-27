package com.violinjourney.app.feature.camera

import androidx.camera.viewfinder.compose.CoordinateTransformer
import androidx.camera.viewfinder.compose.IdentityCoordinateTransformer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A touch of the viewfinder is a share of it; the camera wants a point of its surface, which lies the way the sensor
 * does — on an upright phone a quarter turned against the screen (spec 3.32: focus where the finger is).
 */
class ViewfinderTouchTest {
    /** An upright phone, 1080 × 2400, over a surface of 1920 × 1080 lying on its side: the matrix from the screen to the surface. */
    private val upright = object : CoordinateTransformer {
        // (x, y) on the screen → (y × 1920 / 2400, 1080 − x) on the surface: a quarter turn and the scale of the crop
        override val transformMatrix = Matrix(
            floatArrayOf(
                0f, -1f, 0f, 0f,
                SCALE, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 1080f, 0f, 1f,
            ),
        )
    }

    @Test
    fun `a touch at the top of an upright viewfinder lands at the side of the sensor`() {
        near(Offset(0f, 540f), ViewfinderTouch.onSurface(0.5f, 0f, SCREEN, upright))
        near(Offset(960f, 540f), ViewfinderTouch.onSurface(0.5f, 0.5f, SCREEN, upright))
        near(Offset(1920f, 0f), ViewfinderTouch.onSurface(1f, 1f, SCREEN, upright))
    }

    @Test
    fun `without a turn the shares become pixels of the viewfinder`() {
        near(Offset(270f, 1800f), ViewfinderTouch.onSurface(0.25f, 0.75f, SCREEN, IdentityCoordinateTransformer))
    }

    private fun near(expected: Offset, actual: Offset) =
        assertTrue(abs(expected.x - actual.x) < 0.5f && abs(expected.y - actual.y) < 0.5f, "expected $expected, got $actual")

    private companion object {
        val SCREEN = IntSize(1080, 2400)
        const val SCALE = 1920f / 2400f
    }
}
