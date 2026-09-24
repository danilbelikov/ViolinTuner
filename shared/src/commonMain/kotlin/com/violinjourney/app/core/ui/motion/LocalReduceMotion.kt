package com.violinjourney.app.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the system setting «убрать анимации» is on. Decorative motion — the shine of the
 * level bar, the breathing dot, rolling numbers (spec 3.16) — asks this before it starts; a route
 * provides it, screens and components stay free of `Context`.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The system asks for less motion («убрать анимации» on Android, «Уменьшение движения» on iOS); read again on every return to the app. Goes into [LocalReduceMotion]. */
@Composable
expect fun rememberAnimationsRemoved(): Boolean
