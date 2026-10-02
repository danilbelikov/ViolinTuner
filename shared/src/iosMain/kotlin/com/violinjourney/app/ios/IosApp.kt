package com.violinjourney.app.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.violinjourney.app.core.ui.analytics.AnalyticsViewModel
import com.violinjourney.app.core.ui.components.DockPlace
import com.violinjourney.app.core.ui.components.LocalDockPlace
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.components.Messages
import com.violinjourney.app.core.ui.components.ToastLift
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinAppTheme
import com.violinjourney.app.feature.practice.components.LocalAppPromptShown
import com.violinjourney.app.feature.practice.components.PracticePromptHost
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.AppStartViewModel
import com.violinjourney.app.navigation.LocalTabBarLight
import com.violinjourney.app.navigation.ONBOARDING_ROUTE
import com.violinjourney.app.navigation.TabBarLight
import com.violinjourney.app.navigation.TabsFrame
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.manrope_variable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The iOS app as a whole — what `MainActivity` is on Android: the tabs «Занятия · Live · Репертуар · Записи» over the screens,
 * the forgotten-practice prompt over everything, the short words of the screens in a toast of its own.
 */
@Composable
internal fun IosApp(graph: IosGraph, texts: IosTexts, openRoute: String? = null) {
    val start = viewModel {
        AppStartViewModel(
            repository = graph.settings, runningPractice = graph.runningPractice, finisher = graph.finisher, config = graph.practiceConfig,
            clock = graph.clock, practice = graph.practice, awarder = graph.awarder, repertoire = graph.repertoire,
            housekeeping = graph.housekeeping, blocks = graph.blockStore, events = graph.events,
        )
    }
    val startRoute by start.startRoute.collectAsStateWithLifecycle()
    val practiceRunning by start.practiceRunning.collectAsStateWithLifecycle()
    val practicePrompt by start.practicePrompt.collectAsStateWithLifecycle()
    // read only by the parts of «Занятие не закончено» that show the numbers: a minute changes them, not the root
    val promptEndings = start.promptEndings.collectAsStateWithLifecycle()
    // "When the app is opened" (spec 3.12): the first start and every return from the background.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { start.onAppOpened() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { start.onAppStopped() }

    var message by remember { mutableStateOf<Toast?>(null) }
    val messages = remember { Messages { text -> message = Toast(text) } }
    // how bright the tab bar is: Live lends it its light behind LiveSwitches.DIM_TAB_BAR, off by default (spec 3.36.6)
    val tabBarLight = remember { TabBarLight() }
    // where the bottom zone of the screen is: the message stands above it (spec 5.29)
    val dockPlace = remember { DockPlace() }

    val navController = rememberNavController()
    // which screen was opened (spec 3.34), the route cut to its name as on Android
    val tracking = viewModel { AnalyticsViewModel(graph.analytics) }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { opened -> tracking.onScreenOpened(opened.id, opened.destination.route) }
    }
    val entry by navController.currentBackStackEntryAsState()
    // null outside the tabs: the onboarding and the screens above the tabs have no bottom bar
    val currentTab = TopLevelDestination.entries.firstOrNull { destination ->
        entry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }

    ViolinAppTheme(fontFamily = manrope()) {
        // «Занятие не закончено» lies over the gift of «Занятия» (spec 3.36.3): the gift waits while the prompt is shown
        CompositionLocalProvider(
            LocalMessages provides messages,
            LocalTabBarLight provides tabBarLight,
            LocalDockPlace provides dockPlace,
            LocalAppPromptShown provides (practicePrompt != null),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                // Landscape Live is the music-stand view: no bar, all height to the ring; the other tabs keep a compact one.
                val landscape = maxWidth > maxHeight
                val bottomBarTab = currentTab?.takeUnless { landscape && it == TopLevelDestination.LIVE }
                // as Scaffold, but the screens lie over the bar: the glow of «Начать занятие» falls on it (spec 3.36.2)
                TabsFrame(
                    // the keyboard is the business of the fields that bring it — forms pad themselves, dialogs sit above it —
                    // as on Android, where a dialog's keyboard never reaches the screen behind it
                    contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
                    bottomBar = {
                        if (bottomBarTab != null) {
                            AppBottomBar(
                                current = bottomBarTab,
                                onSelect = navController::navigateToTopLevel,
                                practiceRunning = practiceRunning,
                                compact = landscape,
                                dimmed = tabBarLight::alpha,
                            )
                        }
                    },
                ) { innerPadding ->
                    // Until the stored settings are read there is only the dark surface (spec 3.7).
                    startRoute?.let { route ->
                        // consumed as well as padded: a screen asking for the bars or the keyboard again gets only what is left
                        IosNavHost(graph, texts, navController, route, Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))
                        // `-openRoute live` of the launch: straight to a screen, for checks by screenshot
                        LaunchedEffect(openRoute) {
                            if (openRoute == null || route == ONBOARDING_ROUTE) return@LaunchedEffect
                            val tab = TopLevelDestination.entries.firstOrNull { it.route == openRoute }
                            if (tab != null) navController.navigateToTopLevel(tab) else navController.navigate(openRoute)
                        }
                    }
                    PracticePromptHost(
                        prompt = practicePrompt,
                        endings = { promptEndings.value },
                        stepMinutes = start.promptStepMinutes,
                        onIntent = start::onPromptIntent,
                        effects = start.promptEffects,
                    )
                }
                ToastHost(message, dockPlace, onGone = { message = null })
            }
        }
    }
}

/** One short word on screen; a new one with the same text is still new. */
private class Toast(val text: String)

/**
 * What a toast is on Android: a plate low on the screen, for two seconds, touching nothing — in the colour of a dialog, the words of
 * the first level of text (spec 3.36.1, 5.29). 12 dp over the bottom zone of the screen ([DockPlace]), never on its main button;
 * with no zone, or with the zone under the keyboard, 96 dp over the bottom inset — over the keyboard when it is up. The height is
 * read while placing: the root is not recomposed for it.
 */
@Composable
private fun ToastHost(toast: Toast?, dockPlace: DockPlace, onGone: () -> Unit) {
    var shown by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        shown = toast
        delay(TOAST_MS)
        shown = null
        onGone()
    }
    val safe = WindowInsets.safeDrawing
    Box(Modifier.fillMaxSize().windowInsetsPadding(safe.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = shown != null,
            modifier = Modifier.offset {
                IntOffset(0, -ToastLift.of(dockPlace.rise, safe.getBottom(this), ToastAboveDock.roundToPx(), ToastAboveInset.roundToPx()))
            },
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = shown?.text.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.L)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

private const val TOAST_MS = 2_000L
private val ToastAboveDock = 12.dp
private val ToastAboveInset = 96.dp

/** The language the resources speak: the first of the app's own languages the person prefers (spec 3.26). */
internal fun interfaceLanguageTag(): String = NSLocale.preferredLanguages.firstOrNull()?.toString() ?: "en"

internal fun useInterfaceLanguage() = Formats.use(interfaceLanguageTag())

/** Manrope from the variable TTF, one file for every weight — as the app does on Android from res/font. */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun manrope(): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    val fonts = weights.map { weight ->
        Font(
            resource = Res.font.manrope_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}
