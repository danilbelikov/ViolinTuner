package com.violinjourney.app.feature.live.block

import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.history.HistorySectionAsk
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** BlockViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltBlockViewModel @Inject constructor(
    runningPractice: RunningPracticeStore,
    blockStore: BlockStore,
    blockHistory: PieceBlockRepository,
    repertoire: RepertoireRepository,
    sessions: SessionRepository,
    config: PracticeConfig,
    clock: WallClock,
    sectionAsk: HistorySectionAsk,
) : BlockViewModel(runningPractice, blockStore, blockHistory, repertoire, sessions, config, clock, sectionAsk)
