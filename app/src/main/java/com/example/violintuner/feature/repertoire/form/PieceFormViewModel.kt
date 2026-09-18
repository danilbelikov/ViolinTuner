package com.example.violintuner.feature.repertoire.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceRules
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PieceFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val config: RepertoireConfig,
    private val clock: Clock,
) : ViewModel() {
    /** Null = a new piece. */
    private val pieceId: Long? = savedState.get<Long>(ARG_PIECE_ID)?.takeIf { it != NEW_PIECE }
    private val focusNotes: Boolean = savedState.get<Boolean>(ARG_FOCUS_NOTES) ?: false

    private var initial = PieceDraft()
    private var titleTouched = false
    private var saving = false

    private val mutableState = MutableStateFlow(stateOf(PieceDraft(), loading = pieceId != null, dialog = null))
    val state: StateFlow<PieceFormState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PieceFormEffect>(Channel.BUFFERED)
    val effects: Flow<PieceFormEffect> = effectChannel.receiveAsFlow()

    init {
        if (pieceId != null) {
            viewModelScope.launch {
                val piece = repertoire.piece(pieceId)
                if (piece == null) {
                    effectChannel.send(PieceFormEffect.CloseDeleted)
                } else {
                    initial = PieceRules.draftOf(piece)
                    mutableState.value = stateOf(initial, loading = false, dialog = null)
                }
            }
        }
    }

    fun onIntent(intent: PieceFormIntent) {
        when (intent) {
            is PieceFormIntent.TitleChanged -> {
                titleTouched = true
                edit { it.copy(title = PieceFormReducer.capped(intent.text, config.maxTitleLength)) }
            }
            is PieceFormIntent.ComposerChanged -> edit { it.copy(composer = PieceFormReducer.capped(intent.text, config.maxComposerLength)) }
            is PieceFormIntent.NotesChanged -> edit { it.copy(notes = PieceFormReducer.capped(intent.text, config.maxNotesLength)) }
            is PieceFormIntent.TonicClicked -> edit { it.copy(key = PieceFormReducer.clickTonic(it.key, intent.tonic)) }
            is PieceFormIntent.AccidentalSelected -> edit { it.copy(key = it.key?.copy(accidental = intent.accidental)) }
            is PieceFormIntent.ModeSelected -> edit { it.copy(key = it.key?.copy(mode = intent.mode)) }
            is PieceFormIntent.TempoStepped -> edit { it.copy(tempoBpm = PieceRules.stepTempo(it.tempoBpm, intent.by, config)) }
            is PieceFormIntent.TempoPicked -> edit { it.copy(tempoBpm = intent.bpm) }
            is PieceFormIntent.StatusSelected -> edit { it.copy(status = intent.status) }
            PieceFormIntent.SaveClicked -> save()
            PieceFormIntent.CloseClicked -> close()
            PieceFormIntent.DeleteClicked -> if (pieceId != null) showDialog(PieceFormDialog.DELETE)
            PieceFormIntent.DialogDismissed -> showDialog(null)
            PieceFormIntent.DialogConfirmed -> confirm()
        }
    }

    private fun save() {
        val draft = mutableState.value.draft
        if (saving) return
        if (!PieceFormReducer.canSave(draft, config)) {
            titleTouched = true
            edit { it }
            return
        }
        saving = true
        viewModelScope.launch {
            val now = clock.millis()
            if (pieceId == null) {
                effectChannel.send(PieceFormEffect.OpenCreated(repertoire.add(draft, now)))
            } else {
                repertoire.update(pieceId, draft, now)
                effectChannel.send(PieceFormEffect.Close)
            }
        }
    }

    private fun close() {
        if (PieceFormReducer.isDirty(initial, mutableState.value.draft, config)) {
            showDialog(PieceFormDialog.DISCARD)
        } else {
            effectChannel.trySend(PieceFormEffect.Close)
        }
    }

    private fun confirm() {
        when (mutableState.value.dialog) {
            PieceFormDialog.DISCARD -> effectChannel.trySend(PieceFormEffect.Close)
            PieceFormDialog.DELETE -> viewModelScope.launch {
                pieceId?.let { repertoire.delete(it) }
                effectChannel.send(PieceFormEffect.CloseDeleted)
            }
            null -> Unit
        }
        showDialog(null)
    }

    private fun showDialog(dialog: PieceFormDialog?) = mutableState.update { it.copy(dialog = dialog) }

    private inline fun edit(transform: (PieceDraft) -> PieceDraft) {
        mutableState.update { stateOf(transform(it.draft), loading = it.loading, dialog = it.dialog) }
    }

    private fun stateOf(draft: PieceDraft, loading: Boolean, dialog: PieceFormDialog?): PieceFormState {
        val canSave = PieceFormReducer.canSave(draft, config)
        return PieceFormState(
            loading = loading,
            isNew = pieceId == null,
            draft = draft,
            titleError = titleTouched && !canSave,
            canSave = canSave,
            dialog = dialog,
            maxTitleLength = config.maxTitleLength,
            maxComposerLength = config.maxComposerLength,
            maxNotesLength = config.maxNotesLength,
            focusNotes = focusNotes,
        )
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        const val ARG_FOCUS_NOTES = "focusNotes"

        /** Navigation arguments cannot be null longs: this stands for "no piece yet". */
        const val NEW_PIECE = -1L
    }
}
