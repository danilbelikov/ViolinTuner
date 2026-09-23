package com.violinjourney.app.navigation

import androidx.annotation.StringRes
import com.violinjourney.app.R

/**
 * Bottom-bar destinations in display order (spec section 4, handoff nav_bar 32c): the course of a practice, left to
 * right — begun, playing, listening. «Настройки» is not a tab: a gear on Live opens it above the tabs.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    PRACTICE("practice", R.string.nav_practice),
    LIVE("live", R.string.nav_live),
    /** «Записи»: the route keeps its old name, only the label changed (spec 4). */
    HISTORY("history", R.string.nav_history);

    companion object {
        /** The app opens on «Занятия» (spec 3.25): one comes to start a practice, and the home is there. The order of the tabs is another matter. */
        val START = PRACTICE
    }
}
