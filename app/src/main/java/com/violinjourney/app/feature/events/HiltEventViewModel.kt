package com.violinjourney.app.feature.events

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.settings.videoQuality
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.events.screen.EventViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltEventViewModel @Inject constructor(
    savedState: SavedStateHandle,
    events: EventRepository,
    sessions: SessionRepository,
    repertoire: RepertoireRepository,
    configSource: IntonationConfigSource,
    takes: TakePipeline,
    importer: VideoTakeImporter,
    audioImporter: AudioTakeImporter,
    videos: VideoFiles,
    shareFiles: ShareFiles,
    config: EventsConfig,
    repertoireConfig: RepertoireConfig,
    clock: WallClock,
    settings: SettingsRepository,
) : EventViewModel(
    savedState, events, sessions, repertoire, configSource, takes, importer, audioImporter, videos, shareFiles, config, repertoireConfig, clock,
    videoQuality = settings.videoQuality,
)
