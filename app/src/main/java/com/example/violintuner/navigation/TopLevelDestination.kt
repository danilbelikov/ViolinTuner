package com.example.violintuner.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.violintuner.R

/** Bottom-bar destinations in display order (spec section 4). */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    LIVE("live", R.string.nav_live, R.drawable.ic_nav_live),
    HISTORY("history", R.string.nav_history, R.drawable.ic_nav_history),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_nav_settings);

    companion object {
        val START = LIVE
    }
}
