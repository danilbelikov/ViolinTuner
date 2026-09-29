package com.violinjourney.app.feature.repertoire.stand

import com.violinjourney.app.core.domain.repertoire.scale.Scales
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.StandHintStore
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.time.WallClock
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

/**
 * The pages on the stand, the panel that hides by itself, the removal of a page and the one-time hint (spec 3.15, 3.36.4): nothing
 * shows until both the pages and the mark of the first visit are read; on the first visit the hint stands without the panel for
 * [RepertoireConfig.standHintMs] or until the first touch, and the panel stays away after it — the middle of the sheet calls it.
 */
open class StandViewModel(
    savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val sheetFiles: SheetFiles,
    private val hints: StandHintStore,
    private val config: RepertoireConfig,
    private val clock: WallClock,
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
    private var hintJob: Job? = null
    private var closed = false

    init {
        viewModelScope.launch {
            // Read once, not followed: a hint that vanished mid-play because it marked itself seen would be a flicker. Read before
            // the pages are shown: the panel of a first visit must not blink up before the hint takes its place.
            val unseen = !hints.seen.first()
            combine(repertoire.pages, repertoire.pieces) { all, pieces ->
                // A scale opens with its notes drawn by the app; the photos — a fingering from the book — follow.
                val drawn = pieces.firstOrNull { it.id == pieceId }?.scale
                    ?.let { Scales.build(it, config.scaleLowestMidi, config.scaleHighestMidi) }
                    ?.let { StandPage(StandPage.DRAWN_ID, path = null, scale = it) }
                listOfNotNull(drawn) + all.filter { it.pieceId == pieceId }
                    .sortedBy { it.position }
                    .map { StandPage(it.id, sheetFiles.existing(it.fileName)?.filePath) }
            }.collect { pages ->
                // Nothing left to read from: the last page was removed, or the piece itself.
                if (pages.isEmpty()) close()
                val first = mutableState.value.loading
                mutableState.update { state ->
                    state.copy(
                        loading = false,
                        pages = pages,
                        initialPage = if (state.loading) firstPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) else state.initialPage,
                        showHint = if (state.loading) unseen else state.showHint,
                        panelVisible = if (state.loading) !unseen else state.panelVisible,
                    )
                }
                if (first) {
                    if (unseen) showHintAWhile() else restartHiding()
                }
            }
        }
    }

    fun onIntent(intent: StandIntent) {
        when (intent) {
            StandIntent.BackClicked -> close()
            StandIntent.PanelToggled -> {
                // a tap in the middle under the hint takes it away and calls the panel, as it will from now on
                endHint()
                mutableState.update { it.copy(panelVisible = !it.panelVisible) }
                restartHiding()
            }
            StandIntent.Touched -> {
                endHint()
                restartHiding()
            }
            is StandIntent.PageSettled -> currentPage = intent.index
            StandIntent.DeleteClicked -> {
                if (mutableState.value.pages.getOrNull(currentPage)?.drawn == true) return
                mutableState.update { it.copy(deleteDialog = true) }
                restartHiding()
            }
            StandIntent.DeleteDismissed -> {
                mutableState.update { it.copy(deleteDialog = false) }
                restartHiding()
            }
            StandIntent.DeleteConfirmed -> {
                val page = mutableState.value.pages.getOrNull(currentPage)?.takeIf { !it.drawn }
                mutableState.update { it.copy(deleteDialog = false) }
                restartHiding()
                if (page != null) viewModelScope.launch { repertoire.deletePage(page.pageId, clock.millis()) }
            }
            StandIntent.HintShown -> endHint()
        }
    }

    /** The hint of the first visit stays its time, then goes by itself. */
    private fun showHintAWhile() {
        hintJob = viewModelScope.launch {
            delay(config.standHintMs)
            onIntent(StandIntent.HintShown)
        }
    }

    /** The hint goes — its time is up, or the first touch came — and is never shown again; the panel stays where it is. */
    private fun endHint() {
        if (!mutableState.value.showHint) return
        hintJob?.cancel()
        hintJob = null
        mutableState.update { it.copy(showHint = false) }
        viewModelScope.launch { hints.markSeen() }
    }

    /** The panel leaves after [RepertoireConfig.standPanelHideMs] without a touch — but never from under an open dialog. */
    private fun restartHiding() {
        hideJob?.cancel()
        val state = mutableState.value
        if (!state.panelVisible || state.deleteDialog || state.showHint) return
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
