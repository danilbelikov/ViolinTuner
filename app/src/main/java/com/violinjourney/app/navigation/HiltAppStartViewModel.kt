package com.violinjourney.app.navigation

import com.violinjourney.app.core.data.Housekeeping
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltAppStartViewModel @Inject constructor(
    repository: SettingsRepository,
    runningPractice: RunningPracticeStore,
    finisher: PracticeFinisher,
    config: PracticeConfig,
    clock: WallClock,
    practice: PracticeRepository,
    awarder: TrophyAwarder,
    repertoire: RepertoireRepository,
    housekeeping: Housekeeping,
    blocks: BlockStore,
    events: EventRepository,
) : AppStartViewModel(repository, runningPractice, finisher, config, clock, practice, awarder, repertoire, housekeeping, blocks, events)
