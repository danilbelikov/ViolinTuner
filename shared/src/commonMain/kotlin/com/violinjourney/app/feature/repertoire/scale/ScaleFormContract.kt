package com.violinjourney.app.feature.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind

enum class ScaleFormDialog {
    DISCARD,

    /** «Не сохранять?» on the way to the scale that is there already («Открыть её»). */
    DISCARD_AND_OPEN,
    DELETE,
}

/** What the form of a scale holds (spec 3.22, handoff 24d). No tonic — no scale yet. */
data class ScaleDraft(
    val tonic: Tonic? = null,
    val accidental: Accidental = Accidental.NATURAL,
    val kind: ScaleKind = ScaleKind.MAJOR,
    val octaves: Int = 2,
    val tempoBpm: Int? = null,
    val status: PieceStatus = PieceStatus.READING,
    val notes: String = "",
)

/**
 * The scale that is there already (spec 3.22, 3.36.4): the plate over «Открыть её» says where it lives, its status and how many takes
 * it has — «Такая гамма уже есть — в «Гаммах», статус «Учу», 2 дубля.».
 */
data class ScaleTwin(val id: Long, val status: PieceStatus, val takes: Int)

data class ScaleFormState(
    /** True until an edited scale has been read; a new one is never loading. */
    val loading: Boolean,
    val isNew: Boolean,
    val draft: ScaleDraft,
    /** The scale the three choices add up to: the live preview, the title, the range. Null until a tonic is picked. */
    val scale: Scale?,
    /** The tonics that make a key of seven signs at most with the accidental and the kind picked; the others are dimmed and deaf. */
    val tonicsAllowed: Set<Tonic>,
    /** Octaves that still end on the instrument. */
    val octavesAllowed: Set<Int>,
    /** The very scale is in the repertoire already: the main button opens it instead («Открыть её»). */
    val twin: ScaleTwin?,
    val canSave: Boolean,
    val dialog: ScaleFormDialog?,
    val maxNotesLength: Int,
    /**
     * The title as stored, for an edit: what «Не сохранять?» and «Удалить …?» call the scale by (spec 3.36.1) — the name in the
     * list, in the language the scale was made in (spec 3.26), not the one [scale] gives now: the octaves can be edited, and the
     * language may have changed since. Null for a new scale and while it is read.
     */
    val savedTitle: String? = null,
    /**
     * The lowest tonic the violin has for the key picked — «F4» — what the reason of an octave that does not fit starts from («Три
     * октавы от F4 не помещаются на скрипке», spec 3.36.4); null without a tonic.
     */
    val startNote: String? = null,
    /** Opened by «Добавить заметку» of the scale's screen: the notes field takes the focus (spec 3.15, 3.36.4). */
    val focusNotes: Boolean = false,
)

sealed interface ScaleFormIntent {
    data class TonicClicked(val tonic: Tonic) : ScaleFormIntent

    data class AccidentalSelected(val accidental: Accidental) : ScaleFormIntent

    data class KindSelected(val kind: ScaleKind) : ScaleFormIntent

    data class OctavesSelected(val octaves: Int) : ScaleFormIntent

    data class TempoStepped(val by: Int) : ScaleFormIntent

    data class TempoPicked(val bpm: Int?) : ScaleFormIntent

    data class StatusSelected(val status: PieceStatus) : ScaleFormIntent

    data class NotesChanged(val text: String) : ScaleFormIntent

    data object OpenExistingClicked : ScaleFormIntent

    data object SaveClicked : ScaleFormIntent

    data object CloseClicked : ScaleFormIntent

    data object DeleteClicked : ScaleFormIntent

    data object DialogConfirmed : ScaleFormIntent

    data object DialogDismissed : ScaleFormIntent
}

sealed interface ScaleFormEffect {
    data object Close : ScaleFormEffect

    /** A new scale, or the one that turned out to be there already: its screen takes the place of the form. */
    data class OpenScale(val pieceId: Long) : ScaleFormEffect

    data object CloseDeleted : ScaleFormEffect
}
