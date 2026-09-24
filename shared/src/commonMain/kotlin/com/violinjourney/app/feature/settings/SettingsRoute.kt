package com.violinjourney.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun SettingsRoute(
    onOpenOnboarding: () -> Unit,
    onOpenSound: () -> Unit,
    /** The language of the app, where the platform has a screen for it (Android 13+, iOS); null — the device's language only. */
    onLanguageClick: (() -> Unit)?,
    /** «Данные» (spec 3.20, 3.34): the copy and the statistics switch, with the platform's file pickers. */
    dataBlock: @Composable (analyticsEnabled: Boolean, onAnalyticsChange: (Boolean) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenOnboarding by rememberUpdatedState(onOpenOnboarding)
    val currentOnOpenSound by rememberUpdatedState(onOpenSound)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.OpenOnboarding -> currentOnOpenOnboarding()
                    SettingsEffect.OpenSound -> currentOnOpenSound()
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        onBack = onClose,
        dataBlock = { dataBlock(state.analyticsEnabled) { viewModel.onIntent(SettingsIntent.AnalyticsToggled(it)) } },
        onLanguageClick = onLanguageClick,
    )
}
