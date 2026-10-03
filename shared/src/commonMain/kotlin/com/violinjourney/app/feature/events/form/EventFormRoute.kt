package com.violinjourney.app.feature.events.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.datetime.LocalDate

/**
 * The entry of the form of an event (spec 3.35, 3.36.9): owns its view model and its effects. [onOpenCreated] — a new event: its screen
 * takes the place of the form, «Занятия» told the date it lies on (plan D24); [onSaved] — an edit: back to the screen of the event, «Занятия»
 * told the date as well; [onClose] — nothing saved.
 */
@Composable
fun EventFormRoute(
    onClose: () -> Unit,
    onOpenCreated: (eventId: Long, date: LocalDate) -> Unit,
    onSaved: (date: LocalDate) -> Unit,
    viewModel: EventFormViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenCreated by rememberUpdatedState(onOpenCreated)
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    EventFormEffect.Close -> currentOnClose()
                    is EventFormEffect.OpenCreated -> currentOnOpenCreated(effect.eventId, effect.date)
                    is EventFormEffect.Saved -> currentOnSaved(effect.date)
                }
            }
        }
    }
    EventFormScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
