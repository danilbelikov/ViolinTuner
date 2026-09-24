package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** HomeLookViewModel of the shared code, made by Hilt on Android: the same constructor, qualifiers and all. */
@HiltViewModel
class HiltHomeLookViewModel @Inject constructor(
    home: HomeRepository,
) : HomeLookViewModel(home)
