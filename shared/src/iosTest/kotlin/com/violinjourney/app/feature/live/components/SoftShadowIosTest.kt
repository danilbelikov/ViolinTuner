package com.violinjourney.app.feature.live.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.violinjourney.app.core.ui.components.seal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The soft shadows of Live on iOS (plan Р3, spec 5.29 R6): baked once and laid down on every frame of the window, so the picture is
 * sealed — skiko then shares its pixels instead of copying them at every record of the node (docs/plan-performance.md, «На iOS») — and
 * what is laid down is a shadow: dark under the shape, set down by its drop, gone far from it, and nothing at an alpha of 0.
 */
class SoftShadowIosTest {
    private val box = 96

    private fun alphaAt(pixels: IntArray, x: Int, y: Int): Int = (pixels[y * box + x] ushr 24) and 0xFF

    @Test
    fun `a sealed picture is immutable for skiko`() {
        val image = ImageBitmap(4, 4).seal()
        assertTrue(image.asSkiaBitmap().isImmutable, "a sealed picture shares its pixels")
    }

    @Test
    fun `a baked shadow is dark under the shape set down by its drop and gone far from it`() {
        val target = ImageBitmap(box, box)
        val shape = Size(40f, 20f)
        val shadow = SoftShadow.bake(shape, corner = 10f, sigma = SIGMA.toFloat())
        val drop = 6f
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target), shape) {
            translate(left = 28f, top = 30f) { shadow.draw(this, drop, alpha = 1f) }
        }
        val pixels = IntArray(box * box).also { target.readPixels(it) }
        // under the middle of the shape, set down by the drop: dark
        val under = alphaAt(pixels, 48, 40 + drop.toInt())
        assertTrue(under > 200, "under the shape: $under")
        // a sigma past its lower edge (56) the blur is on its way out — some shadow, less than under the shape: a shadow that is not
        // blurred has none there (a hard edge), and none of the tests below would see it
        val pastTheEdge = alphaAt(pixels, 48, 30 + 20 + drop.toInt() + SIGMA)
        assertTrue(pastTheEdge in 1 until under, "a sigma past the edge: $pastTheEdge, under the shape: $under")
        // further on it has faded; far from the shape nothing
        assertTrue(alphaAt(pixels, 48, 30 + 20 + drop.toInt() + 10) < pastTheEdge, "the blur fades below the shape")
        assertEquals(0, alphaAt(pixels, 2, 2), "nothing far from the shape")
        // an alpha of nought lays nothing down
        val empty = ImageBitmap(box, box)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(empty), shape) {
            translate(left = 28f, top = 30f) { shadow.draw(this, drop, alpha = 0f) }
        }
        val none = IntArray(box * box).also { empty.readPixels(it) }
        assertTrue(none.all { it == 0 }, "no shadow at an alpha of 0")
    }

    private companion object {
        /** The blur of the shadow of the test, in pixels at a density of 1. */
        const val SIGMA = 4
    }
}
