package com.violinjourney.app.feature.practice.components

import kotlin.math.PI
import kotlin.math.sin

/**
 * The lights inside «Начать занятие» (spec 3.16, 5.10; the prototype `start-button.html`), free of
 * Compose: three soft ovals drift over the fill, each along its own slow sines, so the pattern never
 * repeats. Where a light is — fractions of the button's width and height; time — seconds of the
 * button's own clock, which runs slower on a wide button and eases to a stop and back.
 */
object StartButtonMath {
    /**
     * One light: its centre sways around [x] and [y] by [swayX] and [swayY] along a sine on each
     * axis, with periods in seconds and phases of its own. [radiusX] is a share of the button's
     * width, [radiusY] of its height; [alpha] is its opacity at the centre.
     */
    data class Light(
        val x: Float,
        val swayX: Float,
        val periodX: Float,
        val phaseX: Float,
        val y: Float,
        val swayY: Float,
        val periodY: Float,
        val phaseY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val alpha: Float,
    )

    /** Back to front, in the order of `practiceColors.startLights`: periwinkle, rose, pearl. */
    val LIGHTS = listOf(
        Light(x = 0.50f, swayX = 0.28f, periodX = 21f, phaseX = 4.2f, y = 0.55f, swayY = 0.30f, periodY = 10.3f, phaseY = 2.9f, radiusX = 0.28f, radiusY = 1.3f, alpha = 0.75f),
        Light(x = 0.70f, swayX = 0.20f, periodX = 15.7f, phaseX = 2.3f, y = 0.62f, swayY = 0.30f, periodY = 9.1f, phaseY = 0.4f, radiusX = 0.30f, radiusY = 1.4f, alpha = 0.8f),
        Light(x = 0.28f, swayX = 0.18f, periodX = 14f, phaseX = 0f, y = 0.30f, swayY = 0.25f, periodY = 7.3f, phaseY = 1.1f, radiusX = 0.34f, radiusY = 1.3f, alpha = 0.9f),
    )

    /** The soft edge of a light: at these shares of its radius its opacity is these shares of [Light.alpha]. */
    val EDGE_AT = floatArrayOf(0f, 0.45f, 0.8f, 1f)
    val EDGE_ALPHA = floatArrayOf(1f, 0.62f, 0.16f, 0f)

    /** The moment the button shows when nothing moves (previews, «убрать анимации»): the lights apart, the pearl in the middle. */
    const val REST_SECONDS = 3.2f

    /** The width the paths are made for — portrait on a phone. A wider button runs its clock slower. */
    const val REFERENCE_WIDTH_DP = 380f

    /** No light crosses the button faster than this, on any width. */
    const val MAX_SPEED_DP_S = 35f

    /** The lights slow to a stop, and gather speed again, over this long. */
    const val EASE_S = 0.8f

    data class Point(val x: Float, val y: Float)

    /** The centre of light [index] at [seconds] of the button's clock, as fractions of the button. */
    fun centreAt(index: Int, seconds: Float): Point {
        val light = LIGHTS[index]
        return Point(
            x = light.x + light.swayX * sin(TAU * seconds / light.periodX + light.phaseX),
            y = light.y + light.swayY * sin(TAU * seconds / light.periodY + light.phaseY),
        )
    }

    /** Seconds of the button's clock in a second of time on a button [widthDp] wide: the lights go no faster in dp on a wider one. */
    fun paceFor(widthDp: Float): Float = if (widthDp <= REFERENCE_WIDTH_DP) 1f else REFERENCE_WIDTH_DP / widthDp

    /** The pace of the clock [dtSeconds] later on its way to [target] — 1 moving, 0 still — the whole way taking [EASE_S]. */
    fun eased(pace: Float, target: Float, dtSeconds: Float): Float {
        val step = dtSeconds / EASE_S
        return if (target > pace) minOf(target, pace + step) else maxOf(target, pace - step)
    }

    private const val TAU = 2 * PI.toFloat()
}
