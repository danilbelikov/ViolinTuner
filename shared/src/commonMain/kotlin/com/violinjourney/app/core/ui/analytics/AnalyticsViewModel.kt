package com.violinjourney.app.core.ui.analytics

import androidx.lifecycle.ViewModel
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.MicPermission
import com.violinjourney.app.core.analytics.ScreenOpen
import com.violinjourney.app.core.analytics.screenKeyOf
import com.violinjourney.app.core.ui.permission.MicPermissionAnswer

/**
 * What only a screen can see (spec 3.34): which screen was opened and what the system answered
 * about the microphone. It exists so that neither fact has to be threaded through the contract of
 * every feature that happens to ask.
 */
open class AnalyticsViewModel(private val analytics: Analytics) : ViewModel() {
    // the entry of the back stack told last: the view model outlives the activity, a turn of the phone included
    private var lastEntryId: String? = null

    /**
     * A screen is only ever opened by a touch, so this cannot fire while a note is sounding. Only
     * the name of the screen leaves: [screenKeyOf] cuts the arguments off the route.
     *
     * One event per entry of the back stack ([entryId]): the navigation tells the entry on top again when the
     * activity is made anew — a turn of the phone, another language — and that opens nothing. Coming back to an
     * entry after another one is a new opening.
     */
    fun onScreenOpened(entryId: String, route: String?) {
        if (entryId == lastEntryId) return
        lastEntryId = entryId
        screenKeyOf(route)?.let { analytics.track(ScreenOpen(it)) }
    }

    /**
     * Only an answer to a dialog that was actually shown, once per dialog, or a tap the system no longer shows it
     * for — not the check made on every return, and nothing when the microphone is allowed already.
     */
    fun onMicPermissionAnswered(answer: MicPermissionAnswer) {
        analytics.track(MicPermission(answer))
    }
}
