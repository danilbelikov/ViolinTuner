package com.example.violintuner

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.practice.components.PracticePromptHost
import com.example.violintuner.navigation.AppBottomBar
import com.example.violintuner.navigation.AppNavHost
import com.example.violintuner.navigation.AppStartViewModel
import com.example.violintuner.navigation.TopLevelDestination
import com.example.violintuner.navigation.navigateToTopLevel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ViolinTheme { ViolinTunerRoot() }
        }
    }
}

@Composable
private fun ViolinTunerRoot() {
    val startViewModel = hiltViewModel<AppStartViewModel>()
    val startRoute by startViewModel.startRoute.collectAsStateWithLifecycle()
    val practiceRunning by startViewModel.practiceRunning.collectAsStateWithLifecycle()
    val practicePrompt by startViewModel.practicePrompt.collectAsStateWithLifecycle()

    // "When the app is opened" (spec 3.12): the first start and every return from the background.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { startViewModel.onAppOpened() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // null outside the tabs: on the onboarding there is no bottom bar
    val currentTab = TopLevelDestination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    }

    // Landscape Live is the music-stand view of the handoff: no navigation bar, all height goes
    // to the ring. The other tabs keep a compact bar, otherwise there would be no way back but the gesture.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bottomBarTab = currentTab?.takeUnless { landscape && it == TopLevelDestination.LIVE }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // safeDrawing also covers the display cutout, which sits on a side in landscape
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
        // Until the stored settings are read there is only the dark surface: neither the
        // onboarding nor Live may flash for a frame on the wrong kind of start.
        startRoute?.let { route ->
            AppNavHost(
                navController = navController,
                startRoute = route,
                modifier = Modifier.padding(innerPadding),
            )
        }
        PracticePromptHost(
            prompt = practicePrompt,
            stepMinutes = PracticeConfig().editStepMinutes,
            onIntent = startViewModel::onPromptIntent,
        )
    }
}
