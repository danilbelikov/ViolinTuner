package com.violinjourney.app.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** RepertoireViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltRepertoireViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repertoire: RepertoireRepository,
    sessions: SessionRepository,
    sheetFiles: SheetFiles,
    config: RepertoireConfig,
    clock: WallClock,
) : RepertoireViewModel(savedState, repertoire, sessions, sheetFiles, config, clock)
