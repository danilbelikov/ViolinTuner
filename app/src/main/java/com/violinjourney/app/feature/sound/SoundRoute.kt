package com.violinjourney.app.feature.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.feature.share.ShareHost
import com.violinjourney.app.feature.share.ShareViewModel

/** Entry point of the «Звук» screen — of a recording, or of all of them. */
@Composable
fun SoundRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoundViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val meters = viewModel.meters.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)

    // Leaving the screen stops the sound and stores what was set (spec 3.17).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(SoundIntent.ScreenStopped) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SoundEffect.Close -> currentOnClose()
                    is SoundEffect.Share -> shareViewModel.start(effect.sessionId)
                }
            }
        }
    }

    SoundScreen(state = state, meters = meters, onIntent = viewModel::onIntent, modifier = modifier)
    ShareHost(shareViewModel)
}
