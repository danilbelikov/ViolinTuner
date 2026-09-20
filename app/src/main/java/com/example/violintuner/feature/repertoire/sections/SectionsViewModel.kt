package com.example.violintuner.feature.repertoire.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.repertoire.PieceRules
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.domain.repertoire.SectionStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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
) : ViewModel() {
    private val newName = MutableStateFlow<String?>(null)

    val state: StateFlow<SectionsState> = combine(repertoire.pieces, repertoire.groups, newName) { pieces, groups, newName ->
        val cards = SectionStats.summaries(pieces, groups).map { SectionCard(it.ref, it.name, it.count) }
        SectionsState(
            loading = false,
            cards = cards,
            total = cards.fold(SectionCount.EMPTY) { sum, card -> sum + card.count },
            newName = newName,
            maxNameLength = config.maxGroupNameLength,
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
        }
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
