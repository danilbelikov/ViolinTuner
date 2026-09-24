package com.violinjourney.app.feature.journey

import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** JourneyViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltJourneyViewModel @Inject constructor(
    journey: JourneyRepository,
    clock: WallClock,
    venues: Venues,
) : JourneyViewModel(journey, clock, venues)
