package com.violinjourney.app.feature.journey.art

import androidx.compose.ui.geometry.Rect

/** One step of drawing a scene: still layers baked once, living ones drawn every frame, or the life of the sky. */
sealed interface SceneStep {
    /** Layers that never change, in the order they are drawn: baked into one picture while the view stands. */
    class Still(val layers: IntArray) : SceneStep

    /** Layers that change with time, drawn every frame in their order. */
    class Alive(val layers: IntArray) : SceneStep

    /** The stars and clouds the app's rule draws over the sky. */
    data object SkyLife : SceneStep
}

/**
 * The order a living picture is drawn in so that what stands still can be baked (docs/plan-performance.md):
 * a still layer may sink under a living one it never touches, and so every still layer between two living
 * ones need not be a picture of its own. Whatever overlaps keeps its order — a lamp in front of the street
 * stays in front of the tram — so the picture looks exactly as it did; only what cannot touch is reordered.
 * A living layer overlaps by all the ground it covers as it moves. Pure, with tests.
 */
object SceneStrata {
    /**
     * [alive] — which layers change with time; [skyLifeAt] — the layer the sky's life is drawn over (−1 —
     * none), covering [skyLife]. The steps come still, living, still, living… each only where it has layers.
     */
    fun plan(layers: List<SceneLayer>, bounds: List<Rect>, alive: List<Boolean>, skyLifeAt: Int, skyLife: Rect?): List<SceneStep> {
        // the sky's life is one more living layer right after the one it is drawn over
        val order = ArrayList<Int>(layers.size + 1)
        for (index in layers.indices) {
            order += index
            if (index == skyLifeAt && skyLife != null) order += SKY
        }
        val reach = Array(order.size) { at -> if (order[at] == SKY) skyLife!! else reachOf(layers[order[at]], bounds[order[at]]) }
        val living = BooleanArray(order.size) { at -> order[at] == SKY || alive[order[at]] }
        // still layers sit on even levels, living ones on odd; each goes as low as what it overlaps allows
        val level = IntArray(order.size)
        for (at in order.indices) {
            var need = 0
            for (before in 0 until at) {
                if (!reach[before].overlaps(reach[at])) continue
                need = maxOf(need, level[before] + if (living[before] == living[at]) 0 else 1)
            }
            val parity = if (living[at]) 1 else 0
            level[at] = if (need % 2 == parity) need else need + 1
        }
        val steps = ArrayList<SceneStep>()
        for (current in (level.maxOrNull() ?: -1).let { 0..it }) {
            val run = ArrayList<Int>()
            fun close() {
                if (run.isEmpty()) return
                steps += if (current % 2 == 0) SceneStep.Still(run.toIntArray()) else SceneStep.Alive(run.toIntArray())
                run.clear()
            }
            for (at in order.indices) {
                if (level[at] != current) continue
                if (order[at] == SKY) {
                    close()
                    steps += SceneStep.SkyLife
                } else {
                    run += order[at]
                }
            }
            close()
        }
        return steps
    }

    /** All the ground the layers at [indices] may cover together; null — there are none. */
    fun reachOf(layers: List<SceneLayer>, bounds: List<Rect>, indices: List<Int>): Rect? =
        indices.map { reachOf(layers[it], bounds[it]) }.reduceOrNull { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }

    /** All the ground a layer may cover, in units of the grid: where it is drawn, and for a moving thing all of its way. */
    fun reachOf(layer: SceneLayer, bounds: Rect): Rect {
        // a sharp corner of an outline reaches out as far as the miter limit lets it
        val grow = layer.strokeWidth / 2 * MITER_LIMIT + EDGE
        var left = bounds.left * layer.scale + layer.tx - grow
        var top = bounds.top * layer.scale + layer.ty - grow
        var right = bounds.right * layer.scale + layer.tx + grow
        var bottom = bounds.bottom * layer.scale + layer.ty + grow
        val width = right - left
        val height = bottom - top
        layer.anim?.let { anim ->
            anim.ride?.let { left += it.from; right += it.to }
            anim.bird?.let {
                left = minOf(left, it.left - width)
                right = maxOf(right, it.left + it.span + width)
                top -= SceneMotion.BIRD_BOB
                bottom += SceneMotion.BIRD_BOB
            }
            anim.fly?.let { right += it.amplitude }
            anim.fall?.let {
                top = minOf(top, it.top - height)
                bottom = maxOf(bottom, it.top + it.height + height)
                left -= FALL_SWAY
                right += FALL_SWAY
            }
            anim.rise?.let { top -= it.distance ?: SMOKE_RISE }
            anim.sway?.let { left -= it.amplitude; right += it.amplitude }
            anim.bob?.let { top -= it.amplitude; bottom += it.amplitude }
            if (anim.swing != null || anim.peck != null) {
                // a turn about a point: the thing may sweep as far as its own size about it
                val size = maxOf(width, height)
                left -= size
                right += size + SceneMotion.PECK_STEP
                top -= size
                bottom += size
            }
        }
        if (layer.fill == WATER) {
            left -= SceneMotion.WATER_DRIFT
            right += SceneMotion.WATER_DRIFT
        }
        return Rect(left, top, right, bottom)
    }

    private const val SKY = -1
    private const val WATER = "waterLit"

    /** Antialiasing and rounding: two shapes a unit apart may still share a pixel. */
    private const val EDGE = 1f

    /** Android's default for a stroke's joins, which the scenes keep. */
    private const val MITER_LIMIT = 4f
    private const val FALL_SWAY = 10f
    private const val SMOKE_RISE = 10f
}
