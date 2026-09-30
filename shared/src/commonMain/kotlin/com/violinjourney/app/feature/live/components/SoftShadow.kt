package com.violinjourney.app.feature.live.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.violinjourney.app.core.ui.components.blurMask
import com.violinjourney.app.core.ui.components.seal
import kotlin.math.roundToInt

/**
 * A soft shadow under a rounded shape of Live (plan Р3, spec 5.29 R6: the record key, the cards of the bottom row, the card «нет
 * разрешения»), baked: the shape is blurred once per size into a picture of a quarter of the pixels each way — a mask blur works there
 * on every Android version, and a shadow loses nothing at that resolution — sealed ([seal]), and a frame only lays it down, set down by
 * its drop, at an alpha read while drawing. A blur drawn anew would be played again on every frame of the window on iOS. The shadow
 * takes no room: it is drawn around the shape, past its bounds.
 */
internal class SoftShadow private constructor(private val image: ImageBitmap?, private val reach: Float, private val size: Size) {

    /** Lays the shadow down under the shape of this scope's size, [drop] lower, at [alpha] — nothing at 0. */
    fun draw(scope: DrawScope, drop: Float, alpha: Float) {
        val picture = image ?: return
        if (alpha <= 0f) return
        scope.drawImage(
            image = picture,
            dstOffset = IntOffset((-reach).roundToInt(), (drop - reach).roundToInt()),
            dstSize = IntSize((size.width + reach * 2).roundToInt(), (size.height + reach * 2).roundToInt()),
            alpha = alpha.coerceAtMost(1f),
            filterQuality = FilterQuality.Low,
        )
    }

    companion object {
        /** The shadow reaches this many sigmas past the shape: beyond that nothing is left to see. */
        private const val REACH_SIGMAS = 3f

        /** A blur loses nothing drawn at a quarter of the pixels each way, and takes a sixteenth of the memory. */
        private const val RESOLUTION = 0.25f

        /** A mask blur is given a radius, which Skia turns into a sigma as 0.57735 · r + 0.5. */
        private const val SIGMA_PER_RADIUS = 0.57735f
        private const val SIGMA_BIAS = 0.5f

        /** A mask blur needs a radius above zero. */
        private const val MIN_BLUR_RADIUS = 1f

        /**
         * The shadow of a rectangle [size] rounded by [corner], blurred with [sigma] — all in pixels; a CSS blur of B is a sigma of
         * B / 2. Nothing for an empty shape.
         */
        fun bake(size: Size, corner: Float, sigma: Float): SoftShadow {
            val reach = sigma * REACH_SIGMAS
            if (size.width <= 0f || size.height <= 0f) return SoftShadow(null, reach, size)
            val k = RESOLUTION
            val image = ImageBitmap(
                ((size.width + reach * 2) * k).roundToInt().coerceAtLeast(1),
                ((size.height + reach * 2) * k).roundToInt().coerceAtLeast(1),
            )
            val paint = Paint().apply { color = Color.Black }
            paint.blurMask(((sigma * k - SIGMA_BIAS) / SIGMA_PER_RADIUS).coerceAtLeast(MIN_BLUR_RADIUS))
            val round = corner.coerceAtMost(minOf(size.width, size.height) / 2) * k
            Canvas(image).drawRoundRect(reach * k, reach * k, (reach + size.width) * k, (reach + size.height) * k, round, round, paint)
            return SoftShadow(image.seal(), reach, size)
        }
    }
}
