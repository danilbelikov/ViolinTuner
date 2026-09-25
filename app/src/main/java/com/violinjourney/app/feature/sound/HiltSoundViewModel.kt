package com.violinjourney.app.feature.sound

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltSoundViewModel @Inject constructor(
    savedState: SavedStateHandle,
    sound: SoundRepository,
    sessions: SessionRepository,
    repertoire: RepertoireRepository,
    audioFiles: SessionAudioFiles,
    playerFactory: SessionPlayerFactory,
    waveforms: SessionWaveforms,
    config: SoundConfig,
    backings: BackingRepository,
    backingPcm: BackingPcm,
    backingConfig: BackingConfig,
) : SoundViewModel(savedState, sound, sessions, repertoire, audioFiles, playerFactory, waveforms, config, backings, backingPcm, backingConfig)
