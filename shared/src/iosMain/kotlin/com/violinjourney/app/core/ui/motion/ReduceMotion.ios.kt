package com.violinjourney.app.core.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberAnimationsRemoved(): Boolean {
    var removed by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { removed = UIAccessibilityIsReduceMotionEnabled() }
    return removed
}
