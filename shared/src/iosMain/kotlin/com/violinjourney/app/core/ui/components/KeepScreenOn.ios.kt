package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        val application = UIApplication.sharedApplication
        val before = application.idleTimerDisabled
        application.idleTimerDisabled = true
        onDispose { application.idleTimerDisabled = before }
    }
}
