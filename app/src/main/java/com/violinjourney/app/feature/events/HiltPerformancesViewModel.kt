package com.violinjourney.app.feature.events

import com.violinjourney.app.core.di.DefaultDispatcher
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.events.performances.PerformancesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** PerformancesViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltPerformancesViewModel @Inject constructor(
    events: EventRepository,
    sessions: SessionRepository,
    repertoire: RepertoireRepository,
    videos: VideoFiles,
    config: EventsConfig,
    clock: WallClock,
    @DefaultDispatcher background: CoroutineDispatcher,
) : PerformancesViewModel(events, sessions, repertoire, videos, config, clock, background)
