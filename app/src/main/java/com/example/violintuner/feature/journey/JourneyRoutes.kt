package com.example.violintuner.feature.journey

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
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.core.ui.motion.rememberAnimationsRemoved

/** Which of the journey's three views of the same state a route shows. */
enum class JourneyView { MAIN, MAP, PASSPORT }

@Composable
fun JourneyRoute(
    view: JourneyView,
    onOpenMap: () -> Unit,
    onOpenPassport: () -> Unit,
    onOpenStop: (stopId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JourneyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenMap by rememberUpdatedState(onOpenMap)
    val currentOnOpenPassport by rememberUpdatedState(onOpenPassport)
    val currentOnOpenStop by rememberUpdatedState(onOpenStop)
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
                }
            }
        }
    }
    // The road has no way out: it is two seconds, and the leg is already paid
    BackHandler(enabled = state.phase is JourneyPhase.Road) {}
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        when (view) {
            JourneyView.MAIN -> JourneyScreen(state, viewModel::onIntent, modifier)
            JourneyView.MAP -> MapScreen(state, viewModel::onIntent, modifier)
            JourneyView.PASSPORT -> PassportScreen(state, viewModel::onIntent, modifier)
        }
    }
}

@Composable
fun StopRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: StopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.closes.collect { currentOnClose() } }
    }
    StopScreen(state, viewModel::onIntent, modifier)
}
