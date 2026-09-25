package com.violinjourney.app.feature.session

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltSessionViewModel @Inject constructor(
    repository: SessionRepository,
    defaultConfig: IntonationConfig,
    audioFiles: SessionAudioFiles,
    playerFactory: SessionPlayerFactory,
    repertoire: RepertoireRepository,
    sound: SoundRepository,
    soundConfig: SoundConfig,
    pictureFactory: VideoPictureFactory,
    savedState: SavedStateHandle,
    backings: BackingRepository,
    backingPcm: BackingPcm,
) : SessionViewModel(repository, defaultConfig, audioFiles, playerFactory, repertoire, sound, soundConfig, pictureFactory, savedState, backings, backingPcm)
