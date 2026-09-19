package com.example.violintuner.feature.practice.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** Durations of the living «Занятия» screen (handoff `Иконки и жизнь`, `anims`; spec 5.10). */
internal object PracticeMotion {
    // The dot of «Занятие идёт» breathes; it does not blink.
    const val DOT_HALF_BREATH_MS = 2_000
    const val DOT_SCALE_TO = 1.25f
    const val DOT_ALPHA_FROM = 0.7f
    val Breath: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f) // EaseInOutSine

    /** The arc around the dot goes round once a minute, led by the seconds of the timer. */
    const val RING_TURN_MS = 60_000L
    const val RING_ARC_DEGREES = 90f

    // Numbers of the summary roll to their new values.
    const val ROLL_MS = 600
    const val ROLL_STREAK_MS = 400

    /** A change smaller than this is simply shown. */
    const val ROLL_FROM_MINUTES = 5

    // The streak flame (handoff `Записи`, `anims`; spec 5.12): a candle. It lives for a few
    // seconds and comes to rest — blik, timer and flame all at once read as a shop window.
    const val FLAME_FROM_DAYS = 3
    const val FLAME_FULL_FROM_DAYS = 7
    const val FLAME_HOT_FROM_DAYS = 30
    const val FLAME_CYCLE_MS = 2_600L
    const val FLAME_ALIVE_MS = 6_000L
    const val FLAME_SETTLE_MS = 600L
    const val FLAME_FLARE_MS = 600
    const val FLAME_FLARE_UP_MS = 200
    const val FLAME_FLARE_SCALE = 1.35f
    const val FLAME_APPEAR_MS = 200L

    /** The fallback of the handoff, to be decided on a phone: false — the flame comes alive only when the streak grows. */
    const val FLAME_SWAY_ON_OPEN = true
}
