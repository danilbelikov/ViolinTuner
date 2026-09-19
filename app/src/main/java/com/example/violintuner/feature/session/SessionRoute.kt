package com.example.violintuner.feature.session

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
import com.example.violintuner.feature.share.ShareHost
import com.example.violintuner.feature.share.ShareViewModel

@Composable
fun SessionRoute(
    onClose: () -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = hiltViewModel(),
    shareViewModel: ShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenSound by rememberUpdatedState(onOpenSound)

    // Leaving the screen, the app going to the background: the sound stops (spec 3.10).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(SessionIntent.ScreenStopped) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SessionEffect.Close -> currentOnClose()
                    is SessionEffect.OpenSound -> currentOnOpenSound(effect.sessionId)
                    is SessionEffect.Share -> shareViewModel.start(effect.sessionId)
                }
            }
        }
    }

    SessionScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
    ShareHost(shareViewModel)
}
