package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOffset
import kotlin.math.roundToInt

/**
 * The two sliders of «Минусовка» on «Звук» (spec 3.32) between their track and the backing's level and shift: the screen
 * draws the thumb at a fraction, the view model turns a dragged fraction into a value — by the same [BackingConfig], the
 * one the app gives the view model, so the thumb stands where the value is.
 */
object BackingSliders {
    fun gainFraction(gainDb: Float, config: BackingConfig): Float =
        ((gainDb - config.minGainDb) / (config.maxGainDb - config.minGainDb)).coerceIn(0f, 1f)

    fun gainAt(fraction: Float, config: BackingConfig): Float = config.minGainDb + fraction * (config.maxGainDb - config.minGainDb)

    fun offsetFraction(offsetMs: Int, config: BackingConfig): Float =
        ((offsetMs - config.minOffsetMs).toFloat() / (config.maxOffsetMs - config.minOffsetMs)).coerceIn(0f, 1f)

    /** The slider lands on whole steps of the shift (spec 5.25); «Как записано» keeps the exact one worked out while recording. */
    fun offsetAt(fraction: Float, config: BackingConfig): Int =
        BackingOffset.snap((config.minOffsetMs + fraction * (config.maxOffsetMs - config.minOffsetMs)).roundToInt(), config)
}
