package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * One count for the whole app: the idle timer is the application's, not a window's. Screens hold it while shown, and a
 * copy of the data while it runs (`IosKeepAlive`) — whatever screen of the app is shown meanwhile.
 */
internal val appScreenOnHolds = ScreenOnHolds { on -> UIApplication.sharedApplication.idleTimerDisabled = on }

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        appScreenOnHolds.take()
        onDispose { appScreenOnHolds.give() }
    }
}
