package com.example.violintuner.feature.session

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.violintuner.feature.session.components.VideoSurfaceCallbacks
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

    // The video over the whole screen is watched, not touched: the screen must not dim under it while it plays (spec 3.19).
    val loaded = state as? SessionState.Loaded
    if (loaded?.fullscreen == true && loaded.player?.playing == true) {
        val activity = LocalActivity.current
        DisposableEffect(activity) {
            val window = activity?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    SessionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        videoSurface = remember(viewModel) { VideoSurfaceCallbacks(viewModel::attachSurface, viewModel::detachSurface) },
    )
    ShareHost(shareViewModel)
}
