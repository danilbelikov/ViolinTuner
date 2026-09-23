package com.violinjourney.app.feature.practice.components

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Durations and curves of the progress header and the gift sheet (handoff `Прогресс.dc.html`, `anims`). */
internal object ProgressMotion {
    /** The level bar growing within one level. */
    const val BAR_GROW_MS = 600

    // A level passed: the bar runs to the end, rests, and starts the new level from nothing.
    const val BAR_LEVEL_UP_FILL_MS = 400
    const val BAR_LEVEL_UP_PAUSE_MS = 200L
    const val BAR_LEVEL_UP_GROW_MS = 400
    const val CAPTION_CROSSFADE_MS = 150

    // A trophy taking its place in the row once its gift sheet is answered.
    const val TROPHY_FILL_MS = 200
    const val TROPHY_POP_MS = 250
    const val TROPHY_POP_FROM = 0.6f
    const val NEXT_TROPHY_FADE_MS = 200
    const val NEXT_TROPHY_DELAY_MS = 100
    private const val TROPHY_POP_TENSION = 1.2f
    val TrophyPop: Easing = OvershootInterpolator(TROPHY_POP_TENSION).let { curve -> Easing { curve.getInterpolation(it) } }

    // The gift: the trophy comes forward, the light behind it follows. No particles, no sound.
    const val GIFT_IN_MS = 400
    const val GIFT_SCALE_FROM = 0.88f
    const val GIFT_GLOW_MS = 600
    const val GIFT_GLOW_DELAY_MS = 200
    val GiftIn: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}
