package com.violinjourney.app.feature.repertoire.piece

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.RecordingRate
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingFileImporter
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.backing.BackingPreview
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@HiltViewModel
class HiltPieceViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repertoire: RepertoireRepository,
    sheetFiles: SheetFiles,
    config: RepertoireConfig,
    clock: WallClock,
    takes: TakePipeline,
    configSource: IntonationConfigSource,
    sessions: SessionRepository,
    videos: VideoFiles,
    importer: VideoTakeImporter,
    shareFiles: ShareFiles,
    backings: BackingRepository,
    backingFiles: BackingFiles,
    backingPcm: BackingPcm,
    recordingRate: RecordingRate,
    backingImporter: BackingFileImporter,
    backingPreview: BackingPreview,
    routes: AudioRoutes,
    @IoDispatcher io: CoroutineDispatcher,
) : PieceViewModel(
    savedState, repertoire, sheetFiles, config, clock, takes, configSource, sessions, videos, importer, shareFiles, backings,
    backingFiles, backingPcm, recordingRate, backingImporter, backingPreview, routes, io,
)
