package com.violinjourney.app.feature.journey.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.violinjourney.app.feature.live.venue.VenueLook
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.use

/**
 * What the still picture behind Live on iOS stands on (docs/plan-performance.md, «На iOS»): its image shares its
 * own pixels with every Skia image made of it — so the GPU keeps one texture of it — and the dimming laid over the
 * image is the dimming the layer laid over the drawn picture.
 */
class SkiaStillImagesTest {
    private val box = IntSize(64, 48)
    private val density = Density(1f)
    private val surface = Color(0xFF131318)

    // a gradient, a path seen through, a plain shape — what a scene is made of
    private val content: DrawScope.() -> Unit = {
        drawRect(Brush.radialGradient(listOf(Color(0xFFE2B74E), Color(0x008E2F3F)), center = Offset(20f, 20f), radius = 40f))
        val path = Path().apply {
            moveTo(5f, 40f)
            lineTo(40f, 5f)
            lineTo(60f, 44f)
            close()
        }
        drawPath(path, Color(0xFF4E8E57), alpha = 0.6f)
        drawRect(Color(0xFFF3EEE2), topLeft = Offset(44f, 30f), size = Size(12f, 10f))
    }

    private fun imageOf(picture: DrawScope.() -> Unit): ImageBitmap {
        val recorded = recordPicture(box, density, LayoutDirection.Ltr, picture)
        try {
            return assertNotNull(rasterize(recorded, box))
        } finally {
            recorded.close()
        }
    }

    private fun drawn(block: DrawScope.() -> Unit): IntArray {
        val target = ImageBitmap(box.width, box.height)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(target), Size(box.width.toFloat(), box.height.toFloat())) {
            drawRect(surface)
            block()
        }
        return IntArray(box.width * box.height).also { target.readPixels(it) }
    }

    private fun dimming(darkness: Float): ColorFilter? =
        if (darkness > 0f) ColorFilter.colorMatrix(ColorMatrix(VenueLook.dimMatrix(darkness, floatArrayOf(surface.red, surface.green, surface.blue)))) else null

    @OptIn(ExperimentalForeignApi::class) // the address of the pixels
    @Test
    fun `a still image is laid down from its own pixels and not a copy`() {
        val image = imageOf(content)
        val bitmap = image.asSkiaBitmap()
        assertTrue(bitmap.isImmutable, "sealed")
        val own = assertNotNull(bitmap.peekPixels()).use { it.addr }
        repeat(2) {
            Image.makeFromBitmap(bitmap).use { skia -> assertEquals(own, assertNotNull(skia.peekPixels()).use { it.addr }, "every image of it shares its pixels") }
        }
        // the trap: a bitmap left open is copied every time an image is made of it — a full screen copied and uploaded again on every record
        val open = ImageBitmap(box.width, box.height).asSkiaBitmap()
        val openPixels = assertNotNull(open.peekPixels()).use { it.addr }
        Image.makeFromBitmap(open).use { skia -> assertNotEquals(openPixels, assertNotNull(skia.peekPixels()).use { it.addr }) }
        SkiaStillImages.free(image)
    }

    @Test
    fun `the dimming laid over the image matches the dimming of a layer`() {
        val image = imageOf(content)
        for (darkness in listOf(0f, 0.5f, 1f)) {
            val filter = dimming(darkness)
            val throughLayer = drawn {
                drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { colorFilter = filter }) }
                content()
                drawIntoCanvas { it.restore() }
            }
            val throughImage = drawn { drawImage(image, colorFilter = filter) }
            val worst = throughLayer.indices.maxOf { at -> channelDifference(throughLayer[at], throughImage[at]) }
            assertTrue(worst <= 1, "darkness $darkness: the largest difference of a channel is $worst")
        }
        SkiaStillImages.free(image)
    }

    @Test
    fun `a freed image lets go of its pixels`() {
        val image = imageOf(content)
        SkiaStillImages.free(image)
        assertTrue(image.asSkiaBitmap().isClosed)
    }

    @Test
    fun `an empty box has no image`() {
        val recorded = recordPicture(IntSize(0, 10), density, LayoutDirection.Ltr, content)
        assertNull(rasterize(recorded, IntSize(0, 10)))
        recorded.close()
    }

    private fun channelDifference(a: Int, b: Int): Int = (0 until 4).maxOf { shift -> abs((a shr (shift * 8) and 0xFF) - (b shr (shift * 8) and 0xFF)) }
}
