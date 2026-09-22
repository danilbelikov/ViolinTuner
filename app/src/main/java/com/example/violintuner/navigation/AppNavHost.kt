package com.example.violintuner.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.violintuner.core.domain.journey.JourneyRoute as JourneyStops
import com.example.violintuner.core.domain.repertoire.PieceSection
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.feature.backup.BackupRoute
import com.example.violintuner.feature.backup.RestoreRoute
import com.example.violintuner.feature.backup.RestoreViewModel
import com.example.violintuner.feature.history.HistoryRoute
import com.example.violintuner.feature.home.HomeRoute
import com.example.violintuner.feature.home.HomeView
import com.example.violintuner.feature.home.SplashKind
import com.example.violintuner.feature.home.SplashRoute
import com.example.violintuner.feature.journey.JourneyRoute
import com.example.violintuner.feature.journey.JourneyView
import com.example.violintuner.feature.journey.StopRoute
import com.example.violintuner.feature.journey.StopViewModel
import com.example.violintuner.feature.live.LiveRoute
import com.example.violintuner.feature.onboarding.OnboardingRoute
import com.example.violintuner.feature.practice.PracticeRoute
import com.example.violintuner.feature.repertoire.RepertoireViewModel
import com.example.violintuner.feature.repertoire.SectionKeys
import com.example.violintuner.feature.repertoire.SectionRoute
import com.example.violintuner.feature.repertoire.form.PieceFormRoute
import com.example.violintuner.feature.repertoire.form.PieceFormViewModel
import com.example.violintuner.feature.repertoire.piece.PieceRoute
import com.example.violintuner.feature.repertoire.piece.PieceViewModel
import com.example.violintuner.feature.repertoire.scale.ScaleFormRoute
import com.example.violintuner.feature.repertoire.scale.ScaleFormViewModel
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
private const val SCALE_FORM_ROUTE = "scaleForm"
private const val SECTION_ROUTE = "section"
private const val SECTION_PATTERN = "$SECTION_ROUTE/{${RepertoireViewModel.ARG_SECTION}}"
private const val JOURNEY_ROUTE = "journey"
private const val JOURNEY_MAP_ROUTE = "journeyMap"
private const val JOURNEY_PASSPORT_ROUTE = "journeyPassport"
private const val JOURNEY_STOP_ROUTE = "journeyStop"
private const val JOURNEY_STOP_PATTERN = "$JOURNEY_STOP_ROUTE/{${StopViewModel.ARG_STOP_ID}}"
private const val HOME_ROUTE = "home"
private const val HOME_SHOP_ROUTE = "homeShop"
private const val HOME_ARRANGE_ROUTE = "homeArrange"
private const val HOME_HOUSES_ROUTE = "homeHouses"
private const val SPLASH_AWAY_ROUTE = "splashAway"
private const val SPLASH_HOME_ROUTE = "splashHome"
private const val BACKUP_ROUTE = "backup"
private const val RESTORE_ROUTE = "restore"
private const val RESTORE_PATTERN = "$RESTORE_ROUTE?${RestoreViewModel.ARG_URI}={${RestoreViewModel.ARG_URI}}"

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
            OnboardingRoute(onFinished = navController::navigateFromOnboardingToLive, onRestore = navController::navigateToRestore)
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
                onOpenJourney = { navController.navigate(JOURNEY_ROUTE) { launchSingleTop = true } },
                onOpenHome = { navController.navigate(HOME_ROUTE) { launchSingleTop = true } },
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            HistoryRoute(
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                onOpenSection = navController::navigateToSection,
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
        // A section of the repertoire (spec 3.22): its list, above the tabs; «Гаммы» adds through a form of its own.
        composable(
            route = SECTION_PATTERN,
            arguments = listOf(navArgument(RepertoireViewModel.ARG_SECTION) { type = NavType.StringType }),
        ) {
            SectionRoute(
                onOpenPiece = navController::navigateToPiece,
                onNew = { section ->
                    if (section == SectionRef.BuiltIn(PieceSection.SCALES)) navController.navigateToScaleForm(pieceId = null)
                    else navController.navigateToPieceForm(pieceId = null, section = section)
                },
                onClose = navController::popBackStack,
            )
        }
        // The repertoire (spec 3.15): a piece, its form and its music stand, all above the tabs.
        composable(
            route = PIECE_PATTERN,
            arguments = listOf(navArgument(PieceViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            PieceRoute(
                onClose = navController::popBackStack,
                onOpenForm = { pieceId, focusNotes, scale ->
                    if (scale) navController.navigateToScaleForm(pieceId) else navController.navigateToPieceForm(pieceId, focusNotes)
                },
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
                "&${PieceFormViewModel.ARG_FOCUS_NOTES}={${PieceFormViewModel.ARG_FOCUS_NOTES}}" +
                "&${PieceFormViewModel.ARG_SECTION}={${PieceFormViewModel.ARG_SECTION}}",
            arguments = listOf(
                navArgument(PieceFormViewModel.ARG_SECTION) {
                    type = NavType.StringType
                    defaultValue = PieceSection.PIECES.name
                },
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
                onCloseDeleted = { navController.popUpToSection() },
            )
        }
        composable(
            route = "$SCALE_FORM_ROUTE?${ScaleFormViewModel.ARG_PIECE_ID}={${ScaleFormViewModel.ARG_PIECE_ID}}",
            arguments = listOf(
                navArgument(ScaleFormViewModel.ARG_PIECE_ID) {
                    type = NavType.LongType
                    defaultValue = ScaleFormViewModel.NEW_SCALE
                },
            ),
        ) {
            ScaleFormRoute(
                onClose = navController::popBackStack,
                // a new scale, and one that turned out to exist already, take the place of the form
                onOpenScale = { pieceId ->
                    navController.popBackStack()
                    navController.navigateToPiece(pieceId)
                },
                onCloseDeleted = { navController.popUpToSection() },
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsRoute(
                onOpenOnboarding = navController::navigateToOnboarding,
                onOpenSound = { navController.navigateToSound(sessionId = null) },
                onOpenBackup = navController::navigateToBackup,
                onOpenRestore = navController::navigateToRestore,
            )
        }
        // The journey (spec 3.23): above the tabs, without the bottom bar. The map and the passport are views of the same state.
        mapOf(JOURNEY_ROUTE to JourneyView.MAIN, JOURNEY_MAP_ROUTE to JourneyView.MAP, JOURNEY_PASSPORT_ROUTE to JourneyView.PASSPORT).forEach { (route, view) ->
            composable(route) {
                JourneyRoute(
                    view = view,
                    onOpenMap = { navController.navigate(JOURNEY_MAP_ROUTE) { launchSingleTop = true } },
                    onOpenPassport = { navController.navigate(JOURNEY_PASSPORT_ROUTE) { launchSingleTop = true } },
                    // home is not a stop with a postcard any more: it is a section of its own (spec 3.24)
                    // home is a section of its own (spec 3.24); the way into it goes through its title card (3.25)
                    onOpenStop = { stopId -> navController.navigate(if (stopId == JourneyStops.HOME) SPLASH_HOME_ROUTE else "$JOURNEY_STOP_ROUTE/$stopId") { launchSingleTop = true } },
                    // «Сыграть здесь» after an arrival: the tabs come back, on Live (spec 3.27)
                    onOpenLive = navController::navigateToLiveLeavingTheGame,
                    onClose = navController::popBackStack,
                )
            }
        }
        composable(
            route = JOURNEY_STOP_PATTERN,
            arguments = listOf(navArgument(StopViewModel.ARG_STOP_ID) { type = NavType.StringType }),
        ) {
            StopRoute(
                onClose = navController::popBackStack,
                // «Играть здесь»: the tabs come back, on Live, in this city (spec 3.27)
                onOpenLive = navController::navigateToLiveLeavingTheGame,
                onOpenHome = { navController.navigate(SPLASH_HOME_ROUTE) { launchSingleTop = true } },
            )
        }
        // The home (spec 3.24): four views of one state, above the tabs.
        mapOf(HOME_ROUTE to HomeView.MAIN, HOME_SHOP_ROUTE to HomeView.SHOP, HOME_ARRANGE_ROUTE to HomeView.ARRANGE, HOME_HOUSES_ROUTE to HomeView.HOUSES).forEach { (route, view) ->
            composable(route) {
                HomeRoute(
                    view = view,
                    onOpenShop = { navController.navigate(HOME_SHOP_ROUTE) { launchSingleTop = true } },
                    onOpenArrange = { navController.navigate(HOME_ARRANGE_ROUTE) { launchSingleTop = true } },
                    onOpenHouses = { navController.navigate(HOME_HOUSES_ROUTE) { launchSingleTop = true } },
                    onOpenHome = { if (!navController.popBackStack(HOME_ROUTE, inclusive = false)) navController.navigate(HOME_ROUTE) { launchSingleTop = true } },
                    onOpenJourney = { navController.navigate(SPLASH_AWAY_ROUTE) { launchSingleTop = true } },
                    onClose = navController::popBackStack,
                )
            }
        }
        // The title cards between the home and the journey (spec 3.25, 3.27): the player moves from one to the other.
        composable(SPLASH_AWAY_ROUTE) {
            SplashRoute(SplashKind.AWAY, onDone = { navController.navigateWithinTheGame(JOURNEY_ROUTE) })
        }
        composable(SPLASH_HOME_ROUTE) {
            SplashRoute(SplashKind.HOME, onDone = { navController.navigateWithinTheGame(HOME_ROUTE) })
        }
        // A copy of the data and its coming back (spec 3.20): above the tabs, without the bottom bar.
        composable(BACKUP_ROUTE) { BackupRoute(onClose = navController::popBackStack) }
        composable(
            route = RESTORE_PATTERN,
            arguments = listOf(navArgument(RestoreViewModel.ARG_URI) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            RestoreRoute(onClose = navController::popBackStack, onOpenBackup = navController::navigateToBackup)
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

/**
 * Between the home and the journey (spec 3.25, 3.27): a move, not a step deeper. Where the player goes lies
 * right on «Занятия», in place of where the player left and of the title card, so «назад» from the home or
 * the journey always leads to «Занятия», whichever way the player came.
 */
private fun NavHostController.navigateWithinTheGame(route: String) {
    navigate(route) {
        popUpTo(TopLevelDestination.START.route)
        launchSingleTop = true
    }
}

/**
 * From the journey to Live (spec 3.27, «Играть здесь»): the home, the journey and the stop lie on the
 * stack of «Занятия»; they are folded first, or the tab would open on them next time.
 */
fun NavHostController.navigateToLiveLeavingTheGame() {
    popBackStack(TopLevelDestination.START.route, inclusive = false)
    navigateToTopLevel(TopLevelDestination.LIVE)
}

fun NavHostController.navigateToBackup() {
    navigate(BACKUP_ROUTE) { launchSingleTop = true }
}

/** [uri] is the file the system's «Открыть» came back with; blank — a restore that is on its way already is come back to. */
fun NavHostController.navigateToRestore(uri: String) {
    navigate(if (uri.isBlank()) RESTORE_ROUTE else "$RESTORE_ROUTE?${RestoreViewModel.ARG_URI}=${Uri.encode(uri)}") { launchSingleTop = true }
}

/** The tap on the notification of a running job. */
fun NavHostController.navigateToRunningBackup(restoring: Boolean) {
    if (restoring) navigateToRestore("") else navigateToBackup()
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

/** [pieceId] null opens the form of a new element of [section]. */
fun NavHostController.navigateToPieceForm(pieceId: Long?, focusNotes: Boolean = false, section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES)) {
    val id = pieceId ?: PieceFormViewModel.NEW_PIECE
    navigate(
        "$PIECE_FORM_ROUTE?${PieceFormViewModel.ARG_PIECE_ID}=$id&${PieceFormViewModel.ARG_FOCUS_NOTES}=$focusNotes" +
            "&${PieceFormViewModel.ARG_SECTION}=${SectionKeys.keyOf(section)}",
    ) { launchSingleTop = true }
}

/** [pieceId] null opens the form of a new scale. */
fun NavHostController.navigateToScaleForm(pieceId: Long?) {
    navigate("$SCALE_FORM_ROUTE?${ScaleFormViewModel.ARG_PIECE_ID}=${pieceId ?: ScaleFormViewModel.NEW_SCALE}") { launchSingleTop = true }
}

fun NavHostController.navigateToSection(section: SectionRef) {
    navigate("$SECTION_ROUTE/${SectionKeys.keyOf(section)}") { launchSingleTop = true }
}

/** An element is gone with its form and its screen: back to the list of its section, or to the tab if it was opened from elsewhere. */
private fun NavHostController.popUpToSection() {
    if (!popBackStack(SECTION_PATTERN, inclusive = false)) popBackStack(TopLevelDestination.HISTORY.route, inclusive = false)
}
