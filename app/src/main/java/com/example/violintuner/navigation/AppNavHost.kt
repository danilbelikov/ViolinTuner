package com.example.violintuner.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.violintuner.R
import com.example.violintuner.feature.history.HistoryScreen
import com.example.violintuner.feature.live.LiveRoute
import com.example.violintuner.feature.onboarding.OnboardingRoute
import com.example.violintuner.feature.settings.SettingsRoute

const val ONBOARDING_ROUTE = "onboarding"

@Composable
fun AppNavHost(
    navController: NavHostController,
    startRoute: String,
    modifier: Modifier = Modifier,
) {
    // The session screen arrives with stage 9 (docs/plan-history.md); until then a saved
    // recording is confirmed with a toast instead of being opened.
    val context = LocalContext.current
    val onSessionSaved: (Long) -> Unit = {
        Toast.makeText(context, R.string.record_saved, Toast.LENGTH_SHORT).show()
    }
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(ONBOARDING_ROUTE) {
            OnboardingRoute(onFinished = navController::navigateFromOnboardingToLive)
        }
        composable(TopLevelDestination.LIVE.route) { LiveRoute(onOpenSession = onSessionSaved) }
        composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsRoute(onOpenOnboarding = navController::navigateToOnboarding)
        }
    }
}

/**
 * Switches bottom-bar tabs without stacking copies and keeps each tab's state. Live is the root
 * of the tabs whatever the graph started with, so the pop target is its route, not the graph's
 * start destination (that can be the onboarding, which is gone from the stack by then).
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(TopLevelDestination.START.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateFromOnboardingToLive() {
    navigate(TopLevelDestination.START.route) {
        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}

/** "See the onboarding again": nothing of the tabs stays under it. */
private fun NavHostController.navigateToOnboarding() {
    navigate(ONBOARDING_ROUTE) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
