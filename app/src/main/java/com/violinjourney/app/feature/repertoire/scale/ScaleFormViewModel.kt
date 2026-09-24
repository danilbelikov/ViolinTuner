package com.violinjourney.app.feature.repertoire.scale

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.SectionStats
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The form of a scale, new or edited (spec 3.22): three choices, and the notes follow them at once. */
@HiltViewModel
class ScaleFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val config: RepertoireConfig,
    private val clock: WallClock,
    private val texts: ScaleTexts,
) : ViewModel() {
    /** Null = a new scale. */
    private val pieceId: Long? = savedState.get<Long>(ARG_PIECE_ID)?.takeIf { it != NEW_SCALE }

    private var initial = ScaleDraft()
    private var pieces: List<Piece> = emptyList()
    private var saving = false

    private val mutableState = MutableStateFlow(stateOf(ScaleDraft(), loading = pieceId != null, dialog = null))
    val state: StateFlow<ScaleFormState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ScaleFormEffect>(Channel.BUFFERED)
    val effects: Flow<ScaleFormEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repertoire.pieces.collect { list ->
                pieces = list
                edit { it }
            }
        }
        if (pieceId != null) {
            viewModelScope.launch {
                val piece = repertoire.piece(pieceId)
                val scale = piece?.scale
                if (piece == null || scale == null) {
                    effectChannel.send(ScaleFormEffect.CloseDeleted)
                } else {
                    initial = ScaleDraft(scale.tonic, scale.accidental, scale.kind, scale.octaves, piece.tempoBpm, piece.status, piece.notes)
                    mutableState.value = stateOf(initial, loading = false, dialog = null)
                }
            }
        }
    }

    fun onIntent(intent: ScaleFormIntent) {
        when (intent) {
            // The key and the kind of a scale that exists are locked: another key is another scale (handoff 24d, question 7).
            is ScaleFormIntent.TonicClicked -> keyEdit { draft ->
                if (intent.tonic in allowedTonics(draft)) fitted(draft.copy(tonic = intent.tonic)) else draft
            }
            is ScaleFormIntent.AccidentalSelected -> keyEdit { fitted(it.copy(accidental = intent.accidental)) }
            is ScaleFormIntent.KindSelected -> keyEdit { fitted(it.copy(kind = intent.kind)) }
            is ScaleFormIntent.OctavesSelected -> edit { if (intent.octaves in allowedOctaves(it)) it.copy(octaves = intent.octaves) else it }
            is ScaleFormIntent.TempoStepped -> edit { it.copy(tempoBpm = PieceRules.stepTempo(it.tempoBpm, intent.by, config)) }
            is ScaleFormIntent.TempoPicked -> edit { it.copy(tempoBpm = intent.bpm) }
            is ScaleFormIntent.StatusSelected -> edit { it.copy(status = intent.status) }
            is ScaleFormIntent.NotesChanged -> edit { it.copy(notes = intent.text.take(config.maxNotesLength)) }
            ScaleFormIntent.OpenExistingClicked -> mutableState.value.existingId?.let { effectChannel.trySend(ScaleFormEffect.OpenScale(it)) }
            ScaleFormIntent.SaveClicked -> save()
            ScaleFormIntent.CloseClicked -> if (mutableState.value.draft != initial) showDialog(ScaleFormDialog.DISCARD) else effectChannel.trySend(ScaleFormEffect.Close)
            ScaleFormIntent.DeleteClicked -> if (pieceId != null) showDialog(ScaleFormDialog.DELETE)
            ScaleFormIntent.DialogDismissed -> showDialog(null)
            ScaleFormIntent.DialogConfirmed -> confirm()
        }
    }

    private fun save() {
        val current = mutableState.value
        val draft = pieceDraftOf(current.draft) ?: return
        if (saving || !current.canSave) return
        saving = true
        viewModelScope.launch {
            val now = clock.millis()
            if (pieceId == null) {
                effectChannel.send(ScaleFormEffect.OpenScale(repertoire.add(draft, now)))
            } else {
                repertoire.update(pieceId, draft, now)
                effectChannel.send(ScaleFormEffect.Close)
            }
        }
    }

    private fun confirm() {
        when (mutableState.value.dialog) {
            ScaleFormDialog.DISCARD -> effectChannel.trySend(ScaleFormEffect.Close)
            ScaleFormDialog.DELETE -> viewModelScope.launch {
                pieceId?.let { repertoire.delete(it) }
                effectChannel.send(ScaleFormEffect.CloseDeleted)
            }
            null -> Unit
        }
        showDialog(null)
    }

    private fun showDialog(dialog: ScaleFormDialog?) = mutableState.update { it.copy(dialog = dialog) }

    private fun specOf(draft: ScaleDraft): ScaleSpec? = draft.tonic?.let { ScaleSpec(it, draft.accidental, draft.kind, draft.octaves) }

    /** The title is the scale itself, said in words; nobody types it (spec 3.22). */
    private fun pieceDraftOf(draft: ScaleDraft): PieceDraft? = specOf(draft)?.let { spec ->
        PieceDraft(
            title = texts.titleOf(spec), key = spec.key, tempoBpm = draft.tempoBpm, status = draft.status, notes = draft.notes,
            section = PieceSection.SCALES, scale = spec,
        )
    }

    private fun allowedTonics(draft: ScaleDraft): Set<Tonic> = Tonic.entries.filter { Scales.isKeyAllowed(it, draft.accidental, draft.kind) }.toSet()

    private fun allowedOctaves(draft: ScaleDraft): Set<Int> {
        val tonic = draft.tonic ?: return (1..Scales.MAX_OCTAVES).toSet()
        return (1..Scales.MAX_OCTAVES).filter { Scales.octavesFit(tonic, draft.accidental, it, config.scaleLowestMidi, config.scaleHighestMidi) }.toSet()
    }

    /**
     * After a change of the key: a tonic that now makes eight signs is dropped, octaves that no
     * longer end on the instrument come down to the most that do — the form never holds a scale
     * it would refuse to draw.
     */
    private fun fitted(draft: ScaleDraft): ScaleDraft {
        val tonic = draft.tonic?.takeIf { Scales.isKeyAllowed(it, draft.accidental, draft.kind) }
        val kept = draft.copy(tonic = tonic)
        val octaves = allowedOctaves(kept)
        return if (kept.octaves in octaves || octaves.isEmpty()) kept else kept.copy(octaves = octaves.max())
    }

    private inline fun keyEdit(transform: (ScaleDraft) -> ScaleDraft) {
        if (pieceId != null) effectChannel.trySend(ScaleFormEffect.ShowLocked) else edit(transform)
    }

    private inline fun edit(transform: (ScaleDraft) -> ScaleDraft) {
        mutableState.update { stateOf(transform(it.draft), loading = it.loading, dialog = it.dialog) }
    }

    private fun stateOf(draft: ScaleDraft, loading: Boolean, dialog: ScaleFormDialog?): ScaleFormState {
        val scale = specOf(draft)?.let { Scales.build(it, config.scaleLowestMidi, config.scaleHighestMidi) }
        val existing = pieceDraftOf(draft)?.let { SectionStats.sameScale(pieces, it, exceptId = pieceId) }
        return ScaleFormState(
            loading = loading,
            isNew = pieceId == null,
            draft = draft,
            scale = scale,
            tonicsAllowed = allowedTonics(draft),
            octavesAllowed = allowedOctaves(draft),
            existingId = existing?.id,
            canSave = scale != null && existing == null,
            dialog = dialog,
            maxNotesLength = config.maxNotesLength,
        )
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"

        /** Navigation arguments cannot be null longs: this stands for "no scale yet". */
        const val NEW_SCALE = -1L
    }
}
