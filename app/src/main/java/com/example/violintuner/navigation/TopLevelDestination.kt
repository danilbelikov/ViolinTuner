package com.example.violintuner.navigation

import androidx.annotation.StringRes
import com.example.violintuner.R

/** Bottom-bar destinations in display order (spec section 4). */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    LIVE("live", R.string.nav_live),
    PRACTICE("practice", R.string.nav_practice),
    /** «Записи»: the route keeps its old name, only the label changed (spec 4). */
    HISTORY("history", R.string.nav_history),
    SETTINGS("settings", R.string.nav_settings);

    companion object {
        /** The app opens on «Занятия» (spec 3.25): one comes to start a practice, and the home is there. The order of the tabs is another matter. */
        val START = PRACTICE
    }
}
