package com.violinjourney.app.feature.repertoire.stand

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.StandHintStore
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** StandViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltStandViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repertoire: RepertoireRepository,
    sheetFiles: SheetFiles,
    hints: StandHintStore,
    config: RepertoireConfig,
    clock: WallClock,
) : StandViewModel(savedState, repertoire, sheetFiles, hints, config, clock)
