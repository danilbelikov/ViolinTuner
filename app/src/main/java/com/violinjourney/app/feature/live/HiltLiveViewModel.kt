package com.violinjourney.app.feature.live

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltLiveViewModel @Inject constructor(
    takes: TakePipeline,
    configSource: IntonationConfigSource,
    runningPractice: RunningPracticeStore,
    clock: WallClock,
    venues: Venues,
    analytics: Analytics,
    finishAsk: FinishPracticeAsk,
) : LiveViewModel(takes, configSource, runningPractice, clock, venues, analytics, finishAsk)
