package com.violinjourney.app.core.ui.components

/**
 * Who keeps the screen lit: it stays on while at least one holder is present, whatever order
 * holders come and go in, and [apply] is told after every change. One per flag — per window on
 * Android, per app on iOS. Remembering the flag and putting it back broke under the crossfade of
 * the navigation: the screen that comes in takes the flag before the one that leaves gives it
 * back, and the leaving one then wrote its stale "before" over the newcomer's hold.
 *
 * Not thread-safe: the main thread only, where compositions are applied. Do not mix with
 * Compose's `Modifier.keepScreenOn()` — it keeps its own count on the same flag.
 */
internal class ScreenOnHolds(private val apply: (on: Boolean) -> Unit) {
    private var count = 0

    val held: Boolean get() = count > 0

    fun take() {
        count++
        apply(held)
    }

    fun give() {
        if (count > 0) count--
        apply(held)
    }
}
