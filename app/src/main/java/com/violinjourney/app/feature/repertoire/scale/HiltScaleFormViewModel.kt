package com.violinjourney.app.feature.repertoire.scale

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** ScaleFormViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltScaleFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repertoire: RepertoireRepository,
    config: RepertoireConfig,
    clock: WallClock,
    texts: ScaleTexts,
) : ScaleFormViewModel(savedState, repertoire, config, clock, texts)
