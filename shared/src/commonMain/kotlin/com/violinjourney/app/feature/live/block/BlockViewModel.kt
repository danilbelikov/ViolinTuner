package com.violinjourney.app.feature.live.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.domain.practice.practiceTicks
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.ui.format.Formats
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Blocks on Live (spec 3.28): the bookmark by the record key and the sheets it opens. Apart from
 * [com.violinjourney.app.feature.live.LiveViewModel]: Live changes twenty times a second, the
 * bookmark once a second, and none of this is about the sound.
 */
open class BlockViewModel(
    private val runningPractice: RunningPracticeStore,
    private val blockStore: BlockStore,
    blockHistory: PieceBlockRepository,
    repertoire: RepertoireRepository,
    sessions: SessionRepository,
    private val config: PracticeConfig,
    private val clock: WallClock,
) : ViewModel() {

    private val ui = MutableStateFlow(BlockReducer.Ui())

    /**
     * The clock, on every second of the running practice's own time — the seconds the practice tag of Live turns on
     * ([practiceTicks]), so «занятие 24:18» in the header of the choice is the tag's time and runs with it (spec 3.36.6); the minutes
     * left and the brass line follow it too. Once when none runs. Nothing accumulates.
     */
    private val now: Flow<Long> = runningPractice.practiceTicks(clock).map { tick -> tick?.nowEpochMs ?: clock.millis() }

    private val practice = combine(runningPractice.running, blockStore.blocks, blockHistory.blocks, ::Triple)

    // ordered when the repertoire or the takes change, not on every tick of the clock; the sections by the alphabet of the interface
    private val shelf = combine(repertoire.pieces, repertoire.groups, sessions.sessions) { pieces, groups, sessions ->
        BlockReducer.shelfOf(pieces, groups, sessions, Formats.alphabetical())
    }

    // the time zone is asked only while the choice is open: the bookmark alone ticks without it
    private val zone = { clock.zone }

    val state: StateFlow<BlockState> =
        combine(practice, shelf, ui, now) { (running, blocks, saved), shelf, ui, now ->
            BlockReducer.stateOf(running, blocks, saved, shelf, ui, now, zone, config)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BlockState.NONE)

    private val effectChannel = Channel<BlockEffect>(Channel.BUFFERED)
    val effects: Flow<BlockEffect> = effectChannel.receiveAsFlow()

    // The stores are the truth; the state mirrors them a moment later, and a tap may come in between.
    private var latestRunning: RunningPractice? = null
    private var latestBlocks: PracticeBlocks? = null
    private var latestSaved: List<SavedBlock> = emptyList()

    init {
        viewModelScope.launch { runningPractice.running.collect { latestRunning = it } }
        viewModelScope.launch { blockStore.blocks.collect { latestBlocks = it } }
        viewModelScope.launch { blockHistory.blocks.collect { latestSaved = it } }
    }

    fun onIntent(intent: BlockIntent) {
        when (intent) {
            BlockIntent.BookmarkClicked -> ui.value = BlockReducer.Ui(sheetOpen = true)
            BlockIntent.SheetDismissed, BlockIntent.NotNowClicked -> ui.value = BlockReducer.Ui()
            BlockIntent.StartPracticeClicked -> viewModelScope.launch {
                // the same start as on «Занятия» (spec 3.12), but Live stays: the sheet turns into the choice by itself
                runningPractice.startIfIdle(clock.millis())
            }
            is BlockIntent.PieceClicked -> ui.update {
                val blocks = BlockRules.ofPractice(latestRunning, latestBlocks)
                it.copy(selectedId = intent.id, goalMinutes = BlockRules.defaultGoalMinutes(intent.id, latestSaved, blocks, config))
            }
            is BlockIntent.GoalPicked -> ui.update { it.copy(goalMinutes = BlockRules.clampGoal(intent.minutes, config)) }
            is BlockIntent.GoalStepped -> ui.update { current ->
                current.copy(goalMinutes = BlockRules.stepGoal(current.goalMinutes ?: config.blockDefaultGoalMinutes, intent.steps, config))
            }
            BlockIntent.StartClicked -> start()
            BlockIntent.StopClicked -> stop()
            BlockIntent.OpenRepertoireClicked -> {
                ui.value = BlockReducer.Ui()
                // the tab «Репертуар» (spec 3.36.1): where it leads is the route's business
                effectChannel.trySend(BlockEffect.OpenRepertoire)
            }
        }
    }

    private fun start() {
        val running = latestRunning ?: return
        val choice = ui.value
        val pieceId = choice.selectedId ?: return
        val now = clock.millis()
        val current = BlockRules.ofPractice(running, latestBlocks)?.current
        // the element whose block runs is not picked again
        if (current != null && current.pieceId == pieceId && BlockRules.isRunning(current, now)) return
        val goal = choice.goalMinutes ?: config.blockDefaultGoalMinutes
        ui.value = BlockReducer.Ui()
        viewModelScope.launch {
            blockStore.update { BlockRules.started(it, running.startedAtEpochMs, pieceId, goal * MS_PER_MINUTE, now) }
        }
    }

    private fun stop() {
        val running = latestRunning ?: return
        val now = clock.millis()
        viewModelScope.launch { blockStore.update { BlockRules.stopped(it, running.startedAtEpochMs, now) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
