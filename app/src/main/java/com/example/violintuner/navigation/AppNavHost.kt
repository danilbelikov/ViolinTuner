package com.example.violintuner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.violintuner.feature.history.HistoryRoute
import com.example.violintuner.feature.live.LiveRoute
import com.example.violintuner.feature.onboarding.OnboardingRoute
import com.example.violintuner.feature.practice.PracticeRoute
import com.example.violintuner.feature.repertoire.form.PieceFormRoute
import com.example.violintuner.feature.repertoire.form.PieceFormViewModel
import com.example.violintuner.feature.repertoire.piece.PieceRoute
import com.example.violintuner.feature.repertoire.piece.PieceViewModel
import com.example.violintuner.feature.repertoire.stand.StandRoute
import com.example.violintuner.feature.repertoire.stand.StandViewModel
import com.example.violintuner.feature.session.SessionRoute
import com.example.violintuner.feature.session.SessionViewModel
import com.example.violintuner.feature.settings.SettingsRoute
import com.example.violintuner.feature.sound.SoundRoute
import com.example.violintuner.feature.sound.SoundViewModel

const val ONBOARDING_ROUTE = "onboarding"
private const val SESSION_ROUTE = "session"
private const val PIECE_ROUTE = "piece"
private const val PIECE_PATTERN = "$PIECE_ROUTE/{${PieceViewModel.ARG_PIECE_ID}}"
private const val STAND_ROUTE = "stand"
private const val SOUND_ROUTE = "sound"
private const val PIECE_FORM_ROUTE = "pieceForm"

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
        composable(TopLevelDestination.LIVE.route) {
            LiveRoute(
                onOpenSession = navController::navigateToSession,
                onOpenPractice = { navController.navigateToTopLevel(TopLevelDestination.PRACTICE) },
            )
        }
        composable(TopLevelDestination.PRACTICE.route) {
            PracticeRoute(
                onOpenLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
                onOpenSession = navController::navigateToSession,
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryRoute(
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                onOpenPiece = navController::navigateToPiece,
                onNewPiece = { navController.navigateToPieceForm(pieceId = null) },
            )
        }
        // Above the tabs and without the bottom bar; back returns to where it was opened from.
        composable(
            route = "$SESSION_ROUTE/{${SessionViewModel.ARG_SESSION_ID}}",
            arguments = listOf(navArgument(SessionViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            SessionRoute(onClose = navController::popBackStack, onOpenSound = navController::navigateToSound)
        }
        // «Звук» (spec 3.17): of one recording, or — without an id — the default of all of them. Above the tabs.
        composable(
            route = "$SOUND_ROUTE?${SoundViewModel.ARG_SESSION_ID}={${SoundViewModel.ARG_SESSION_ID}}",
            arguments = listOf(
                navArgument(SoundViewModel.ARG_SESSION_ID) {
                    type = NavType.LongType
                    defaultValue = SoundViewModel.EVERYONE
                },
            ),
        ) {
            SoundRoute(onClose = navController::popBackStack)
        }
        // The repertoire (spec 3.15): a piece, its form and its music stand, all above the tabs.
        composable(
            route = PIECE_PATTERN,
            arguments = listOf(navArgument(PieceViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            PieceRoute(
                onClose = navController::popBackStack,
                onOpenForm = { pieceId, focusNotes -> navController.navigateToPieceForm(pieceId, focusNotes) },
                onOpenStand = navController::navigateToStand,
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
            )
        }
        composable(
            route = "$STAND_ROUTE/{${StandViewModel.ARG_PIECE_ID}}?${StandViewModel.ARG_PAGE}={${StandViewModel.ARG_PAGE}}",
            arguments = listOf(
                navArgument(StandViewModel.ARG_PIECE_ID) { type = NavType.LongType },
                navArgument(StandViewModel.ARG_PAGE) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            // The stand only opens from its piece, so that screen lies right under it. Its view model
            // owns the take: shared, a recording walks between the two screens unbroken.
            val pieceEntry = remember(entry) { navController.getBackStackEntry(PIECE_PATTERN) }
            StandRoute(pieceViewModel = hiltViewModel(pieceEntry), onClose = navController::popBackStack)
        }
        composable(
            route = "$PIECE_FORM_ROUTE?${PieceFormViewModel.ARG_PIECE_ID}={${PieceFormViewModel.ARG_PIECE_ID}}" +
                "&${PieceFormViewModel.ARG_FOCUS_NOTES}={${PieceFormViewModel.ARG_FOCUS_NOTES}}",
            arguments = listOf(
                navArgument(PieceFormViewModel.ARG_PIECE_ID) {
                    type = NavType.LongType
                    defaultValue = PieceFormViewModel.NEW_PIECE
                },
                navArgument(PieceFormViewModel.ARG_FOCUS_NOTES) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            PieceFormRoute(
                onClose = navController::popBackStack,
                // The new piece takes the place of its form: "back" from it leads to the list.
                onOpenCreated = { pieceId ->
                    navController.popBackStack()
                    navController.navigateToPiece(pieceId)
                },
                // The form of a piece lies on that piece's screen: both go.
                onCloseDeleted = { navController.popBackStack(TopLevelDestination.HISTORY.route, inclusive = false) },
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsRoute(onOpenOnboarding = navController::navigateToOnboarding, onOpenSound = { navController.navigateToSound(sessionId = null) })
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

fun NavHostController.navigateToPiece(pieceId: Long) {
    navigate("$PIECE_ROUTE/$pieceId") { launchSingleTop = true }
}

/** [sessionId] null opens the sound of all recordings. */
fun NavHostController.navigateToSound(sessionId: Long?) {
    navigate("$SOUND_ROUTE?${SoundViewModel.ARG_SESSION_ID}=${sessionId ?: SoundViewModel.EVERYONE}") { launchSingleTop = true }
}

/** Opens the music stand of a piece at [pageIndex] (from zero). */
fun NavHostController.navigateToStand(pieceId: Long, pageIndex: Int) {
    navigate("$STAND_ROUTE/$pieceId?${StandViewModel.ARG_PAGE}=$pageIndex") { launchSingleTop = true }
}

/** [pieceId] null opens the form of a new piece. */
fun NavHostController.navigateToPieceForm(pieceId: Long?, focusNotes: Boolean = false) {
    val id = pieceId ?: PieceFormViewModel.NEW_PIECE
    navigate("$PIECE_FORM_ROUTE?${PieceFormViewModel.ARG_PIECE_ID}=$id&${PieceFormViewModel.ARG_FOCUS_NOTES}=$focusNotes") {
        launchSingleTop = true
    }
}
