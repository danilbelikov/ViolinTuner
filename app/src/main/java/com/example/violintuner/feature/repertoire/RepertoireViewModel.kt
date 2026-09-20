package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.repertoire.PieceRules
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.domain.repertoire.SectionStats
import com.example.violintuner.core.domain.session.SessionRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list of one section of the repertoire (spec 3.22): its elements, its count, and — for a section of the player's own — its name and its end. */
@HiltViewModel
class RepertoireViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    sessions: SessionRepository,
    private val sheetFiles: SheetFiles,
    private val config: RepertoireConfig,
    private val clock: Clock,
) : ViewModel() {
    private val section: SectionRef = SectionKeys.refOf(savedState.get<String>(ARG_SECTION))

    private data class Ui(val filter: PieceStatus? = null, val dialog: SectionDialog? = null, val nameDraft: String = "")

    private val ui = MutableStateFlow(Ui())
    private var closed = false

    // The name as the list shows it: read by the intents, written where the state is built — both on the main thread.
    private var currentName: String? = null

    val state: StateFlow<RepertoireState> =
        combine(repertoire.pieces, repertoire.groups, repertoire.pages, sessions.sessions, ui) { pieces, groups, pages, sessions, ui ->
            val group = (section as? SectionRef.Custom)?.let { ref -> groups.firstOrNull { it.id == ref.groupId } }
            // Deleted here or from elsewhere: there is nothing to show. Said once — a second "close" would take the screen underneath with it.
            if (section is SectionRef.Custom && group == null && !closed) {
                closed = true
                effectChannel.trySend(RepertoireEffect.Close)
            }
            currentName = group?.name
            val own = SectionStats.piecesOf(section, pieces, groups)
            RepertoireReducer.stateOf(own, pages, sessions, ui.filter, LocalDate.now(clock), clock.zone) { sheetFiles.existing(it)?.path }.copy(
                section = section,
                sectionName = group?.name,
                count = SectionStats.countOf(own),
                dialog = ui.dialog,
                nameDraft = ui.nameDraft,
                maxNameLength = config.maxGroupNameLength,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RepertoireReducer.loading(filter = null).copy(section = section),
        )

    private val effectChannel = Channel<RepertoireEffect>(Channel.BUFFERED)
    val effects: Flow<RepertoireEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: RepertoireIntent) {
        when (intent) {
            is RepertoireIntent.FilterSelected -> ui.update { it.copy(filter = intent.status) }
            is RepertoireIntent.PieceClicked -> effectChannel.trySend(RepertoireEffect.OpenPiece(intent.id))
            RepertoireIntent.AddClicked -> effectChannel.trySend(RepertoireEffect.OpenNew(section))
            RepertoireIntent.BackClicked -> effectChannel.trySend(RepertoireEffect.Close)
            is RepertoireIntent.DialogRequested ->
                if (section is SectionRef.Custom) ui.update { it.copy(dialog = intent.dialog, nameDraft = currentName.orEmpty()) }
            is RepertoireIntent.NameChanged -> ui.update { it.copy(nameDraft = intent.text.take(config.maxGroupNameLength)) }
            RepertoireIntent.DialogDismissed -> ui.update { it.copy(dialog = null) }
            RepertoireIntent.DialogConfirmed -> confirm()
        }
    }

    private fun confirm() {
        val group = section as? SectionRef.Custom ?: return
        val current = ui.value
        when (current.dialog) {
            SectionDialog.RENAME -> {
                val name = PieceRules.cleanGroupName(current.nameDraft, config) ?: return
                viewModelScope.launch { repertoire.renameGroup(group.groupId, name) }
            }
            // The list closes itself when its section is gone.
            SectionDialog.DELETE -> viewModelScope.launch { repertoire.deleteGroup(group.groupId) }
            null -> Unit
        }
        ui.update { it.copy(dialog = null) }
    }

    companion object {
        const val ARG_SECTION = "section"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
