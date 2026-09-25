package com.violinjourney.app.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.violinjourney.app.core.domain.journey.JourneyRoute as JourneyStops
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
import com.violinjourney.app.feature.camera.CaptureViewModel
import com.violinjourney.app.feature.history.HiltHistoryViewModel
import com.violinjourney.app.feature.history.HistoryRoute
import com.violinjourney.app.feature.history.HistorySection
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
import com.violinjourney.app.feature.repertoire.SectionKeys
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

private const val SESSION_ROUTE = "session"
private const val PIECE_ROUTE = "piece"
private const val PIECE_PATTERN = "$PIECE_ROUTE/{${PieceViewModel.ARG_PIECE_ID}}"
private const val STAND_ROUTE = "stand"
private const val CAPTURE_ROUTE = "capture"
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
private const val SETTINGS_ROUTE = "settings"
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
            // On a new phone a copy is the first thing a person with one needs (spec 3.20): the system's «Открыть», then the restore screen.
            val copy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { navController.navigateToRestore(it.toString()) } }
            OnboardingRoute(
                onFinished = navController::navigateFromOnboardingToLive,
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
                onOpenJourney = { navController.navigate(JOURNEY_ROUTE) { launchSingleTop = true } },
                onOpenHome = { navController.navigate(HOME_ROUTE) { launchSingleTop = true } },
                onOpenSettings = navController::navigateToSettings,
                viewModel = hiltViewModel<HiltPracticeViewModel>(),
                homeLookViewModel = hiltViewModel<HiltHomeLookViewModel>(),
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            val activity = LocalActivity.current
            HistoryRoute(
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                onOpenSection = navController::navigateToSection,
                onOpenPiece = navController::navigateToPiece,
                viewModel = hiltViewModel<HiltHistoryViewModel>(),
                sectionsViewModel = hiltViewModel<HiltSectionsViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // Above the tabs and without the bottom bar; back returns to where it was opened from.
        composable(
            route = "$SESSION_ROUTE/{${SessionViewModel.ARG_SESSION_ID}}",
            arguments = listOf(navArgument(SessionViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            SessionRoute(
                onClose = navController::popBackStack,
                onOpenSound = navController::navigateToSound,
                viewModel = hiltViewModel<HiltSessionViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
            )
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
            val shareViewModel = hiltViewModel<HiltShareViewModel>()
            SoundRoute(
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltSoundViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
            )
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
                viewModel = hiltViewModel<HiltRepertoireViewModel>(),
            )
        }
        // The repertoire (spec 3.15): a piece, its form and its music stand, all above the tabs.
        composable(
            route = PIECE_PATTERN,
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
                onOpenCapture = { pieceId -> navController.navigate("$CAPTURE_ROUTE/$pieceId") { launchSingleTop = true } },
                viewModel = hiltViewModel<HiltPieceViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
                onShare = shareViewModel::start,
                shareHost = { ShareHost(shareViewModel) },
                changingConfigurations = { activity?.isChangingConfigurations == true },
            )
        }
        // «Снять под минусовку» (spec 3.32): the app's own camera, over everything
        composable(
            route = "$CAPTURE_ROUTE/{${CaptureViewModel.ARG_PIECE_ID}}",
            arguments = listOf(navArgument(CaptureViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            CaptureRoute(onClose = navController::popBackStack)
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
            StandRoute(
                pieceViewModel = hiltViewModel<HiltPieceViewModel>(pieceEntry),
                onClose = navController::popBackStack,
                viewModel = hiltViewModel<HiltStandViewModel>(),
                tracking = hiltViewModel<HiltAnalyticsViewModel>(),
            )
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
                viewModel = hiltViewModel<HiltPieceFormViewModel>(),
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
                viewModel = hiltViewModel<HiltScaleFormViewModel>(),
            )
        }
        // «Настройки» (spec 3.8, 4): not a tab any more — the gear of Live opens them above the tabs, without the bottom bar.
        composable(SETTINGS_ROUTE) {
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
                    viewModel = hiltViewModel<HiltJourneyViewModel>(),
                    homeLookViewModel = hiltViewModel<HiltHomeLookViewModel>(),
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
                viewModel = hiltViewModel<HiltStopViewModel>(),
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
                    viewModel = hiltViewModel<HiltHomeViewModel>(),
                )
            }
        }
        // The title cards between the home and the journey (spec 3.25, 3.27): the player moves from one to the other.
        composable(SPLASH_AWAY_ROUTE) {
            SplashRoute(SplashKind.AWAY, onDone = { navController.navigateWithinTheGame(JOURNEY_ROUTE) }, viewModel = hiltViewModel<HiltHomeViewModel>())
        }
        composable(SPLASH_HOME_ROUTE) {
            SplashRoute(SplashKind.HOME, onDone = { navController.navigateWithinTheGame(HOME_ROUTE) }, viewModel = hiltViewModel<HiltHomeViewModel>())
        }
        // A copy of the data and its coming back (spec 3.20): above the tabs, without the bottom bar.
        composable(BACKUP_ROUTE) { BackupRoute(onClose = navController::popBackStack, viewModel = hiltViewModel<HiltBackupViewModel>()) }
        composable(
            route = RESTORE_PATTERN,
            arguments = listOf(navArgument(RestoreViewModel.ARG_URI) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            RestoreRoute(onClose = navController::popBackStack, onOpenBackup = navController::navigateToBackup, viewModel = hiltViewModel<HiltRestoreViewModel>())
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

/** «Открыть репертуар» from Live (spec 3.28): the tab «Записи»; «Репертуар» was asked for already (`HistorySectionAsk`). */
fun NavHostController.navigateToRepertoire() {
    navigateToTopLevel(TopLevelDestination.HISTORY)
}

/**
 * The tag of a running practice on Live (spec 3.12, handoff nav_bar 35): «Закончить занятие» is the sheet of «Занятия»,
 * with the recap after it — the tab is opened, the sheet was asked for already (`FinishPracticeAsk`).
 */
fun NavHostController.navigateToFinishPractice() {
    navigateToTopLevel(TopLevelDestination.PRACTICE)
}

fun NavHostController.navigateToSettings() {
    navigate(SETTINGS_ROUTE) { launchSingleTop = true }
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

/** Android 13 lets a person choose the language of one app; before it the app follows the device and there is nothing to open. */
@Composable
private fun rememberAppLanguageSettings(): (() -> Unit)? {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return { context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
}
