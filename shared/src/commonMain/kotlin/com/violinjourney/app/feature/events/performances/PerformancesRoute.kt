package com.violinjourney.app.feature.events.performances

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * The entry of «Выступления» (spec 3.35, 3.36.9): owns its view model and its effects. [onOpenEvent] — the screen of an event, «назад»
 * from it comes back here; [onAddPerformance] — the form of a new performance of today, whose «Сохранить» opens the screen of the new one
 * (its «назад» comes back here too).
 */
@Composable
fun PerformancesRoute(
    onBack: () -> Unit,
    onOpenEvent: (eventId: Long) -> Unit,
    onAddPerformance: () -> Unit,
    viewModel: PerformancesViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenEvent by rememberUpdatedState(onOpenEvent)
    val currentOnAddPerformance by rememberUpdatedState(onAddPerformance)
    // in sight again — back from an event, from the form, from the background: presses count anew
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onIntent(PerformancesIntent.Shown) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is PerformancesEffect.OpenEvent -> currentOnOpenEvent(effect.eventId)
                    PerformancesEffect.OpenNewPerformance -> currentOnAddPerformance()
                    PerformancesEffect.Close -> currentOnBack()
                }
            }
        }
    }
    PerformancesScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
