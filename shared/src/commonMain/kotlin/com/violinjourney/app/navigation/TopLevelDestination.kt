package com.violinjourney.app.navigation

import org.jetbrains.compose.resources.StringResource
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.nav_live
import com.violinjourney.app.shared.resources.nav_practice
import com.violinjourney.app.shared.resources.nav_repertoire

/**
 * Bottom-bar destinations in display order (spec 3.36.1, section 4): the course of a practice, left to right — begun,
 * playing, what is played, listening. «Настройки» is not a tab: a gear on Live opens it above the tabs.
 */
enum class TopLevelDestination(
    val route: String,
    val labelRes: StringResource,
) {
    PRACTICE("practice", Res.string.nav_practice),
    LIVE("live", Res.string.nav_live),
    /** «Репертуар»: the sections of the repertoire under a title (spec 3.36.1); until 0.79 they lived behind a switch in «Записи». */
    REPERTOIRE("repertoire", Res.string.nav_repertoire),
    /**
     * «Записи»: the recordings only (spec 3.36.1). The route keeps its old name — it is the key of `screen_open` (spec 5.27,
     * 5.29), and only the label changed (spec 4).
     */
    HISTORY("history", Res.string.nav_history);

    companion object {
        /** The app opens on «Занятия» (spec 3.25): one comes to start a practice, and the home is there. The order of the tabs is another matter. */
        val START = PRACTICE
    }
}

/** The first start, until it is gone through (spec 3.7, 3.33): the route of the onboarding, outside the tabs. */
const val ONBOARDING_ROUTE = "onboarding"
