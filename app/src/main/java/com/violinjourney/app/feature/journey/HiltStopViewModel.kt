package com.violinjourney.app.feature.journey

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** StopViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltStopViewModel @Inject constructor(
    savedState: SavedStateHandle,
    journey: JourneyRepository,
    config: JourneyConfig,
    clock: WallClock,
    venues: Venues,
) : StopViewModel(savedState, journey, config, clock, venues)
