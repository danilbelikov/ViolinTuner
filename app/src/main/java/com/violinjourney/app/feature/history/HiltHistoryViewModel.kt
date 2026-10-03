package com.violinjourney.app.feature.history

import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.di.DefaultDispatcher
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

/** HistoryViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltHistoryViewModel @Inject constructor(
    repository: SessionRepository,
    repertoire: RepertoireRepository,
    config: IntonationConfig,
    clock: WallClock,
    audioFiles: SessionAudioFiles,
    backings: BackingRepository,
    events: EventRepository,
    eventsConfig: EventsConfig,
    @DefaultDispatcher background: CoroutineDispatcher,
) : HistoryViewModel(repository, repertoire, config, clock, audioFiles, backings, events, eventsConfig, background)
