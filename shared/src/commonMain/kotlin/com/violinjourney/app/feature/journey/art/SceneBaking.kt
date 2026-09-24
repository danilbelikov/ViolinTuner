package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * What stands still in a living picture, baked (docs/plan-performance.md). The biggest still steps of
 * [PreparedScene.steps] — at most [MAX_BAKED], of [MIN_LAYERS] layers or more — are drawn once into
 * pictures the GPU keeps, each as large as what it holds; a frame only lays them down. They are baked
 * again only when the view changes, and not while it keeps changing: a frame whose view differs from
 * the one before is drawn directly.
 */
class SceneBaking internal constructor(private val context: GraphicsContext, val prepared: PreparedScene) {
    /** Where the picture stands in its box: the box, the grid's origin in it and the scale. */
    data class View(val width: Float, val height: Float, val left: Float, val top: Float, val k: Float)

    private val layers: Map<Int, GraphicsLayer> = prepared.steps.withIndex()
        .filter { (_, step) -> step is SceneStep.Still && step.layers.size >= MIN_LAYERS }
        .sortedByDescending { (it.value as SceneStep.Still).layers.size }
        .take(MAX_BAKED)
        .associate { (at, _) -> at to context.createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen } }
    private var lastView: View? = null
    private var bakedView: View? = null

    /** True when [view] is the one of the frame before: the picture stands, its still steps can be baked and kept. */
    fun stands(view: View): Boolean {
        val same = view == lastView
        lastView = view
        return same
    }

    /** Bakes every baked step for [view] with [record], unless they already are baked for it. */
    fun bake(view: View, record: (GraphicsLayer, SceneStep.Still) -> Unit) {
        if (view == bakedView) return
        layers.forEach { (at, layer) -> record(layer, prepared.steps[at] as SceneStep.Still) }
        bakedView = view
    }

    /** The baked picture of the step at [at]; null — the step is drawn every frame. */
    fun layerOf(at: Int): GraphicsLayer? = layers[at]

    internal fun release() = layers.values.forEach(context::releaseGraphicsLayer)

    companion object {
        /**
         * The pixels of the box a baked step takes: [reach] (units of the grid) at [View.k] pixels a unit
         * from the grid's origin, cut to the box and rounded out to whole pixels, so that the picture lies
         * on the same pixels as the layers drawn directly would. Null — none of it is in the box.
         */
        fun pixelsOf(reach: Rect, view: View): IntRect? {
            val left = maxOf(0f, view.left + reach.left * view.k)
            val top = maxOf(0f, view.top + reach.top * view.k)
            val right = minOf(view.width, view.left + reach.right * view.k)
            val bottom = minOf(view.height, view.top + reach.bottom * view.k)
            if (right <= left || bottom <= top) return null
            return IntRect(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())
        }

        /** Pictures a scene may keep: the biggest still steps, each no larger than what it holds; the rest are drawn as before. */
        const val MAX_BAKED = 4

        /** A still step of fewer layers is cheaper to draw than to keep as a picture and lay down. */
        const val MIN_LAYERS = 8
    }
}

/** The baking of [prepared], for as long as it is shown; null while there is nothing to show. */
@Composable
fun rememberSceneBaking(prepared: PreparedScene?): SceneBaking? {
    val context = LocalGraphicsContext.current
    val noBake = sceneNoBake()
    val baking = remember(prepared, context) { prepared?.takeUnless { noBake }?.let { SceneBaking(context, it) } }
    DisposableEffect(baking) { onDispose { baking?.release() } }
    return baking
}
