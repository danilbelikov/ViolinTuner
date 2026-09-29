package com.violinjourney.app.feature.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.motion.rememberAnimationsRemoved
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
    /** «К произведению» (spec 3.36.5): the screen of the piece — the one behind in the stack, if it is there. */
    onOpenPiece: (pieceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel,
    /** «Поделиться» (spec 3.17): the platform prepares the file and hands it to other apps. */
    onShare: (sessionId: Long) -> Unit,
    /** Where sharing shows how it goes; drawn over the screen. */
    shareHost: @Composable () -> Unit = {},
    /** True while the screen is only rebuilt (a rotation on Android): the sound plays on through it. */
    changingConfigurations: () -> Boolean = { false },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // a state, not a value: read where the cursor and the slider are drawn, so the screen itself recomposes once a second
    val position = viewModel.position.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val messages = LocalMessages.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenSound by rememberUpdatedState(onOpenSound)
    val currentOnOpenPiece by rememberUpdatedState(onOpenPiece)

    // Leaving the screen, the app going to the background: the sound stops (spec 3.10). A rotation only rebuilds it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!changingConfigurations()) viewModel.onIntent(SessionIntent.ScreenStopped)
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SessionEffect.Close -> currentOnClose()
                    is SessionEffect.OpenSound -> currentOnOpenSound(effect.sessionId)
                    is SessionEffect.OpenPiece -> currentOnOpenPiece(effect.pieceId)
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

    // «Убрать анимации» (5.29 R5): the spinner of «Готовим минусовку…» stands, the glyph of a tap on the picture shows without motion
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        SessionScreen(
            state = state,
            onIntent = viewModel::onIntent,
            modifier = modifier,
            videoSurface = remember(viewModel) { VideoSurfaceCallbacks(viewModel::attachSurface, viewModel::detachSurface) },
            position = { position.value },
        )
    }
    shareHost()
}
