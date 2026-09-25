package com.violinjourney.app.feature.camera

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.RecordingRate
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.settings.IntonationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@HiltViewModel
class HiltCaptureViewModel @Inject constructor(
    savedState: SavedStateHandle,
    takes: TakePipeline,
    configSource: IntonationConfigSource,
    repertoire: RepertoireRepository,
    backings: BackingRepository,
    backingPcm: BackingPcm,
    routes: AudioRoutes,
    videos: VideoFiles,
    backingConfig: BackingConfig,
    cameraFactory: ShotCameraFactory,
    recordingRate: RecordingRate,
    muxer: VideoMux,
    @IoDispatcher io: CoroutineDispatcher,
) : CaptureViewModel(
    savedState, takes, configSource, repertoire, backings, backingPcm, routes, videos, backingConfig, cameraFactory, recordingRate, muxer, io,
)
