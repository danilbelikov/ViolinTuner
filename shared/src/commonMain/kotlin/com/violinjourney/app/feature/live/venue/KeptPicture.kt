package com.violinjourney.app.feature.live.venue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.toIntSize
import com.violinjourney.app.feature.journey.art.StillImages
import com.violinjourney.app.feature.journey.art.keptLayerStrategy
import com.violinjourney.app.feature.journey.art.sceneNoBake
import com.violinjourney.app.feature.journey.art.stillImages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The picture of the place, kept between frames (docs/plan-performance.md): it is drawn again only
 * when what it is drawn from has changed — the box, the framing, the second of a living scene, the
 * lamps going out ([Mark]) — and a frame otherwise only lays it down; putting the light out is a colour
 * filter over it ([dim]), not a new drawing.
 *
 * On Android the layer is a hardware layer: the GPU keeps its pixels, and while the violin sounds the
 * ring above the picture costs a frame nothing. On iOS a layer keeps the drawing, not the pixels, so a
 * picture that stands still — the light fully out, or the light on with nothing moving («убрать
 * анимации», a place with nothing alive) — becomes one image ([images]): recorded once in its first
 * still frame, laid down by the layer until its pixels are made off the main thread, then laid down as
 * that image with the same filter. The image is made in the first still frame, not after one: skiko
 * does not draw a Canvas again unless something it reads changes, so there may be no second frame.
 */
internal class KeptPicture(private val layer: GraphicsLayer, private val images: StillImages?, private val scope: CoroutineScope) {
    /** Everything the drawn picture depends on. */
    data class Mark(val box: Size, val framing: Framing, val seconds: Float?, val lamps: Float)

    private val image = images?.let { KeptImage<Mark, ImageBitmap>(it::free) }
    private var layerMark: Mark? = null
    private var darkness = Float.NaN
    private var surface: Color? = null
    private var filter: ColorFilter? = null

    /** The filter that puts the light out ([filterOf]), made again only when [darkness] or [surface] change. */
    fun dim(darkness: Float, surface: Color, filterOf: () -> ColorFilter?) {
        if (this.darkness == darkness && this.surface == surface) return
        this.darkness = darkness
        this.surface = surface
        filter = filterOf()
        layer.colorFilter = filter
    }

    /** Lays the picture of [mark] down, drawing it with [picture] only if it is not kept already; [still] — it will stay as it is. */
    fun DrawScope.lay(mark: Mark, still: Boolean, picture: DrawScope.() -> Unit) {
        val shown = image?.frame(mark, still) { make(it, picture) }
        if (shown != null) {
            drawImage(shown, colorFilter = filter)
            return
        }
        if (layerMark != mark) {
            layerMark = mark
            layer.record(size.toIntSize(), picture)
        }
        drawLayer(layer)
    }

    // the scene is recorded once, here on the main thread: the layer lays it down until its pixels, made on another thread, come
    private fun DrawScope.make(mark: Mark, picture: DrawScope.() -> Unit) {
        val images = images ?: return
        val kept = image ?: return
        val making = with(images) { record(layer, picture) }
        layerMark = mark
        // the screen may go while it is made: a making that has begun ends and hands its image in (to be freed), one that has not never
        // begins; either way the recording is let go only after it
        scope.launch(Dispatchers.Default) { kept.made(mark, making.make()) }.invokeOnCompletion { making.close() }
    }

    fun release(context: GraphicsContext) {
        image?.release()
        context.releaseGraphicsLayer(layer)
    }
}

@Composable
internal fun rememberKeptPicture(): KeptPicture {
    val context = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    // the debug switch -noBake draws the picture of Live without an image as well (A/B); asked only where there are images
    val images = if (stillImages != null && !sceneNoBake()) stillImages else null
    val kept = remember(context, images) { KeptPicture(context.createGraphicsLayer().apply { compositingStrategy = keptLayerStrategy }, images, scope) }
    DisposableEffect(kept) { onDispose { kept.release(context) } }
    return kept
}
