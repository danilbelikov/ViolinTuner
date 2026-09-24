package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeRepository
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** HomeViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltHomeViewModel @Inject constructor(
    home: HomeRepository,
    journey: JourneyRepository,
    clock: WallClock,
    venues: Venues,
) : HomeViewModel(home, journey, clock, venues)
