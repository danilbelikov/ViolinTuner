package com.violinjourney.app.feature.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved

/** Entry point of the «Звук» screen — of a recording, or of all of them. */
@Composable
fun SoundRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoundViewModel,
    /** «Поделиться» (spec 3.17): the platform prepares the file and hands it to other apps. */
    onShare: (sessionId: Long) -> Unit,
    /** Where sharing shows how it goes; drawn over the screen. */
    shareHost: @Composable () -> Unit = {},
    /** True while the screen is only rebuilt (a rotation on Android): the sound plays on through it. */
    changingConfigurations: () -> Boolean = { false },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val meters = viewModel.meters.collectAsStateWithLifecycle()
    // a state, not a value, like the meters: read where the waveform is drawn, so the screen recomposes once a second
    val position = viewModel.position.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)

    // Leaving the screen stops the sound and stores what was set (spec 3.17); a rotation only rebuilds it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!changingConfigurations()) viewModel.onIntent(SoundIntent.ScreenStopped)
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SoundEffect.Close -> currentOnClose()
                    is SoundEffect.Share -> onShare(effect.sessionId)
                }
            }
        }
    }

    // the spinner of «Готовим минусовку…» stands still where the animations are removed
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        SoundScreen(
            state = state, meters = meters, onIntent = viewModel::onIntent, config = viewModel.config, backingConfig = viewModel.backingConfig,
            modifier = modifier, position = { position.value },
        )
    }
    shareHost()
}
