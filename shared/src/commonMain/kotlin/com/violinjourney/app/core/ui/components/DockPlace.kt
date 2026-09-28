package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the bottom zone of the screen is — for the message of iOS (spec 5.29), which lives at the root above the screens and does
 * not see a composition local of a screen. An [AppDock] reports the top of its zone here while it is shown, and withdraws when it
 * goes; the message stands above it.
 *
 * The one that came last counts: under the crossfade of the navigation the screen that comes in reports before the one that leaves
 * withdraws (the lesson of `ScreenOnHolds` and `TabBarLight`). A report of an older one updates its value but does not take the
 * first place back; only the one who reported can withdraw. The main thread only, where compositions are applied.
 */
@Stable
class DockPlace {
    // in the order of the first report: the last one counts
    private val owners = LinkedHashMap<Any, Int>()

    /** From the bottom of the root to the top of the zone that counts, in pixels; null — no zone on screen. */
    var rise: Int? by mutableStateOf(null)
        private set

    fun report(owner: Any, rise: Int) {
        owners[owner] = rise
        this.rise = owners.values.last()
    }

    fun withdraw(owner: Any) {
        if (owners.remove(owner) == null) return
        rise = owners.values.lastOrNull()
    }
}

/** The place of the bottom zone for the message of iOS; given only by `IosApp` — on Android the message is the system's. */
val LocalDockPlace = staticCompositionLocalOf<DockPlace?> { null }

/**
 * How high the bottom of the message of iOS rises over the bottom of the root, in pixels (spec 5.29): [aboveDock] over a bottom zone
 * that stands above the bottom inset; with no zone, or with the zone under the keyboard ([dockRise] no higher than [insetBottom],
 * the inset with the keyboard in it), [aboveInset] over the inset — over the keyboard when it is up.
 */
internal object ToastLift {
    fun of(dockRise: Int?, insetBottom: Int, aboveDock: Int, aboveInset: Int): Int =
        if (dockRise != null && dockRise > insetBottom) dockRise + aboveDock else insetBottom + aboveInset
}
