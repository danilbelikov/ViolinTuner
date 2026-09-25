package com.violinjourney.app.feature.session

import androidx.compose.runtime.Composable
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.core.ui.components.LocalMessages
import org.jetbrains.compose.resources.getString
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.feature.session.components.VideoSurfaceCallbacks
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.best_marked
import com.violinjourney.app.shared.resources.best_marked_moved

@Composable
fun SessionRoute(
    onClose: () -> Unit,
    onOpenSound: (sessionId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel,
    /** «Поделиться» (spec 3.17): the platform prepares the file and hands it to other apps. */
    onShare: (sessionId: Long) -> Unit,
    /** Where sharing shows how it goes; drawn over the screen. */
    shareHost: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val messages = LocalMessages.current
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
                    is SessionEffect.Share -> onShare(effect.sessionId)
                    // A toast, like every short message of the app; the handoff draws a snackbar (docs/plan-records2.md).
                    is SessionEffect.ShowBestMarked ->
                        messages.show(getString(if (effect.moved) Res.string.best_marked_moved else Res.string.best_marked))
                }
            }
        }
    }

    // The video over the whole screen is watched, not touched: the screen must not dim under it while it plays (spec 3.19).
    val loaded = state as? SessionState.Loaded
    if (loaded?.fullscreen == true && loaded.player?.playing == true) {
        KeepScreenOn()
    }

    SessionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        videoSurface = remember(viewModel) { VideoSurfaceCallbacks(viewModel::attachSurface, viewModel::detachSurface) },
    )
    shareHost()
}
