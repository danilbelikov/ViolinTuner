package com.violinjourney.app.core.ui.analytics

import androidx.lifecycle.ViewModel
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.MicPermission
import com.violinjourney.app.core.analytics.ScreenOpen
import com.violinjourney.app.core.analytics.screenKeyOf
import com.violinjourney.app.core.ui.permission.MicPermissionAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What only a screen can see (spec 3.34): which screen was opened and what the system answered
 * about the microphone. It exists so that neither fact has to be threaded through the contract of
 * every feature that happens to ask.
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val analytics: Analytics) : ViewModel() {
    /**
     * A screen is only ever opened by a touch, so this cannot fire while a note is sounding. Only
     * the name of the screen leaves: [screenKeyOf] cuts the arguments off the route.
     */
    fun onScreenOpened(route: String?) {
        screenKeyOf(route)?.let { analytics.track(ScreenOpen(it)) }
    }

    /** Only an answer to a dialog that was actually shown — not the check made on every return. */
    fun onMicPermissionAnswered(answer: MicPermissionAnswer) {
        analytics.track(MicPermission(answer))
    }
}
