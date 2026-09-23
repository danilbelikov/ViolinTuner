package com.violinjourney.app.core.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * True when the system setting «убрать анимации» is on. Decorative motion — the shine of the
 * level bar, the breathing dot, rolling numbers (spec 3.16) — asks this before it starts; a route
 * provides it, screens and components stay free of `Context`.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Read on every resume: the setting is changed in the system settings, while we are away. */
@Composable
fun rememberAnimationsRemoved(): Boolean {
    val context = LocalContext.current
    var removed by remember { mutableStateOf(context.animationsRemoved()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { removed = context.animationsRemoved() }
    return removed
}

private fun Context.animationsRemoved(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
