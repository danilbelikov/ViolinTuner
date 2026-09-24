package com.violinjourney.app.feature.onboarding

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
import com.violinjourney.app.core.ui.analytics.AnalyticsViewModel
import com.violinjourney.app.core.ui.permission.rememberMicPermissionRequester

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    /** «У меня есть копия» (spec 3.20, 3.33): the platform picks the file and opens the restore; null — no copies on this platform yet. */
    onHaveBackup: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel,
    tracking: AnalyticsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    // A refusal does not stop the onboarding and must not throw the player into the system
    // settings: Live explains and offers the permission again (spec 3.4).
    val requestMicPermission = rememberMicPermissionRequester(openSettingsWhenBlocked = false, onAnswer = tracking::onMicPermissionAnswered) {
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

    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        OnboardingScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, onHaveBackup = onHaveBackup)
    }
}
