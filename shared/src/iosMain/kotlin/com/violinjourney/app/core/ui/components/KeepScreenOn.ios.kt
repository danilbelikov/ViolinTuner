package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/** One count for the whole app: the idle timer is the application's, not a window's. */
private val holds = ScreenOnHolds { on -> UIApplication.sharedApplication.idleTimerDisabled = on }

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        holds.take()
        onDispose { holds.give() }
    }
}
