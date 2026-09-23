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
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Blocks on Live (spec 3.28): the bookmark by the record key and the sheets it opens. Apart from
 * [com.violinjourney.app.feature.live.LiveViewModel]: Live changes twenty times a second, the
 * bookmark once a second, and none of this is about the sound.
 */
@HiltViewModel
class BlockViewModel @Inject constructor(
    private val runningPractice: RunningPracticeStore,
    private val blockStore: BlockStore,
    blockHistory: PieceBlockRepository,
    repertoire: RepertoireRepository,
    sessions: SessionRepository,
    private val config: PracticeConfig,
    private val clock: Clock,
) : ViewModel() {

    private val ui = MutableStateFlow(BlockReducer.Ui())

    /** The clock, every second while a practice runs: the minutes left and the brass line follow it; nothing accumulates. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val now: Flow<Long> = runningPractice.running.flatMapLatest { running ->
        if (running == null) {
            flowOf(clock.millis())
        } else {
            flow {
                while (true) {
                    val now = clock.millis()
                    emit(now)
                    delay(MS_PER_SECOND - now % MS_PER_SECOND)
                }
            }
        }
    }

    private val practice = combine(runningPractice.running, blockStore.blocks, blockHistory.blocks, ::Triple)
    private val shelf = combine(repertoire.pieces, repertoire.groups, sessions.sessions, ::Triple)

    val state: StateFlow<BlockState> =
        combine(practice, shelf, ui, now) { (running, blocks, saved), (pieces, groups, takes), ui, now ->
            BlockReducer.stateOf(running, blocks, saved, pieces, groups, takes, ui, now, clock.zone, config)
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
                if (latestRunning == null) runningPractice.start(clock.millis())
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
        const val MS_PER_SECOND = 1_000L
    }
}
