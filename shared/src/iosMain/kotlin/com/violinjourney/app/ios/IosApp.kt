package com.violinjourney.app.ios

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.components.Messages
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinAppTheme
import com.violinjourney.app.feature.practice.components.PracticePromptHost
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.AppStartViewModel
import com.violinjourney.app.navigation.ONBOARDING_ROUTE
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.manrope_variable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The iOS app as a whole — what `MainActivity` is on Android: the tabs «Занятия · Live · Записи» over the screens,
 * the forgotten-practice prompt over everything, the short words of the screens in a toast of its own.
 */
@Composable
internal fun IosApp(graph: IosGraph, scaleTexts: IosScaleTexts, openRoute: String? = null) {
    val start = viewModel {
        AppStartViewModel(
            graph.settings, graph.sessions, graph.runningPractice, graph.finisher, graph.practiceConfig, graph.clock, graph.practice,
            graph.trophies, graph.awarder, graph.profiles, graph.avatarFiles, graph.repertoire, graph.waveforms, graph.shareFiles,
            graph.blockStore, graph.backings, graph.backingPcm, graph.io,
        )
    }
    val startRoute by start.startRoute.collectAsStateWithLifecycle()
    val practiceRunning by start.practiceRunning.collectAsStateWithLifecycle()
    val practicePrompt by start.practicePrompt.collectAsStateWithLifecycle()
    // "When the app is opened" (spec 3.12): the first start and every return from the background.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { start.onAppOpened() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { start.onAppStopped() }

    var message by remember { mutableStateOf<Toast?>(null) }
    val messages = remember { Messages { text -> message = Toast(text) } }

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    // null outside the tabs: the onboarding and the screens above the tabs have no bottom bar
    val currentTab = TopLevelDestination.entries.firstOrNull { destination ->
        entry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }

    ViolinAppTheme(fontFamily = manrope()) {
        CompositionLocalProvider(LocalMessages provides messages) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                // Landscape Live is the music-stand view: no bar, all height to the ring; the other tabs keep a compact one.
                val landscape = maxWidth > maxHeight
                val bottomBarTab = currentTab?.takeUnless { landscape && it == TopLevelDestination.LIVE }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (bottomBarTab != null) {
                            AppBottomBar(
                                current = bottomBarTab,
                                onSelect = navController::navigateToTopLevel,
                                practiceRunning = practiceRunning,
                                compact = landscape,
                            )
                        }
                    },
                ) { innerPadding ->
                    // Until the stored settings are read there is only the dark surface (spec 3.7).
                    startRoute?.let { route ->
                        IosNavHost(graph, scaleTexts, navController, route, Modifier.padding(innerPadding))
                        // `-openRoute live` of the launch: straight to a screen, for checks by screenshot
                        LaunchedEffect(openRoute) {
                            if (openRoute == null || route == ONBOARDING_ROUTE) return@LaunchedEffect
                            val tab = TopLevelDestination.entries.firstOrNull { it.route == openRoute }
                            if (tab != null) navController.navigateToTopLevel(tab) else navController.navigate(openRoute)
                        }
                    }
                    PracticePromptHost(prompt = practicePrompt, stepMinutes = graph.practiceConfig.editStepMinutes, onIntent = start::onPromptIntent)
                }
                ToastHost(message, onGone = { message = null })
            }
        }
    }
}

/** One short word on screen; a new one with the same text is still new. */
private class Toast(val text: String)

/** What a toast is on Android: a pill low on the screen, for two seconds, touching nothing. */
@Composable
private fun ToastHost(toast: Toast?, onGone: () -> Unit) {
    var shown by remember { mutableStateOf<Toast?>(null) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        shown = toast
        delay(TOAST_MS)
        shown = null
        onGone()
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = shown != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = shown?.text.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .background(Color(TOAST_BACKGROUND), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private const val TOAST_MS = 2_000L
private const val TOAST_BACKGROUND = 0xE6333333

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
