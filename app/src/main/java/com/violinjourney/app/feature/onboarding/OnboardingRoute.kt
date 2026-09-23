package com.violinjourney.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester
import com.violinjourney.app.feature.backup.BACKUP_FILE_TYPES

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onRestore: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    // A refusal does not stop the onboarding and must not throw the player into the system
    // settings: Live explains and offers the permission again (spec 3.4).
    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = false) {
        viewModel.onIntent(OnboardingIntent.MicPermissionAnswered)
    }

    // On the first page back leaves the app, as the system does by default.
    BackHandler(enabled = OnboardingFlow.back(state.step) != null) {
        viewModel.onIntent(OnboardingIntent.BackPressed)
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    OnboardingEffect.RequestMicPermission -> requestMicPermission()
                    OnboardingEffect.Finished -> currentOnFinished()
                }
            }
        }
    }

    // On a new phone a copy is the first thing a person with one needs (spec 3.20): the system's «Открыть», then the restore screen.
    val copy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onRestore(it.toString()) } }
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        OnboardingScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, onHaveBackup = { copy.launch(BACKUP_FILE_TYPES) })
    }
}
