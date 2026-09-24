package com.violinjourney.app.navigation

import org.jetbrains.compose.resources.StringResource
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.nav_live
import com.violinjourney.app.shared.resources.nav_practice

/**
 * Bottom-bar destinations in display order (spec section 4, handoff nav_bar 32c): the course of a practice, left to
 * right — begun, playing, listening. «Настройки» is not a tab: a gear on Live opens it above the tabs.
 */
enum class TopLevelDestination(
    val route: String,
    val labelRes: StringResource,
) {
    PRACTICE("practice", Res.string.nav_practice),
    LIVE("live", Res.string.nav_live),
    /** «Записи»: the route keeps its old name, only the label changed (spec 4). */
    HISTORY("history", Res.string.nav_history);

    companion object {
        /** The app opens on «Занятия» (spec 3.25): one comes to start a practice, and the home is there. The order of the tabs is another matter. */
        val START = PRACTICE
    }
}

/** The first start, until it is gone through (spec 3.7, 3.33): the route of the onboarding, outside the tabs. */
const val ONBOARDING_ROUTE = "onboarding"
