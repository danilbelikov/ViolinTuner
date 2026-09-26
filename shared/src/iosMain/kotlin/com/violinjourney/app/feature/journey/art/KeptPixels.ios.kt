package com.violinjourney.app.feature.journey.art

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toIntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Picture
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect

// no alpha, no filter — no saveLayer: skiko keeps the drawing of a layer, and Offscreen would only add a full-size pass to every frame
internal actual val keptLayerStrategy: CompositingStrategy = CompositingStrategy.Auto

internal actual val stillImages: StillImages? = SkiaStillImages

/**
 * A still picture as one image the GPU keeps. The image is sealed ([Bitmap.setImmutable]): skiko
 * turns a bitmap into a Skia image every time the node that draws it records, and a sealed one shares
 * its pixels, whose texture Ganesh keeps by their id; a mutable one would be copied and uploaded again —
 * 14 MB for a full screen — on every such record.
 */
internal object SkiaStillImages : StillImages {
    override fun DrawScope.record(layer: GraphicsLayer, picture: DrawScope.() -> Unit): StillMaking {
        val box = size.toIntSize()
        val recorded = recordPicture(box, this, layoutDirection, picture)
        // the layer's own recording holds a reference to the picture: the making may let its own go whenever it is done
        layer.record(box) { drawIntoCanvas { it.nativeCanvas.drawPicture(recorded) } }
        return SkiaMaking(recorded, box)
    }

    override fun free(image: ImageBitmap) = image.asSkiaBitmap().close()
}

private class SkiaMaking(private val picture: Picture, private val box: IntSize) : StillMaking {
    override fun make(): ImageBitmap? = rasterize(picture, box)

    override fun close() = picture.close()
}

/** [picture] drawn once for a box of [box] pixels, as a Skia picture that may be played on any thread. */
internal fun recordPicture(box: IntSize, density: Density, layoutDirection: LayoutDirection, picture: DrawScope.() -> Unit): Picture {
    val recorder = PictureRecorder()
    try {
        val canvas = recorder.beginRecording(Rect.makeWH(box.width.toFloat(), box.height.toFloat()))
        CanvasDrawScope().draw(density, layoutDirection, canvas.asComposeCanvas(), Size(box.width.toFloat(), box.height.toFloat()), picture)
        return recorder.finishRecordingAsPicture()
    } finally {
        recorder.close()
    }
}

/**
 * The pixels of [picture] in a box of [box], sealed; null for an empty box or when there is no memory
 * for them. On the CPU, so off the main thread: playing a recorded picture is safe on any thread.
 */
internal fun rasterize(picture: Picture, box: IntSize): ImageBitmap? {
    if (box.width <= 0 || box.height <= 0) return null
    val bitmap = Bitmap()
    if (!bitmap.allocPixelsFlags(ImageInfo.makeN32Premul(box.width, box.height, ColorSpace.sRGB), zeroPixels = true)) {
        bitmap.close()
        return null
    }
    // an open canvas holds the pixels: it is closed before the image is handed out, so freeing the image lets them go
    val canvas = Canvas(bitmap)
    try {
        canvas.drawPicture(picture)
    } finally {
        canvas.close()
    }
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
