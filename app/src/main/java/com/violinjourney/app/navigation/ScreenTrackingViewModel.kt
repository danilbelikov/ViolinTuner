package com.violinjourney.app.navigation

import androidx.lifecycle.ViewModel
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ScreenOpen
import com.violinjourney.app.core.analytics.screenKeyOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Which screens get opened (spec 3.34). A screen is only ever opened by a touch, so this event
 * cannot fire while a note is sounding — the rule that nothing is sent during play costs nothing
 * here. Only the name of the screen leaves: [screenKeyOf] cuts the arguments off the route.
 */
@HiltViewModel
class ScreenTrackingViewModel @Inject constructor(private val analytics: Analytics) : ViewModel() {
    fun onScreenOpened(route: String?) {
        screenKeyOf(route)?.let { analytics.track(ScreenOpen(it)) }
    }
}
