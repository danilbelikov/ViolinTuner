package com.violinjourney.app.navigation

import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

@HiltViewModel
class HiltAppStartViewModel @Inject constructor(
    repository: SettingsRepository,
    sessions: SessionRepository,
    runningPractice: RunningPracticeStore,
    finisher: PracticeFinisher,
    config: PracticeConfig,
    clock: WallClock,
    practice: PracticeRepository,
    trophies: TrophyRepository,
    awarder: TrophyAwarder,
    profile: ProfileRepository,
    avatarFiles: AvatarFiles,
    repertoire: RepertoireRepository,
    waveforms: SessionWaveforms,
    shareFiles: ShareFiles,
    blocks: BlockStore,
    backings: BackingRepository,
    backingPcm: BackingPcm,
    @IoDispatcher io: CoroutineDispatcher,
) : AppStartViewModel(
    repository, sessions, runningPractice, finisher, config, clock, practice, trophies, awarder, profile, avatarFiles,
    repertoire, waveforms, shareFiles, blocks, backings, backingPcm, io,
)
