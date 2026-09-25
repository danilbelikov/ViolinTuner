package com.violinjourney.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.violinjourney.app.core.audio.backing.IosBackingPreview
import com.violinjourney.app.core.domain.journey.JourneyRoute as JourneyStops
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.analytics.AnalyticsViewModel
import com.violinjourney.app.feature.backup.BackupRoute
import com.violinjourney.app.feature.backup.BackupViewModel
import com.violinjourney.app.feature.backup.DataBlock
import com.violinjourney.app.feature.backup.DataBlockViewModel
import com.violinjourney.app.feature.backup.RestoreRoute
import com.violinjourney.app.feature.backup.RestoreViewModel
import com.violinjourney.app.feature.backup.rememberBackupSystem
import com.violinjourney.app.feature.history.HistoryRoute
import com.violinjourney.app.feature.history.HistoryViewModel
import com.violinjourney.app.feature.home.HomeLookViewModel
import com.violinjourney.app.feature.home.HomeRoute
import com.violinjourney.app.feature.home.HomeView
import com.violinjourney.app.feature.home.HomeViewModel
import com.violinjourney.app.feature.home.SplashKind
import com.violinjourney.app.feature.home.SplashRoute
import com.violinjourney.app.feature.journey.JourneyRoute
import com.violinjourney.app.feature.journey.JourneyView
import com.violinjourney.app.feature.journey.JourneyViewModel
import com.violinjourney.app.feature.journey.StopRoute
import com.violinjourney.app.feature.journey.StopViewModel
import com.violinjourney.app.feature.live.LiveRoute
import com.violinjourney.app.feature.live.LiveViewModel
import com.violinjourney.app.feature.live.block.BlockViewModel
import com.violinjourney.app.feature.onboarding.OnboardingRoute
import com.violinjourney.app.feature.onboarding.OnboardingViewModel
import com.violinjourney.app.feature.practice.PracticeRoute
import com.violinjourney.app.feature.practice.PracticeViewModel
import com.violinjourney.app.feature.repertoire.RepertoireViewModel
import com.violinjourney.app.feature.repertoire.SectionKeys
import com.violinjourney.app.feature.repertoire.SectionRoute
import com.violinjourney.app.feature.repertoire.form.PieceFormRoute
import com.violinjourney.app.feature.repertoire.form.PieceFormViewModel
import com.violinjourney.app.feature.repertoire.piece.PieceRoute
import com.violinjourney.app.feature.repertoire.piece.PieceViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleFormRoute
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.sections.SectionsViewModel
import com.violinjourney.app.feature.repertoire.stand.StandRoute
import com.violinjourney.app.feature.repertoire.stand.StandViewModel
import com.violinjourney.app.feature.session.SessionRoute
import com.violinjourney.app.feature.session.SessionViewModel
import com.violinjourney.app.feature.settings.SettingsRoute
import com.violinjourney.app.feature.settings.SettingsViewModel
import com.violinjourney.app.feature.share.ShareHost
import com.violinjourney.app.feature.share.ShareViewModel
import com.violinjourney.app.feature.sound.SoundRoute
import com.violinjourney.app.feature.sound.SoundViewModel
import com.violinjourney.app.navigation.ONBOARDING_ROUTE
import com.violinjourney.app.navigation.TopLevelDestination
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

private const val SESSION_ROUTE = "session"
private const val SOUND_ROUTE = "sound"
private const val PIECE_ROUTE = "piece"
private const val PIECE_PATTERN = "$PIECE_ROUTE/{${PieceViewModel.ARG_PIECE_ID}}"
private const val STAND_ROUTE = "stand"
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

/**
 * The graph of screens, as `AppNavHost` on Android: the same routes and the same moves between them. What is not on
 * iOS yet — the own camera — is not in the graph, and the ways to it do nothing for now.
 */
@Composable
internal fun IosNavHost(graph: IosGraph, texts: IosTexts, navController: NavHostController, startRoute: String, modifier: Modifier) {
    val notYet: (Long) -> Unit = {}
    NavHost(navController = navController, startDestination = startRoute, modifier = modifier) {
        composable(ONBOARDING_ROUTE) {
            // on a new phone a copy is the first thing a person with one needs (spec 3.20): Files, then the restore screen
            val copy = rememberBackupSystem(onCopyPicked = { uri -> uri?.let(navController::navigateToRestore) })
            OnboardingRoute(
                onFinished = navController::navigateFromOnboarding,
                onHaveBackup = copy.pickCopy,
                viewModel = viewModel { OnboardingViewModel(graph.settings, createSavedStateHandle()) },
                tracking = viewModel { AnalyticsViewModel(graph.analytics) },
            )
        }
        composable(TopLevelDestination.LIVE.route) {
            LiveRoute(
                onOpenSession = navController::navigateToSession,
                onFinishPractice = { navController.navigateToTopLevel(TopLevelDestination.PRACTICE) },
                onOpenRepertoire = { navController.navigateToTopLevel(TopLevelDestination.HISTORY) },
                onOpenSettings = navController::navigateToSettings,
                viewModel = viewModel {
                    LiveViewModel(graph.takes(), graph.configSource, graph.runningPractice, graph.clock, graph.venues, graph.analytics, graph.finishAsk)
                },
                blockViewModel = viewModel {
                    BlockViewModel(
                        graph.runningPractice, graph.blockStore, graph.blockHistory, graph.repertoire, graph.sessions, graph.practiceConfig,
                        graph.clock, graph.sectionAsk,
                    )
                },
                homeLookViewModel = viewModel { HomeLookViewModel(graph.home) },
                tracking = viewModel { AnalyticsViewModel(graph.analytics) },
            )
        }
        composable(TopLevelDestination.PRACTICE.route) {
            PracticeRoute(
                onOpenLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) },
                onOpenSession = navController::navigateToSession,
                onOpenJourney = { navController.navigate(JOURNEY_ROUTE) { launchSingleTop = true } },
                onOpenHome = { navController.navigate(HOME_ROUTE) { launchSingleTop = true } },
                onOpenSettings = navController::navigateToSettings,
                viewModel = viewModel {
                    PracticeViewModel(
                        graph.practice, graph.runningPractice, graph.finisher, graph.sessions, graph.practiceConfig, graph.repertoire,
                        graph.clock, graph.trophies, graph.profiles, graph.avatarFiles, graph.progressConfig, graph.journey, graph.venues,
                        graph.blockStore, graph.journeyConfig, graph.finishAsk, graph.analytics,
                    )
                },
                homeLookViewModel = viewModel { HomeLookViewModel(graph.home) },
            )
        }
        composable(TopLevelDestination.HISTORY.route) {
            val share = viewModel { shareViewModel(graph, texts) }
            HistoryRoute(
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                onOpenSection = navController::navigateToSection,
                onOpenPiece = navController::navigateToPiece,
                viewModel = viewModel {
                    HistoryViewModel(graph.sessions, graph.repertoire, graph.intonationConfig, graph.clock, graph.audioFiles, graph.sectionAsk)
                },
                sectionsViewModel = viewModel {
                    SectionsViewModel(graph.repertoire, graph.repertoireConfig, graph.clock, graph.blockHistory, graph.practiceConfig)
                },
                onShare = share::start,
                shareHost = { ShareHost(share) },
            )
        }
        // A recording (spec 3.10): above the tabs, without the bottom bar; back returns to where it was opened from.
        composable(
            route = "$SESSION_ROUTE/{${SessionViewModel.ARG_SESSION_ID}}",
            arguments = listOf(navArgument(SessionViewModel.ARG_SESSION_ID) { type = NavType.LongType }),
        ) {
            val share = viewModel { shareViewModel(graph, texts) }
            SessionRoute(
                onClose = navController::popBackStack,
                onOpenSound = navController::navigateToSound,
                viewModel = viewModel {
                    SessionViewModel(
                        graph.sessions, graph.intonationConfig, graph.audioFiles, graph.playerFactory, graph.repertoire, graph.sound,
                        graph.soundConfig, graph.pictureFactory, createSavedStateHandle(), graph.backings, graph.backingPcm,
                    )
                },
                onShare = share::start,
                shareHost = { ShareHost(share) },
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
            val share = viewModel { shareViewModel(graph, texts) }
            SoundRoute(
                onClose = navController::popBackStack,
                viewModel = viewModel {
                    SoundViewModel(
                        createSavedStateHandle(), graph.sound, graph.sessions, graph.repertoire, graph.audioFiles, graph.playerFactory,
                        graph.waveforms, graph.soundConfig, graph.backings, graph.backingPcm, graph.backingConfig,
                    )
                },
                onShare = share::start,
                shareHost = { ShareHost(share) },
            )
        }
        // The repertoire (spec 3.15): a piece and its music stand, above the tabs.
        composable(route = PIECE_PATTERN, arguments = listOf(navArgument(PieceViewModel.ARG_PIECE_ID) { type = NavType.LongType })) {
            val share = viewModel { shareViewModel(graph, texts) }
            PieceRoute(
                onClose = navController::popBackStack,
                onOpenForm = { pieceId, focusNotes, scale ->
                    if (scale) navController.navigateToScaleForm(pieceId) else navController.navigateToPieceForm(pieceId, focusNotes)
                },
                onOpenStand = navController::navigateToStand,
                onOpenSession = navController::navigateToSession,
                onOpenSound = navController::navigateToSound,
                viewModel = viewModel { pieceViewModel(graph, createSavedStateHandle()) },
                tracking = viewModel { AnalyticsViewModel(graph.analytics) },
                onShare = share::start,
                shareHost = { ShareHost(share) },
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
            // The stand only opens from its piece, which lies right under it; the take belongs to that view model (spec 3.15).
            val pieceEntry = remember(entry) { navController.getBackStackEntry(PIECE_PATTERN) }
            StandRoute(
                pieceViewModel = viewModel(pieceEntry) { pieceViewModel(graph, createSavedStateHandle()) },
                onClose = navController::popBackStack,
                viewModel = viewModel {
                    StandViewModel(createSavedStateHandle(), graph.repertoire, graph.sheetFiles, graph.standHints, graph.repertoireConfig, graph.clock)
                },
                tracking = viewModel { AnalyticsViewModel(graph.analytics) },
            )
        }
        // A section of the repertoire (spec 3.22): its list, above the tabs; «Гаммы» adds through a form of its own.
        composable(route = SECTION_PATTERN, arguments = listOf(navArgument(RepertoireViewModel.ARG_SECTION) { type = NavType.StringType })) {
            SectionRoute(
                onOpenPiece = navController::navigateToPiece,
                onNew = { section ->
                    if (section == SectionRef.BuiltIn(PieceSection.SCALES)) navController.navigateToScaleForm(pieceId = null)
                    else navController.navigateToPieceForm(pieceId = null, section = section)
                },
                onClose = navController::popBackStack,
                viewModel = viewModel {
                    RepertoireViewModel(createSavedStateHandle(), graph.repertoire, graph.sessions, graph.sheetFiles, graph.repertoireConfig, graph.clock)
                },
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
                onCloseDeleted = { navController.popUpToSection() },
                viewModel = viewModel { PieceFormViewModel(createSavedStateHandle(), graph.repertoire, graph.repertoireConfig, graph.clock) },
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
                viewModel = viewModel {
                    ScaleFormViewModel(createSavedStateHandle(), graph.repertoire, graph.repertoireConfig, graph.clock, texts.scale)
                },
            )
        }
        // «Настройки» (spec 3.8, 4): the gear of Live opens them above the tabs, without the bottom bar.
        composable(SETTINGS_ROUTE) {
            SettingsRoute(
                onOpenOnboarding = navController::navigateToOnboarding,
                onOpenSound = { navController.navigateToSound(null) },
                onClose = navController::popBackStack,
                // iOS keeps the language of each app in its Settings, on the app's own page
                onLanguageClick = ::openAppSettings,
                // the copy of the data comes to iOS with its own step; the statistics switch has nothing to send yet
                dataBlock = { analyticsEnabled, onAnalyticsChange ->
                    DataBlock(
                        onOpenBackup = navController::navigateToBackup,
                        onOpenRestore = navController::navigateToRestore,
                        analyticsEnabled = analyticsEnabled,
                        onAnalyticsChange = onAnalyticsChange,
                        viewModel = viewModel { DataBlockViewModel(graph.backupManager, graph.backupPrefs, graph.sessions, graph.backupStore, graph.backupConfig, graph.clock) },
                    )
                },
                viewModel = viewModel { SettingsViewModel(graph.settings, graph.sound, graph.soundConfig) },
            )
        }
        // A copy of the data and its coming back (spec 3.20): above the tabs, without the bottom bar.
        composable(BACKUP_ROUTE) {
            BackupRoute(
                onClose = navController::popBackStack,
                viewModel = viewModel { BackupViewModel(graph.backupManager, graph.backupStore, graph.backupConfig, graph.recordingWatch, graph.videoImporter) },
            )
        }
        composable(
            route = RESTORE_PATTERN,
            arguments = listOf(
                navArgument(RestoreViewModel.ARG_URI) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            RestoreRoute(
                onClose = navController::popBackStack,
                onOpenBackup = navController::navigateToBackup,
                viewModel = viewModel {
                    RestoreViewModel(graph.backupManager, graph.backupStore, graph.recordingWatch, graph.videoImporter, createSavedStateHandle())
                },
            )
        }
        // The journey (spec 3.23): above the tabs; the map and the passport are views of the same state.
        mapOf(JOURNEY_ROUTE to JourneyView.MAIN, JOURNEY_MAP_ROUTE to JourneyView.MAP, JOURNEY_PASSPORT_ROUTE to JourneyView.PASSPORT).forEach { (route, view) ->
            composable(route) {
                JourneyRoute(
                    view = view,
                    onOpenMap = { navController.navigate(JOURNEY_MAP_ROUTE) { launchSingleTop = true } },
                    onOpenPassport = { navController.navigate(JOURNEY_PASSPORT_ROUTE) { launchSingleTop = true } },
                    onOpenStop = { stopId ->
                        navController.navigate(if (stopId == JourneyStops.HOME) SPLASH_HOME_ROUTE else "$JOURNEY_STOP_ROUTE/$stopId") { launchSingleTop = true }
                    },
                    onOpenLive = navController::navigateToLiveLeavingTheGame,
                    onClose = navController::popBackStack,
                    viewModel = viewModel { JourneyViewModel(graph.journey, graph.clock, graph.venues) },
                    homeLookViewModel = viewModel { HomeLookViewModel(graph.home) },
                )
            }
        }
        composable(route = JOURNEY_STOP_PATTERN, arguments = listOf(navArgument(StopViewModel.ARG_STOP_ID) { type = NavType.StringType })) {
            StopRoute(
                onClose = navController::popBackStack,
                onOpenLive = navController::navigateToLiveLeavingTheGame,
                onOpenHome = { navController.navigate(SPLASH_HOME_ROUTE) { launchSingleTop = true } },
                viewModel = viewModel { StopViewModel(createSavedStateHandle(), graph.journey, graph.journeyConfig, graph.clock, graph.venues) },
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
                    viewModel = viewModel { homeViewModel(graph) },
                )
            }
        }
        // The title cards between the home and the journey (spec 3.25, 3.27): the player moves from one to the other.
        composable(SPLASH_AWAY_ROUTE) {
            SplashRoute(SplashKind.AWAY, onDone = { navController.navigateWithinTheGame(JOURNEY_ROUTE) }, viewModel = viewModel { homeViewModel(graph) })
        }
        composable(SPLASH_HOME_ROUTE) {
            SplashRoute(SplashKind.HOME, onDone = { navController.navigateWithinTheGame(HOME_ROUTE) }, viewModel = viewModel { homeViewModel(graph) })
        }
    }
}

private fun pieceViewModel(graph: IosGraph, savedState: SavedStateHandle) = PieceViewModel(
    savedState, graph.repertoire, graph.sheetFiles, graph.repertoireConfig, graph.clock, graph.takes(), graph.configSource, graph.sessions,
    graph.videoFiles, graph.videoImporter, graph.shareFiles, graph.backings, graph.backingFiles, graph.backingPcm, graph.recordingRate,
    graph.backingImporter, IosBackingPreview(), graph.audioRoutes, graph.io,
)

private fun shareViewModel(graph: IosGraph, texts: IosTexts) = ShareViewModel(
    graph.sessions, graph.repertoire, graph.sound, graph.audioFiles, graph.shareFiles, graph.renderer, texts.share, graph.renderSpeed,
    graph.elapsed, graph.soundConfig, graph.videoFiles, graph.backings, graph.backingPcm,
)

private fun homeViewModel(graph: IosGraph) = HomeViewModel(graph.home, graph.journey, graph.clock, graph.venues)

private fun openAppSettings() {
    NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
}

/** Switches tabs without stacking copies and keeps each tab's state; «Занятия» is the root of the tabs (spec 3.25). */
internal fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(TopLevelDestination.START.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Between the home and the journey (spec 3.25, 3.27): a move, not a step deeper — «назад» leads to «Занятия». */
private fun NavHostController.navigateWithinTheGame(route: String) {
    navigate(route) {
        popUpTo(TopLevelDestination.START.route)
        launchSingleTop = true
    }
}

/** «Играть здесь» (spec 3.27): the home, the journey and the stop are folded first, or «Занятия» would open on them. */
private fun NavHostController.navigateToLiveLeavingTheGame() {
    popBackStack(TopLevelDestination.START.route, inclusive = false)
    navigateToTopLevel(TopLevelDestination.LIVE)
}

private fun NavHostController.navigateToSession(sessionId: Long) {
    navigate("$SESSION_ROUTE/$sessionId") { launchSingleTop = true }
}

/** [sessionId] null opens the sound of all recordings. */
private fun NavHostController.navigateToSound(sessionId: Long?) {
    navigate("$SOUND_ROUTE?${SoundViewModel.ARG_SESSION_ID}=${sessionId ?: SoundViewModel.EVERYONE}") { launchSingleTop = true }
}

private fun NavHostController.navigateToPiece(pieceId: Long) {
    navigate("$PIECE_ROUTE/$pieceId") { launchSingleTop = true }
}

/** Opens the music stand of a piece at [pageIndex] (from zero). */
private fun NavHostController.navigateToStand(pieceId: Long, pageIndex: Int) {
    navigate("$STAND_ROUTE/$pieceId?${StandViewModel.ARG_PAGE}=$pageIndex") { launchSingleTop = true }
}

private fun NavHostController.navigateToBackup() {
    navigate(BACKUP_ROUTE) { launchSingleTop = true }
}

/** [uri] is the file Files came back with; blank — a restore that is on its way already is come back to. */
private fun NavHostController.navigateToRestore(uri: String) {
    navigate(if (uri.isBlank()) RESTORE_ROUTE else "$RESTORE_ROUTE?${RestoreViewModel.ARG_URI}=${encodeQuery(uri)}") { launchSingleTop = true }
}

private fun NavHostController.navigateToSettings() {
    navigate(SETTINGS_ROUTE) { launchSingleTop = true }
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

private fun NavHostController.navigateToSection(section: SectionRef) {
    navigate("$SECTION_ROUTE/${SectionKeys.keyOf(section)}") { launchSingleTop = true }
}

private fun NavHostController.navigateToPieceForm(pieceId: Long?, focusNotes: Boolean = false, section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES)) {
    val id = pieceId ?: PieceFormViewModel.NEW_PIECE
    navigate(
        "$PIECE_FORM_ROUTE?${PieceFormViewModel.ARG_PIECE_ID}=$id&${PieceFormViewModel.ARG_FOCUS_NOTES}=$focusNotes" +
            "&${PieceFormViewModel.ARG_SECTION}=${SectionKeys.keyOf(section)}",
    ) { launchSingleTop = true }
}

private fun NavHostController.navigateToScaleForm(pieceId: Long?) {
    navigate("$SCALE_FORM_ROUTE?${ScaleFormViewModel.ARG_PIECE_ID}=${pieceId ?: ScaleFormViewModel.NEW_SCALE}") { launchSingleTop = true }
}

/** An element is gone with its form: back to the list of its section, or to the tab if it was opened from elsewhere. */
private fun NavHostController.popUpToSection() {
    if (!popBackStack(SECTION_PATTERN, inclusive = false)) popBackStack(TopLevelDestination.HISTORY.route, inclusive = false)
}

/** A value inside the query of a route: everything but the unreserved characters percent-encoded, as `Uri.encode` does. */
private fun encodeQuery(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val c = byte.toInt().toChar()
        if (byte >= 0 && (c.isLetterOrDigit() || c in UNRESERVED)) append(c) else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
    }
}

private const val UNRESERVED = "-_.~"
