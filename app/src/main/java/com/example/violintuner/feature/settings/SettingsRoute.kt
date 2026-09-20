package com.example.violintuner.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.feature.backup.DataBlock

@Composable
fun SettingsRoute(
    onOpenOnboarding: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenRestore: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
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

    SettingsScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier) {
        DataBlock(onOpenBackup = onOpenBackup, onOpenRestore = onOpenRestore)
    }
}
