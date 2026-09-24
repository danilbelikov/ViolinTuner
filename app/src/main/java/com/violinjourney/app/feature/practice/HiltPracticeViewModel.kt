package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** PracticeViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltPracticeViewModel @Inject constructor(
    repository: PracticeRepository,
    runningStore: RunningPracticeStore,
    finisher: PracticeFinisher,
    sessions: SessionRepository,
    config: PracticeConfig,
    repertoire: RepertoireRepository,
    clock: WallClock,
    trophies: TrophyRepository,
    profiles: ProfileRepository,
    avatarFiles: AvatarFiles,
    progressConfig: ProgressConfig,
    journey: JourneyRepository,
    venues: Venues,
    blocks: BlockStore,
    journeyConfig: JourneyConfig,
    finishAsk: FinishPracticeAsk,
    analytics: Analytics,
) : PracticeViewModel(repository, runningStore, finisher, sessions, config, repertoire, clock, trophies, profiles, avatarFiles, progressConfig, journey, venues, blocks, journeyConfig, finishAsk, analytics)
