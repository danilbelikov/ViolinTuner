package com.violinjourney.app.core.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * One count per view of a window — `view.keepScreenOn` is its window's FLAG_KEEP_SCREEN_ON, and every screen of the
 * NavHost shares the one view. The count holds the view only weakly, so a recreated activity is not kept alive.
 */
private val holdsOf = WeakHashMap<View, ScreenOnHolds>()

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val holds = holdsOf.getOrPut(view) {
            val weak = WeakReference(view)
            ScreenOnHolds { on -> weak.get()?.keepScreenOn = on }
        }
        holds.take()
        onDispose { holds.give() }
    }
}
