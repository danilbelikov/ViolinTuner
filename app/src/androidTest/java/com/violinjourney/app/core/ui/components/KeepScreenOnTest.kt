package com.violinjourney.app.core.ui.components

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two screens of one window hold the screen lit in the order of the navigation crossfade: the one
 * that comes in takes the flag before the one that leaves gives it back. Remembering the flag and
 * putting it back used to write the leaving screen's stale "off" over the newcomer's hold.
 */
@RunWith(AndroidJUnit4::class)
class KeepScreenOnTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theScreenStaysOnThroughACrossfadeAndGoesDarkAfterTheLastHolder() {
        var leaving by mutableStateOf(true)
        var coming by mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            if (leaving) KeepScreenOn()
            if (coming) KeepScreenOn()
        }
        compose.runOnIdle { assertTrue(view.keepScreenOn) }

        compose.runOnIdle { coming = true }
        compose.runOnIdle { leaving = false }
        compose.runOnIdle { assertTrue("the screen that came in holds it", view.keepScreenOn) }

        compose.runOnIdle { coming = false }
        compose.runOnIdle { assertFalse("nobody holds it any more", view.keepScreenOn) }
    }
}
