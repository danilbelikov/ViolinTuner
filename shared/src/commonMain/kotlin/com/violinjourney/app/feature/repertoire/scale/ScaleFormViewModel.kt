package com.violinjourney.app.feature.repertoire.scale

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStats
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.SectionStats
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The form of a scale, new or edited (spec 3.22, 3.36.4): three choices, and the notes follow them at once. The takes are read only to
 * say how many the scale that is there already has.
 */
open class ScaleFormViewModel(
    private val savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val sessions: SessionRepository,
    private val config: RepertoireConfig,
    private val clock: WallClock,
    private val texts: ScaleTexts,
) : ViewModel() {
    /** Null = a new scale. */
    private val pieceId: Long? = savedState.get<Long>(ARG_PIECE_ID)?.takeIf { it != NEW_SCALE }
    private val focusNotes: Boolean = savedState.get<Boolean>(ARG_FOCUS_NOTES) ?: false

    // What the player had picked and typed before the system ended the process; laid over the scale once it is read.
    private val kept: ScaleDraft? = ScaleFormSaved.read(savedState)

    /** The scale as stored (for an edit) or the empty draft: what the form compares with. */
    private var initial = ScaleDraft()

    /** The title as stored, in the language the scale was made in; null for a new scale and until it is read. */
    private var savedTitle: String? = null
    private var pieces: List<Piece> = emptyList()
    private var takes: List<SessionSummary> = emptyList()
    private var saving = false

    private val mutableState = MutableStateFlow(stateOf(kept ?: initial, loading = pieceId != null, dialog = null))
    val state: StateFlow<ScaleFormState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ScaleFormEffect>(Channel.BUFFERED)
    val effects: Flow<ScaleFormEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(repertoire.pieces, sessions.sessions) { list, recorded -> list to recorded }.collect { (list, recorded) ->
                pieces = list
                takes = recorded
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
                    savedTitle = piece.title
                    // the key and the kind of a scale that exists are its own, whatever was kept
                    val restored = kept?.copy(tonic = scale.tonic, accidental = scale.accidental, kind = scale.kind)
                    mutableState.value = stateOf(restored ?: initial, loading = false, dialog = null)
                }
            }
        }
    }

    fun onIntent(intent: ScaleFormIntent) {
        when (intent) {
            // The key and the kind of a scale that exists are locked: another key is another scale (spec 3.22). Their buttons sleep
            // under the line that says so (3.36.4); a tap that still comes does nothing.
            is ScaleFormIntent.TonicClicked -> keyEdit { draft ->
                if (intent.tonic in allowedTonics(draft)) fitted(draft.copy(tonic = intent.tonic)) else draft
            }
            is ScaleFormIntent.AccidentalSelected -> keyEdit { fitted(it.copy(accidental = intent.accidental)) }
            is ScaleFormIntent.KindSelected -> keyEdit { fitted(it.copy(kind = intent.kind)) }
            is ScaleFormIntent.OctavesSelected -> userEdit { if (intent.octaves in allowedOctaves(it)) it.copy(octaves = intent.octaves) else it }
            is ScaleFormIntent.TempoStepped -> userEdit { it.copy(tempoBpm = PieceRules.stepTempo(it.tempoBpm, intent.by, config)) }
            is ScaleFormIntent.TempoPicked -> userEdit { it.copy(tempoBpm = intent.bpm) }
            is ScaleFormIntent.StatusSelected -> userEdit { it.copy(status = intent.status) }
            is ScaleFormIntent.NotesChanged -> userEdit { it.copy(notes = intent.text.takeCodePoints(config.maxNotesLength)) }
            ScaleFormIntent.OpenExistingClicked -> openExisting()
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
        // An untouched scale is not saved (spec 5.9): it keeps its place in the list, and its title the language it was made in.
        if (pieceId != null && current.draft == initial) {
            effectChannel.trySend(ScaleFormEffect.Close)
            return
        }
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

    /**
     * «Открыть её» — the scale that is there already: it is found by the key, the kind and the octaves, so those are no loss;
     * a tempo, a status or notes of this form would be (spec 3.15: leaving with edits asks «Не сохранять?»).
     */
    private fun openExisting() {
        val current = mutableState.value
        val id = current.twin?.id ?: return
        val draft = current.draft
        if (draft.tempoBpm != initial.tempoBpm || draft.status != initial.status || draft.notes != initial.notes) {
            showDialog(ScaleFormDialog.DISCARD_AND_OPEN)
        } else {
            effectChannel.trySend(ScaleFormEffect.OpenScale(id))
        }
    }

    private fun confirm() {
        when (mutableState.value.dialog) {
            ScaleFormDialog.DISCARD -> effectChannel.trySend(ScaleFormEffect.Close)
            ScaleFormDialog.DISCARD_AND_OPEN -> mutableState.value.twin?.let { effectChannel.trySend(ScaleFormEffect.OpenScale(it.id)) }
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
        if (pieceId == null) userEdit(transform)
    }

    private inline fun edit(transform: (ScaleDraft) -> ScaleDraft) {
        mutableState.update { stateOf(transform(it.draft), loading = it.loading, dialog = it.dialog) }
    }

    /**
     * The player's own edit: kept in the saved state as well, so that it outlives the process together with the text
     * of the notes field. Not while an edited scale is still being read — the form under it is the empty one.
     */
    private inline fun userEdit(transform: (ScaleDraft) -> ScaleDraft) {
        edit(transform)
        val now = mutableState.value
        if (!now.loading) ScaleFormSaved.write(savedState, now.draft)
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
            twin = existing?.let { ScaleTwin(it.id, it.status, PieceStats.takesOf(it.id, takes).size) },
            canSave = scale != null && existing == null,
            dialog = dialog,
            maxNotesLength = config.maxNotesLength,
            savedTitle = savedTitle,
            startNote = draft.tonic?.let { ScaleWords.nameOf(Scales.lowestTonic(it, draft.accidental, config.scaleLowestMidi)) },
            focusNotes = focusNotes,
        )
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        const val ARG_FOCUS_NOTES = "focusNotes"

        /** Navigation arguments cannot be null longs: this stands for "no scale yet". */
        const val NEW_SCALE = -1L
    }
}
