package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Durations and curves of the level and the gift sheet (handoff `Прогресс.dc.html`, `anims`; spec 5.29 R2 «Время»). */
internal object ProgressMotion {
    /** The ring of the path row and the bar of «Мой путь» growing within one level. */
    const val BAR_GROW_MS = 600

    // A level passed: the arc runs to the end, rests, and starts the new level from nothing.
    const val BAR_LEVEL_UP_FILL_MS = 400
    const val BAR_LEVEL_UP_PAUSE_MS = 200L
    const val BAR_LEVEL_UP_GROW_MS = 400

    // The gift: the trophy comes forward, the light behind it follows. No particles, no sound.
    const val GIFT_IN_MS = 400
    const val GIFT_SCALE_FROM = 0.88f
    const val GIFT_GLOW_MS = 600
    const val GIFT_GLOW_DELAY_MS = 200
    val GiftIn: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}
