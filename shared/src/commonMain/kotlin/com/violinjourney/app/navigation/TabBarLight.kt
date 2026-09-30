package com.violinjourney.app.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How bright the items of the tab bar are (spec 3.36.1, 3.36.6, 5.29): one per app, remembered by the root of the tabs
 * (`MainActivity` on Android, `IosApp` on iOS) and handed down through [LocalTabBarLight]. A screen lends its light while it
 * is shown and takes it back when it goes: Live, whose controls fade while a note sounds, lends it while resumed
 * (`LendTabBarLight`) — behind its switch `LiveSwitches.DIM_TAB_BAR`, off until the owner has looked from the stand; with it off
 * nobody lends, and [alpha] is always 1. Where the bar is drawn under Live — upright, not lying down — the root decides, by the same
 * rule it hides the bar by, and the light lent to a bar not drawn dims nothing.
 *
 * Not a view model's flow: the light of Live changes on every frame and lives in its composition; the lent lambda is read
 * only while the bar draws, so the bar is not recomposed for it. Only the one who lent the light can take it back: under
 * the crossfade of the navigation the screen that comes in lends before the one that leaves takes back (the lesson of
 * `ScreenOnHolds`), and the late taking-back must not put out the newcomer's light.
 *
 * The main thread only, where compositions are applied.
 */
@Stable
class TabBarLight {
    private var light: (() -> Float)? by mutableStateOf(null)
    private var owner: Any? = null

    /** The opacity of the items now, 1 — in full; read it while drawing. */
    fun alpha(): Float = light?.invoke() ?: FULL

    /** [owner] shows the bar at [light] until it takes it back; a later lender takes the light over. */
    fun lend(owner: Any, light: () -> Float) {
        this.owner = owner
        this.light = light
    }

    /** Back to full light — only if [owner] is still the one who lent it. */
    fun takeBack(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        light = null
    }

    private companion object {
        const val FULL = 1f
    }
}

/** The light of the tab bar of the app; a preview or a test without a root gets one that nobody lends. */
val LocalTabBarLight = staticCompositionLocalOf { TabBarLight() }
