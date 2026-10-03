package com.violinjourney.app.feature.events

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.events.form.EventFormViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** EventFormViewModel of the shared code, made by Hilt on Android: the same constructor. */
@HiltViewModel
class HiltEventFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    events: EventRepository,
    config: EventsConfig,
    clock: WallClock,
) : EventFormViewModel(savedState, events, config, clock)
