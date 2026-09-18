package com.example.violintuner.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.practice.RunningPracticeStore
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Decides where the app starts. Only the first stored value counts: a start destination that
 * changed under a live NavHost would rebuild the graph, so later moves between onboarding and
 * the tabs are explicit navigation.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    repository: SettingsRepository,
    sessions: SessionRepository,
    runningPractice: RunningPracticeStore,
) : ViewModel() {
    init {
        viewModelScope.launch { sessions.deleteOrphanAudio() }
    }

    /** Null while the settings are being read: show nothing rather than the wrong screen. */
    val startRoute: StateFlow<String?> = flow {
        val done = repository.settings.first().onboardingDone
        emit(if (done) TopLevelDestination.START.route else ONBOARDING_ROUTE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /** The mark on the «Занятия» tab: a practice runs (spec 3.12). */
    val practiceRunning: StateFlow<Boolean> = runningPractice.running
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = false)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
