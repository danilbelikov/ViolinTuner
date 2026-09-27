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
import com.violinjourney.app.feature.camera.CaptureRoute
import com.violinjourney.app.feature.camera.CaptureViewModel
import com.violinjourney.app.feature.camera.IosShotCamera
import com.violinjourney.app.feature.camera.IosVideoMux
import com.violinjourney.app.feature.camera.ShotCameraFactory
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
import com.violinjourney.app.navigation.Routes
import com.violinjourney.app.navigation.TopLevelDestination
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * The graph of screens, as `AppNavHost` on Android: the same routes ([Routes]) and the same moves between them.
 */
@Composable
internal fun IosNavHost(graph: IosGraph, texts: IosTexts, navController: NavHostController, startRoute: String, modifier: Modifier) {
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
                    LiveViewModel(
                        graph.takes(), graph.configSource, graph.runningPractice, graph.clock, graph.venues, graph.analytics, graph.finishAsk,
                        graph.practiceConfig,
                    )
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
                onOpenJourney = { navController.navigate(Routes.JOURNEY) { launchSingleTop = true } },
                onOpenHome = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                onOpenSettings = navController::navigateToSettings,
                viewModel = viewModel {
                    PracticeViewModel(
                        graph.practice, graph.runningPractice, graph.finisher, graph.sessions, graph.practiceConfig, graph.repertoire,
                        graph.clock, graph.trophies, graph.profiles, graph.avatarFiles, graph.progressConfig, graph.journey, graph.venues,
                        graph.blockStore, graph.journeyConfig, graph.finishAsk, graph.analytics, graph.backings,
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
                    HistoryViewModel(graph.sessions, graph.repertoire, graph.intonationConfig, graph.clock, graph.audioFiles, graph.sectionAsk, graph.backings)
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
            route = Routes.SESSION_PATTERN,
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
            route = Routes.SOUND_PATTERN,
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
                        graph.waveforms, graph.soundConfig, graph.backings, graph.backingPcm, graph.backingConfig, graph.io,
                    )
                },
                onShare = share::start,
                shareHost = { ShareHost(share) },
            )
        }
        // The repertoire (spec 3.15): a piece and its music stand, above the tabs.
        composable(route = Routes.PIECE_PATTERN, arguments = listOf(navArgument(PieceViewModel.ARG_PIECE_ID) { type = NavType.LongType })) {
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
                onOpenCapture = navController::navigateToCapture,
            )
        }
        // «Снять под минусовку» (spec 3.32): the app's own camera, over everything
        composable(
            route = Routes.CAPTURE_PATTERN,
            arguments = listOf(navArgument(CaptureViewModel.ARG_PIECE_ID) { type = NavType.LongType }),
        ) {
            CaptureRoute(
                onClose = navController::popBackStack,
                viewModel = viewModel {
                    CaptureViewModel(
                        createSavedStateHandle(), graph.takes(), graph.configSource, graph.repertoire, graph.backings, graph.backingPcm,
                        graph.audioRoutes, graph.videoFiles, graph.backingConfig, ShotCameraFactory(::IosShotCamera), graph.recordingRate,
                        IosVideoMux, graph.io,
                    )
                },
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
            // The stand only opens from its piece, which lies right under it; the take belongs to that view model (spec 3.15).
            val pieceEntry = remember(entry) { navController.getBackStackEntry(Routes.PIECE_PATTERN) }
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
        composable(route = Routes.SECTION_PATTERN, arguments = listOf(navArgument(RepertoireViewModel.ARG_SECTION) { type = NavType.StringType })) {
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
                onCloseDeleted = { navController.popUpToSection() },
                viewModel = viewModel { PieceFormViewModel(createSavedStateHandle(), graph.repertoire, graph.repertoireConfig, graph.clock) },
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
                viewModel = viewModel {
                    ScaleFormViewModel(createSavedStateHandle(), graph.repertoire, graph.repertoireConfig, graph.clock, texts.scale)
                },
            )
        }
        // «Настройки» (spec 3.8, 4): the gear of Live opens them above the tabs, without the bottom bar.
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onOpenOnboarding = navController::navigateToOnboarding,
                onOpenSound = { navController.navigateToSound(null) },
                onClose = navController::popBackStack,
                // iOS keeps the language of each app in its Settings, on the app's own page
                onLanguageClick = ::openAppSettings,
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
        composable(Routes.BACKUP) {
            BackupRoute(
                onClose = navController::popBackStack,
                viewModel = viewModel { BackupViewModel(graph.backupManager, graph.backupStore, graph.backupConfig, graph.recordingWatch, graph.videoImporter) },
            )
        }
        composable(
            route = Routes.RESTORE_PATTERN,
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
        mapOf(Routes.JOURNEY to JourneyView.MAIN, Routes.JOURNEY_MAP to JourneyView.MAP, Routes.JOURNEY_PASSPORT to JourneyView.PASSPORT).forEach { (route, view) ->
            composable(route) {
                JourneyRoute(
                    view = view,
                    onOpenMap = { navController.navigate(Routes.JOURNEY_MAP) { launchSingleTop = true } },
                    onOpenPassport = { navController.navigate(Routes.JOURNEY_PASSPORT) { launchSingleTop = true } },
                    // home is a section of its own (spec 3.24); the way into it goes through its title card (3.25)
                    onOpenStop = { stopId -> navController.navigate(Routes.stop(stopId)) { launchSingleTop = true } },
                    onOpenLive = navController::navigateToLiveLeavingTheGame,
                    onClose = navController::popBackStack,
                    viewModel = viewModel { JourneyViewModel(graph.journey, graph.clock, graph.venues) },
                    homeLookViewModel = viewModel { HomeLookViewModel(graph.home) },
                )
            }
        }
        composable(route = Routes.JOURNEY_STOP_PATTERN, arguments = listOf(navArgument(StopViewModel.ARG_STOP_ID) { type = NavType.StringType })) {
            StopRoute(
                onClose = navController::popBackStack,
                onOpenLive = navController::navigateToLiveLeavingTheGame,
                onOpenHome = { navController.navigate(Routes.SPLASH_HOME) { launchSingleTop = true } },
                viewModel = viewModel { StopViewModel(createSavedStateHandle(), graph.journey, graph.journeyConfig, graph.clock, graph.venues) },
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
                    viewModel = viewModel { homeViewModel(graph) },
                )
            }
        }
        // The title cards between the home and the journey (spec 3.25, 3.27): the player moves from one to the other.
        composable(Routes.SPLASH_AWAY) {
            SplashRoute(SplashKind.AWAY, onDone = { navController.navigateWithinTheGame(Routes.JOURNEY) }, viewModel = viewModel { homeViewModel(graph) })
        }
        composable(Routes.SPLASH_HOME) {
            SplashRoute(SplashKind.HOME, onDone = { navController.navigateWithinTheGame(Routes.HOME) }, viewModel = viewModel { homeViewModel(graph) })
        }
    }
}

private fun pieceViewModel(graph: IosGraph, savedState: SavedStateHandle) = PieceViewModel(
    savedState, graph.repertoire, graph.sheetFiles, graph.repertoireConfig, graph.clock, graph.takes(), graph.configSource, graph.sessions,
    graph.videoFiles, graph.videoImporter, graph.shareFiles, graph.backings, graph.backingFiles, graph.backingPcm, graph.recordingRate,
    graph.backingImporter, IosBackingPreview(), graph.audioRoutes, graph.backingConfig, graph.io,
)

private fun shareViewModel(graph: IosGraph, texts: IosTexts) = ShareViewModel(
    graph.sessions, graph.repertoire, graph.sound, graph.audioFiles, graph.shareFiles, graph.renderer, texts.share, graph.renderSpeed,
    graph.elapsed, graph.soundConfig, graph.videoFiles, graph.backings, graph.backingPcm, graph.analytics, graph.io,
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
    navigate(Routes.session(sessionId)) { launchSingleTop = true }
}

/** [sessionId] null opens the sound of all recordings. */
private fun NavHostController.navigateToSound(sessionId: Long?) {
    navigate(Routes.sound(sessionId)) { launchSingleTop = true }
}

private fun NavHostController.navigateToPiece(pieceId: Long) {
    navigate(Routes.piece(pieceId)) { launchSingleTop = true }
}

/** Opens the music stand of a piece at [pageIndex] (from zero). */
private fun NavHostController.navigateToStand(pieceId: Long, pageIndex: Int) {
    navigate(Routes.stand(pieceId, pageIndex)) { launchSingleTop = true }
}

private fun NavHostController.navigateToBackup() {
    navigate(Routes.BACKUP) { launchSingleTop = true }
}

/** [uri] is the file Files came back with; blank — a restore that is on its way already is come back to. */
private fun NavHostController.navigateToRestore(uri: String) {
    navigate(Routes.restore(uri)) { launchSingleTop = true }
}

private fun NavHostController.navigateToSettings() {
    navigate(Routes.SETTINGS) { launchSingleTop = true }
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
    navigate(Routes.section(section)) { launchSingleTop = true }
}

/** «Снять под минусовку» (spec 3.32): the app's own camera for a take of [pieceId]. */
private fun NavHostController.navigateToCapture(pieceId: Long) {
    navigate(Routes.capture(pieceId)) { launchSingleTop = true }
}

/** [pieceId] null opens the form of a new element of [section]. */
private fun NavHostController.navigateToPieceForm(pieceId: Long?, focusNotes: Boolean = false, section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES)) {
    navigate(Routes.pieceForm(pieceId, focusNotes, section)) { launchSingleTop = true }
}

/** [pieceId] null opens the form of a new scale. */
private fun NavHostController.navigateToScaleForm(pieceId: Long?) {
    navigate(Routes.scaleForm(pieceId)) { launchSingleTop = true }
}

/** An element is gone with its form: back to the list of its section, or to the tab if it was opened from elsewhere. */
private fun NavHostController.popUpToSection() {
    if (!popBackStack(Routes.SECTION_PATTERN, inclusive = false)) popBackStack(TopLevelDestination.HISTORY.route, inclusive = false)
}
