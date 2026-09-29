package com.violinjourney.app.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.analytics.HiltAnalyticsViewModel
import com.violinjourney.app.feature.backup.BACKUP_FILE_TYPES
import com.violinjourney.app.feature.backup.BackupRoute
import com.violinjourney.app.feature.backup.DataBlock
import com.violinjourney.app.feature.backup.HiltBackupViewModel
import com.violinjourney.app.feature.backup.HiltDataBlockViewModel
import com.violinjourney.app.feature.backup.HiltRestoreViewModel
import com.violinjourney.app.feature.backup.RestoreRoute
import com.violinjourney.app.feature.backup.RestoreViewModel
import com.violinjourney.app.feature.camera.CaptureRoute
import com.violinjourney.app.feature.camera.HiltCaptureViewModel
import com.violinjourney.app.feature.camera.CaptureViewModel
import com.violinjourney.app.feature.history.HiltHistoryViewModel
import com.violinjourney.app.feature.history.HistoryRoute
import com.violinjourney.app.feature.home.HiltHomeLookViewModel
import com.violinjourney.app.feature.home.HiltHomeViewModel
import com.violinjourney.app.feature.home.HomeRoute
import com.violinjourney.app.feature.home.HomeView
import com.violinjourney.app.feature.home.SplashKind
import com.violinjourney.app.feature.home.SplashRoute
import com.violinjourney.app.feature.journey.HiltJourneyViewModel
import com.violinjourney.app.feature.journey.HiltStopViewModel
import com.violinjourney.app.feature.journey.JourneyRoute
import com.violinjourney.app.feature.journey.JourneyView
import com.violinjourney.app.feature.journey.StopRoute
import com.violinjourney.app.feature.journey.StopViewModel
import com.violinjourney.app.feature.live.HiltLiveViewModel
import com.violinjourney.app.feature.live.LiveRoute
import com.violinjourney.app.feature.live.block.HiltBlockViewModel
import com.violinjourney.app.feature.onboarding.HiltOnboardingViewModel
import com.violinjourney.app.feature.repertoire.piece.HiltPieceViewModel
import com.violinjourney.app.feature.repertoire.stand.HiltStandViewModel
import com.violinjourney.app.feature.onboarding.OnboardingRoute
import com.violinjourney.app.feature.practice.HiltPracticeViewModel
import com.violinjourney.app.feature.practice.PracticeRoute
import com.violinjourney.app.feature.repertoire.HiltRepertoireViewModel
import com.violinjourney.app.feature.repertoire.RepertoireViewModel
import com.violinjourney.app.feature.repertoire.SectionRoute
import com.violinjourney.app.feature.repertoire.form.HiltPieceFormViewModel
import com.violinjourney.app.feature.repertoire.form.PieceFormRoute
import com.violinjourney.app.feature.repertoire.form.PieceFormViewModel
import com.violinjourney.app.feature.repertoire.piece.PieceRoute
import com.violinjourney.app.feature.repertoire.piece.PieceViewModel
import com.violinjourney.app.feature.repertoire.scale.HiltScaleFormViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleFormRoute
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.sections.HiltSectionsViewModel
import com.violinjourney.app.feature.repertoire.sections.SectionsRoute
import com.violinjourney.app.feature.repertoire.stand.StandRoute
import com.violinjourney.app.feature.repertoire.stand.StandViewModel
import com.violinjourney.app.feature.session.SessionRoute
import com.violinjourney.app.feature.session.SessionViewModel
import com.violinjourney.app.feature.session.HiltSessionViewModel
import com.violinjourney.app.feature.settings.HiltSettingsViewModel
import com.violinjourney.app.feature.sound.HiltSoundViewModel
import com.violinjourney.app.feature.settings.SettingsRoute
import com.violinjourney.app.feature.share.ShareHost
import com.violinjourney.app.feature.share.HiltShareViewModel
import com.violinjourney.app.feature.sound.SoundRoute
import com.violinjourney.app.feature.sound.SoundViewModel

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
            // On a new phone a copy is the first thing a person with one needs (spec 3.20): the system's «Открыть», then the restore screen.
            val copy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { navController.navigateToRestore(it.toString()) } }
            OnboardingRoute(
                onFinished = navController::navigateFromOnboarding,
                onHaveBackup = { copy.launch(BACKUP_FILE_TYPES) },
                viewModel = hiltViewModel<HiltOnboardingViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
            )
        }
        composable(TopLevelDestination.LIVE.route) {
            LiveRoute(
                onOpenSession = navController::navigateToSession,
                onFinishPractice = navController::navigateToFinishPractice,
                onOpenRepertoire = navController::navigateToRepertoire,
                onOpenSettings = navController::navigateToSettings,
                viewModel = hiltViewModel<HiltLiveViewModel>(),
                blockViewModel = hiltViewModel<HiltBlockViewModel>(),
                homeLookViewModel = hiltViewModel<HiltHomeLookViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
                showVenue = !BuildConfig.PLAIN_LIVE,
            )
        }
        composable(TopLevelDestination.PRACTICE.route) {
            PracticeRoute(
                onOpenLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
                onOpenSession = navController::navigateToSession,
                onOpenJourney = { navController.navigate(Routes.JOURNEY) { launchSingleTop = true } },
                onOpenHome = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                onOpenSettings = navController::navigateToSettings,
                viewModel = hiltViewModel<HiltPracticeViewModel>(),
                homeLookViewModel = hiltViewModel<HiltHomeLookViewModel>(),
            )
        }
        // «Репертуар» (spec 3.36.1): the sections under a title; a section's list, a piece and its forms open above the tabs.
        composable(TopLevelDestination.REPERTOIRE.route) {
            SectionsRoute(
                onOpenSection = navController::navigateToSection,
                onOpenPiece = navController::navigateToPiece,
                onNew = navController::navigateToNew,
                viewModel = hiltViewModel<HiltSectionsViewModel>(),
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            val activity = LocalActivity.current
            HistoryRoute(
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                viewModel = hiltViewModel<HiltHistoryViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // Above the tabs and without the bottom bar; back returns to where it was opened from.
        composable(
            route = Routes.SESSION_PATTERN,
            arguments = listOf(navArgument(SessionViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            val activity = LocalActivity.current
            SessionRoute(
                onClose = navController::popBackStack,
                onOpenSound = navController::navigateToSound,
                viewModel = hiltViewModel<HiltSessionViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // «Звук» (spec 3.17): of one recording, or — without an id — the default of all of them. Above the tabs.
        composable(
            route = Routes.SOUND_PATTERN,
            arguments = listOf(
                navArgument(SoundViewModel.ARG_SESSION_ID) {
                    type = NavType.LongType
                    defaultValue = SoundViewModel.EVERYONE
                },
            ),
        ) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            val activity = LocalActivity.current
            SoundRoute(
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltSoundViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // A section of the repertoire (spec 3.22): its list, above the tabs; «Гаммы» adds through a form of its own.
        composable(
            route = Routes.SECTION_PATTERN,
            arguments = listOf(navArgument(RepertoireViewModel.ARG_SECTION) { type = NavType.StringType }),
        ) {
            SectionRoute(
                onOpenPiece = navController::navigateToPiece,
                onNew = navController::navigateToNew,
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltRepertoireViewModel>(),
            )
        }
        // The repertoire (spec 3.15): a piece, its form and its music stand, all above the tabs.
        composable(
            route = Routes.PIECE_PATTERN,
            arguments = listOf(navArgument(PieceViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            val activity = LocalActivity.current
            PieceRoute(
                onClose = navController::popBackStack,
                onOpenForm = { pieceId, focusNotes, scale ->
                    if (scale) navController.navigateToScaleForm(pieceId) else navController.navigateToPieceForm(pieceId, focusNotes)
                },
                onOpenStand = navController::navigateToStand,
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                onOpenCapture = navController::navigateToCapture,
                viewModel = hiltViewModel<HiltPieceViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // «Снять под минусовку» (spec 3.32): the app's own camera, over everything
        composable(
            route = Routes.CAPTURE_PATTERN,
            arguments = listOf(navArgument(CaptureViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            val activity = LocalActivity.current
            CaptureRoute(
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltCaptureViewModel>(),
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        composable(
            route = Routes.STAND_PATTERN,
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
            val pieceEntry = remember(entry) { navController.getBackStackEntry(Routes.PIECE_PATTERN) }
            StandRoute(
                pieceViewModel = hiltViewModel<HiltPieceViewModel>(pieceEntry),
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltStandViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
            )
        }
        composable(
            route = Routes.PIECE_FORM_PATTERN,
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
                viewModel = hiltViewModel<HiltPieceFormViewModel>(),
            )
        }
        composable(
            route = Routes.SCALE_FORM_PATTERN,
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
                viewModel = hiltViewModel<HiltScaleFormViewModel>(),
            )
        }
        // «Настройки» (spec 3.8, 4): not a tab any more — the gear of Live opens them above the tabs, without the bottom bar.
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onOpenOnboarding = navController::navigateToOnboarding,
                onOpenSound = { navController.navigateToSound(sessionId = null) },
                onClose = navController::popBackStack,
                onLanguageClick = rememberAppLanguageSettings(),
                dataBlock = { analyticsEnabled, onAnalyticsChange ->
                    DataBlock(
                        onOpenBackup = navController::navigateToBackup,
                        onOpenRestore = navController::navigateToRestore,
                        analyticsEnabled = analyticsEnabled,
                        onAnalyticsChange = onAnalyticsChange,
                        viewModel = hiltViewModel<HiltDataBlockViewModel>(),
                    )
                },
                viewModel = hiltViewModel<HiltSettingsViewModel>(),
            )
        }
        // The journey (spec 3.23): above the tabs, without the bottom bar. The map and the passport are views of the same state.
        mapOf(Routes.JOURNEY to JourneyView.MAIN, Routes.JOURNEY_MAP to JourneyView.MAP, Routes.JOURNEY_PASSPORT to JourneyView.PASSPORT).forEach { (route, view) ->
            composable(route) {
                JourneyRoute(
                    view = view,
                    onOpenMap = { navController.navigate(Routes.JOURNEY_MAP) { launchSingleTop = true } },
                    onOpenPassport = { navController.navigate(Routes.JOURNEY_PASSPORT) { launchSingleTop = true } },
                    // home is a section of its own (spec 3.24); the way into it goes through its title card (3.25)
                    onOpenStop = { stopId -> navController.navigate(Routes.stop(stopId)) { launchSingleTop = true } },
                    // «Сыграть здесь» after an arrival: the tabs come back, on Live (spec 3.27)
                    onOpenLive = navController::navigateToLiveLeavingTheGame,
                    onClose = navController::popBackStack,
                    viewModel = hiltViewModel<HiltJourneyViewModel>(),
                    homeLookViewModel = hiltViewModel<HiltHomeLookViewModel>(),
                )
            }
        }
        composable(
            route = Routes.JOURNEY_STOP_PATTERN,
            arguments = listOf(navArgument(StopViewModel.ARG_STOP_ID) { type = NavType.StringType }),
        ) {
            StopRoute(
                onClose = navController::popBackStack,
                // «Играть здесь»: the tabs come back, on Live, in this city (spec 3.27)
                onOpenLive = navController::navigateToLiveLeavingTheGame,
                onOpenHome = { navController.navigate(Routes.SPLASH_HOME) { launchSingleTop = true } },
                viewModel = hiltViewModel<HiltStopViewModel>(),
            )
        }
        // The home (spec 3.24): four views of one state, above the tabs.
        mapOf(Routes.HOME to HomeView.MAIN, Routes.HOME_SHOP to HomeView.SHOP, Routes.HOME_ARRANGE to HomeView.ARRANGE, Routes.HOME_HOUSES to HomeView.HOUSES).forEach { (route, view) ->
            composable(route) {
                HomeRoute(
                    view = view,
                    onOpenShop = { navController.navigate(Routes.HOME_SHOP) { launchSingleTop = true } },
                    onOpenArrange = { navController.navigate(Routes.HOME_ARRANGE) { launchSingleTop = true } },
                    onOpenHouses = { navController.navigate(Routes.HOME_HOUSES) { launchSingleTop = true } },
                    onOpenHome = { if (!navController.popBackStack(Routes.HOME, inclusive = false)) navController.navigate(Routes.HOME) { launchSingleTop = true } },
                    onOpenJourney = { navController.navigate(Routes.SPLASH_AWAY) { launchSingleTop = true } },
                    onClose = navController::popBackStack,
                    viewModel = hiltViewModel<HiltHomeViewModel>(),
                )
            }
        }
        // The title cards between the home and the journey (spec 3.25, 3.27): the player moves from one to the other.
        composable(Routes.SPLASH_AWAY) {
            SplashRoute(SplashKind.AWAY, onDone = { navController.navigateWithinTheGame(Routes.JOURNEY) }, viewModel = hiltViewModel<HiltHomeViewModel>())
        }
        composable(Routes.SPLASH_HOME) {
            SplashRoute(SplashKind.HOME, onDone = { navController.navigateWithinTheGame(Routes.HOME) }, viewModel = hiltViewModel<HiltHomeViewModel>())
        }
        // A copy of the data and its coming back (spec 3.20): above the tabs, without the bottom bar.
        composable(Routes.BACKUP) { BackupRoute(onClose = navController::popBackStack, viewModel = hiltViewModel<HiltBackupViewModel>()) }
        composable(
            route = Routes.RESTORE_PATTERN,
            arguments = listOf(navArgument(RestoreViewModel.ARG_URI) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            RestoreRoute(onClose = navController::popBackStack, onOpenBackup = navController::navigateToBackup, viewModel = hiltViewModel<HiltRestoreViewModel>())
        }
    }
}

/**
 * Switches bottom-bar tabs without stacking copies and keeps each tab's state. «Занятия» ([TopLevelDestination.START],
 * spec 3.25) is the root of the tabs whatever the graph started with, so the pop target is its route, not the graph's
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
    navigate(Routes.BACKUP) { launchSingleTop = true }
}

/** [uri] is the file the system's «Открыть» came back with; blank — a restore that is on its way already is come back to. */
fun NavHostController.navigateToRestore(uri: String) {
    navigate(Routes.restore(uri)) { launchSingleTop = true }
}

/** The tap on the notification of a running job. */
fun NavHostController.navigateToRunningBackup(restoring: Boolean) {
    if (restoring) navigateToRestore("") else navigateToBackup()
}

/** «Открыть репертуар» from Live (spec 3.28): the tab «Репертуар» (spec 3.36.1), with whatever it was left on. */
fun NavHostController.navigateToRepertoire() {
    navigateToTopLevel(TopLevelDestination.REPERTOIRE)
}

/**
 * The tag of a running practice on Live (spec 3.12, handoff nav_bar 35): «Закончить занятие» is the sheet of «Занятия»,
 * with the recap after it — the tab is opened, the sheet was asked for already (`FinishPracticeAsk`).
 */
fun NavHostController.navigateToFinishPractice() {
    navigateToTopLevel(TopLevelDestination.PRACTICE)
}

fun NavHostController.navigateToSettings() {
    navigate(Routes.SETTINGS) { launchSingleTop = true }
}

fun NavHostController.navigateToSession(sessionId: Long) {
    navigate(Routes.session(sessionId)) { launchSingleTop = true }
}

private fun NavHostController.navigateFromOnboarding() {
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
    navigate(Routes.piece(pieceId)) { launchSingleTop = true }
}

/** [sessionId] null opens the sound of all recordings. */
fun NavHostController.navigateToSound(sessionId: Long?) {
    navigate(Routes.sound(sessionId)) { launchSingleTop = true }
}

/** Opens the music stand of a piece at [pageIndex] (from zero). */
fun NavHostController.navigateToStand(pieceId: Long, pageIndex: Int) {
    navigate(Routes.stand(pieceId, pageIndex)) { launchSingleTop = true }
}

/** The form of the element [pieceId], at its notes when [focusNotes]; a new element opens by [navigateToNew]. */
fun NavHostController.navigateToPieceForm(pieceId: Long, focusNotes: Boolean) {
    navigate(Routes.pieceForm(pieceId, focusNotes)) { launchSingleTop = true }
}

/** The form of the scale [pieceId]. */
fun NavHostController.navigateToScaleForm(pieceId: Long) {
    navigate(Routes.scaleForm(pieceId)) { launchSingleTop = true }
}

/** The form of a new element of [section]: a scale's own for «Гаммы», a piece's for the rest (`Routes.newElement`, spec 3.36.4). */
fun NavHostController.navigateToNew(section: SectionRef) {
    navigate(Routes.newElement(section)) { launchSingleTop = true }
}

fun NavHostController.navigateToSection(section: SectionRef) {
    navigate(Routes.section(section)) { launchSingleTop = true }
}

/** «Снять под минусовку» (spec 3.32): the app's own camera for a take of [pieceId]. */
fun NavHostController.navigateToCapture(pieceId: Long) {
    navigate(Routes.capture(pieceId)) { launchSingleTop = true }
}

/**
 * An element is gone with its form and its screen: back to the list of its section, or — opened from «Время по элементам»,
 * with no section behind it — to the tab «Репертуар» (spec 3.36.1).
 */
private fun NavHostController.popUpToSection() {
    if (!popBackStack(Routes.SECTION_PATTERN, inclusive = false)) popBackStack(TopLevelDestination.REPERTOIRE.route, inclusive = false)
}

/**
 * Android 13 lets a person choose the language of one app; before it the app follows the device and there is nothing to open.
 * Some builds of Android 13+ have no such screen: the app's page in the system settings is the nearest thing (some put the
 * language there), and a build without that either leaves a line in the log — not a fall of the app.
 */
@Composable
private fun rememberAppLanguageSettings(): (() -> Unit)? {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return {
        val app = Uri.fromParts("package", context.packageName, null)
        try {
            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, app))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no screen for the language of the app", e)
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
            } catch (e2: ActivityNotFoundException) {
                Log.w(TAG, "no page of the app in the system settings either", e2)
            }
        }
    }
}

private const val TAG = "AppNavHost"
