package com.example.violintuner.feature.onboarding

import androidx.activity.compose.BackHandler
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
import com.example.violintuner.core.ui.permission.rememberMicPermissionRequester

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
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

    // On the first step back leaves the app, as the system does by default.
    BackHandler(enabled = state.step != OnboardingStep.MICROPHONE) {
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

    OnboardingScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
