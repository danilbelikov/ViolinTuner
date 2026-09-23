package com.violinjourney.app.feature.journey

import androidx.activity.compose.BackHandler
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
import com.violinjourney.app.feature.home.HomeLookViewModel

/** Which of the journey's three views of the same state a route shows. */
enum class JourneyView { MAIN, MAP, PASSPORT }

@Composable
fun JourneyRoute(
    view: JourneyView,
    onOpenMap: () -> Unit,
    onOpenPassport: () -> Unit,
    onOpenStop: (stopId: String) -> Unit,
    onOpenLive: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val currentOnOpenPassport by rememberUpdatedState(onOpenPassport)
    val currentOnOpenStop by rememberUpdatedState(onOpenStop)
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnClose by rememberUpdatedState(onClose)
    val reduce = rememberAnimationsRemoved()

    LaunchedEffect(viewModel, reduce) { viewModel.onIntent(JourneyIntent.ReduceMotionChanged(reduce)) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    JourneyEffect.Close -> currentOnClose()
                    JourneyEffect.OpenMap -> currentOnOpenMap()
                    JourneyEffect.OpenPassport -> currentOnOpenPassport()
                    is JourneyEffect.OpenStop -> currentOnOpenStop(effect.stopId)
                    JourneyEffect.OpenLive -> currentOnOpenLive()
                }
            }
        }
    }
    // The road has no way out: it is two seconds, and the leg is already paid
    BackHandler(enabled = state.phase is JourneyPhase.Road) {}
    val homeLook by hiltViewModel<HomeLookViewModel>().state.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalReduceMotion provides reduce, LocalHomeLook provides homeLook) {
        when (view) {
            JourneyView.MAIN -> JourneyScreen(state, viewModel::onIntent, modifier)
            JourneyView.MAP -> MapScreen(state, viewModel::onIntent, modifier)
            JourneyView.PASSPORT -> PassportScreen(state, viewModel::onIntent, modifier)
        }
    }
}

@Composable
fun StopRoute(onClose: () -> Unit, onOpenLive: () -> Unit, onOpenHome: () -> Unit, modifier: Modifier = Modifier, viewModel: StopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenHome by rememberUpdatedState(onOpenHome)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    StopEffect.Close -> currentOnClose()
                    StopEffect.OpenLive -> currentOnOpenLive()
                    StopEffect.OpenHome -> currentOnOpenHome()
                }
            }
        }
    }
    BackHandler(enabled = state.fullscreen) { viewModel.onIntent(StopIntent.FullscreenClosed) }
    CompositionLocalProvider(LocalReduceMotion provides rememberAnimationsRemoved()) {
        StopScreen(state, viewModel::onIntent, modifier)
    }
}
