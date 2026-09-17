package com.example.violintuner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.violintuner.feature.history.HistoryScreen
import com.example.violintuner.feature.live.LiveRoute
import com.example.violintuner.feature.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.START.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.LIVE.route) { LiveRoute() }
        composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
        composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
    }
}

/** Switches bottom-bar tabs without stacking copies and keeps each tab's state. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
