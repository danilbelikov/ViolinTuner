package com.violinjourney.app.core.ui.analytics

import com.violinjourney.app.core.analytics.Analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltAnalyticsViewModel @Inject constructor(analytics: Analytics) : AnalyticsViewModel(analytics)
