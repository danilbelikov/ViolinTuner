package com.violinjourney.app.feature.repertoire.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.NoBlockHistory
import com.violinjourney.app.core.domain.practice.PieceBlockRepository
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.SectionStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The sections of the repertoire with their counts, and the making of a new one (spec 3.22). */
@HiltViewModel
class SectionsViewModel @Inject constructor(
    private val repertoire: RepertoireRepository,
    private val config: RepertoireConfig,
    private val clock: Clock,
    blocks: PieceBlockRepository = NoBlockHistory,
    private val practiceConfig: PracticeConfig = PracticeConfig(),
) : ViewModel() {
    private val newName = MutableStateFlow<String?>(null)
    private val timeExpanded = MutableStateFlow(false)

    val state: StateFlow<SectionsState> = combine(repertoire.pieces, repertoire.groups, newName, blocks.blocks, timeExpanded) { pieces, groups, newName, saved, expanded ->
        val cards = SectionStats.summaries(pieces, groups).map { SectionCard(it.ref, it.name, it.count) }
        SectionsState(
            loading = false,
            cards = cards,
            total = cards.fold(SectionCount.EMPTY) { sum, card -> sum + card.count },
            newName = newName,
            maxNameLength = config.maxGroupNameLength,
            time = timeCardOf(pieces, saved, expanded),
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        SectionsState(loading = true, cards = emptyList(), total = SectionCount.EMPTY, maxNameLength = config.maxGroupNameLength),
    )

    private val effectChannel = Channel<SectionsEffect>(Channel.BUFFERED)
    val effects: Flow<SectionsEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: SectionsIntent) {
        when (intent) {
            is SectionsIntent.SectionClicked -> effectChannel.trySend(SectionsEffect.OpenSection(intent.ref))
            SectionsIntent.AddClicked -> newName.value = ""
            is SectionsIntent.NameChanged -> if (newName.value != null) newName.value = intent.text.take(config.maxGroupNameLength)
            SectionsIntent.DialogDismissed -> newName.value = null
            SectionsIntent.CreateConfirmed -> create()
            SectionsIntent.TimeToggled -> timeExpanded.value = !timeExpanded.value
            is SectionsIntent.TimePieceClicked -> effectChannel.trySend(SectionsEffect.OpenPiece(intent.id))
        }
    }

    /**
     * «Время по элементам» (spec 3.28, 5.21): the saved blocks of the last days, today included, the most first and
     * by name when equal. "Today" is read on every change, as the lists of «Записи» do.
     */
    private fun timeCardOf(pieces: List<Piece>, saved: List<SavedBlock>, expanded: Boolean): PieceTimeCard? {
        if (pieces.isEmpty()) return null
        val titles = pieces.associate { it.id to it.title }
        val rows = BlockRules.timeByPiece(saved, LocalDate.now(clock), practiceConfig.pieceTimeDays)
            .mapNotNull { time -> titles[time.pieceId]?.let { PieceTimeRow(time.pieceId, it, time.totalMs, time.todayMs) } }
            .sortedWith(compareByDescending<PieceTimeRow> { it.totalMs }.thenBy { it.title })
        return PieceTimeCard(rows, practiceConfig.pieceTimeDays, expanded)
    }

    // A new section opens at once: it was made to put something into it.
    private fun create() {
        val name = newName.value?.let { PieceRules.cleanGroupName(it, config) } ?: return
        newName.value = null
        viewModelScope.launch {
            val id = repertoire.addGroup(name, clock.millis())
            effectChannel.send(SectionsEffect.OpenSection(SectionRef.Custom(id)))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
