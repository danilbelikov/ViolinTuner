package com.example.violintuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.navigation.AppBottomBar
import com.example.violintuner.navigation.AppNavHost
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.entries.firstOrNull { destination ->
        backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    } ?: TopLevelDestination.START

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            AppBottomBar(
                current = current,
                onSelect = navController::navigateToTopLevel,
            )
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
