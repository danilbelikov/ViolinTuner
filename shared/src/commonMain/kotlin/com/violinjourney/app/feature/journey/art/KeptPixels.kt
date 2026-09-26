package com.violinjourney.app.feature.journey.art

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer

/*
 * What a picture kept between frames is on each platform (docs/plan-performance.md, «На iOS»). On
 * Android a GraphicsLayer is a RenderNode, and Offscreen makes it a hardware layer: the GPU keeps its
 * pixels between frames, and a colour filter is applied as the texture is laid down. On iOS (skiko) a
 * GraphicsLayer keeps the drawing, not the pixels: every frame of the window plays the recording again,
 * and Offscreen or a colour filter wrap each playing in a full-size saveLayer. So there a picture that
 * stands still is turned into pixels by the app itself ([stillImages]).
 */

/** The compositing of a layer kept between frames: a hardware layer on Android, plain on iOS — there it would be only one more offscreen pass. */
internal expect val keptLayerStrategy: CompositingStrategy

/** Pixels of a picture that stands still, where a layer does not keep its own; null — it does (Android). */
internal expect val stillImages: StillImages?

/** Turns a still picture into one image: recorded once on the main thread, made into pixels off it. */
internal interface StillImages {
    /**
     * Records [picture] for the box of this scope once, and records [layer] to lay that recording down:
     * the frames until the image comes draw the layer. The recording is turned into pixels by the making
     * returned — off the main thread, once.
     */
    fun DrawScope.record(layer: GraphicsLayer, picture: DrawScope.() -> Unit): StillMaking

    /** Lets the pixels of [image] go at once. A frame already recorded with it keeps its own reference. */
    fun free(image: ImageBitmap)
}

/** The pixels of one recorded picture, made once. */
internal interface StillMaking {
    /** Off the main thread: the picture as a sealed image the size of its box; null — there was no memory for it. */
    fun make(): ImageBitmap?

    /** Lets the recording go; after [make], or instead of it. */
    fun close()
}
