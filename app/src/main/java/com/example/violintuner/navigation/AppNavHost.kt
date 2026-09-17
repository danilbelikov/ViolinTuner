package com.example.violintuner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.violintuner.feature.history.HistoryScreen
import com.example.violintuner.feature.live.LiveRoute
import com.example.violintuner.feature.onboarding.OnboardingRoute
import com.example.violintuner.feature.session.SessionRoute
import com.example.violintuner.feature.session.SessionViewModel
import com.example.violintuner.feature.settings.SettingsRoute

const val ONBOARDING_ROUTE = "onboarding"
private const val SESSION_ROUTE = "session"

@Composable
fun AppNavHost(
    navController: NavHostController,
    startRoute: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
    ) {
        composable(ONBOARDING_ROUTE) {
            OnboardingRoute(onFinished = navController::navigateFromOnboardingToLive)
        }
        composable(TopLevelDestination.LIVE.route) { LiveRoute(onOpenSession = navController::navigateToSession) }
        composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
        // Above the tabs and without the bottom bar; back returns to where it was opened from.
        composable(
            route = "$SESSION_ROUTE/{${SessionViewModel.ARG_SESSION_ID}}",
            arguments = listOf(navArgument(SessionViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            SessionRoute(onClose = navController::popBackStack)
        }
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

fun NavHostController.navigateToSession(sessionId: Long) {
    navigate("$SESSION_ROUTE/$sessionId") { launchSingleTop = true }
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
