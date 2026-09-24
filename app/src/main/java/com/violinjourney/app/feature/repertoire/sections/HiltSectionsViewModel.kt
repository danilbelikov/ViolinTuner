package com.violinjourney.app.feature.repertoire.sections

import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** SectionsViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltSectionsViewModel @Inject constructor(
    repertoire: RepertoireRepository,
    config: RepertoireConfig,
    clock: WallClock,
    blocks: PieceBlockRepository,
    practiceConfig: PracticeConfig,
) : SectionsViewModel(repertoire, config, clock, blocks, practiceConfig)
