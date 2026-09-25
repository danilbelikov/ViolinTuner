package com.violinjourney.app.feature.share

import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.recording.video.VideoFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltShareViewModel @Inject constructor(
    sessions: SessionRepository,
    repertoire: RepertoireRepository,
    sound: SoundRepository,
    audioFiles: SessionAudioFiles,
    files: ShareFiles,
    renderer: SoundRenderer,
    texts: ShareTexts,
    speed: RenderSpeed,
    clock: ElapsedClock,
    config: SoundConfig,
    videos: VideoFiles,
    backings: BackingRepository,
    backingPcm: BackingPcm,
) : ShareViewModel(sessions, repertoire, sound, audioFiles, files, renderer, texts, speed, clock, config, videos, backings, backingPcm)
