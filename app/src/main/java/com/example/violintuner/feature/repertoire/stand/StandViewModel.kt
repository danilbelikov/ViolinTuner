package com.example.violintuner.feature.repertoire.stand

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.repertoire.StandHintStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The pages on the stand, the panel that hides by itself, the removal of a page and the one-time hint. */
@HiltViewModel
class StandViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val sheetFiles: SheetFiles,
    private val hints: StandHintStore,
    private val config: RepertoireConfig,
    private val clock: Clock,
) : ViewModel() {

    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "the stand needs a piece" }
    private val firstPage: Int = savedState[ARG_PAGE] ?: 0

    private val mutableState = MutableStateFlow(
        StandState(loading = true, pages = emptyList(), initialPage = 0, panelVisible = true, deleteDialog = false, showHint = false),
    )
    val state: StateFlow<StandState> = mutableState

    private val effectChannel = Channel<StandEffect>(Channel.BUFFERED)
    val effects: Flow<StandEffect> = effectChannel.receiveAsFlow()

    private var currentPage = firstPage
    private var hideJob: Job? = null
    private var closed = false

    init {
        viewModelScope.launch {
            // Read once, not followed: a hint that vanished mid-play because it marked itself seen would be a flicker.
            val unseen = !hints.seen.first()
            mutableState.update { it.copy(showHint = unseen) }
        }
        viewModelScope.launch {
            repertoire.pages.collect { all ->
                val pages = all.filter { it.pieceId == pieceId }
                    .sortedBy { it.position }
                    .map { StandPage(it.id, sheetFiles.existing(it.fileName)?.path) }
                // Nothing left to read from: the last page was removed, or the piece itself.
                if (pages.isEmpty()) close()
                mutableState.update { state ->
                    state.copy(
                        loading = false,
                        pages = pages,
                        initialPage = if (state.loading) firstPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) else state.initialPage,
                    )
                }
            }
        }
        restartHiding()
    }

    fun onIntent(intent: StandIntent) {
        when (intent) {
            StandIntent.BackClicked -> close()
            StandIntent.PanelToggled -> {
                mutableState.update { it.copy(panelVisible = !it.panelVisible) }
                restartHiding()
            }
            StandIntent.Touched -> restartHiding()
            is StandIntent.PageSettled -> currentPage = intent.index
            StandIntent.DeleteClicked -> {
                mutableState.update { it.copy(deleteDialog = true) }
                restartHiding()
            }
            StandIntent.DeleteDismissed -> {
                mutableState.update { it.copy(deleteDialog = false) }
                restartHiding()
            }
            StandIntent.DeleteConfirmed -> {
                val page = mutableState.value.pages.getOrNull(currentPage)
                mutableState.update { it.copy(deleteDialog = false) }
                restartHiding()
                if (page != null) viewModelScope.launch { repertoire.deletePage(page.pageId, clock.millis()) }
            }
            StandIntent.HintShown -> {
                mutableState.update { it.copy(showHint = false) }
                viewModelScope.launch { hints.markSeen() }
            }
        }
    }

    /** The panel leaves after [RepertoireConfig.standPanelHideMs] without a touch — but never from under an open dialog. */
    private fun restartHiding() {
        hideJob?.cancel()
        val state = mutableState.value
        if (!state.panelVisible || state.deleteDialog) return
        hideJob = viewModelScope.launch {
            delay(config.standPanelHideMs)
            mutableState.update { it.copy(panelVisible = false) }
        }
    }

    private fun close() {
        if (closed) return
        closed = true
        effectChannel.trySend(StandEffect.Close)
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        const val ARG_PAGE = "page"
    }
}
