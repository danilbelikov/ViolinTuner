package com.violinjourney.app.feature.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind

enum class ScaleFormDialog { DISCARD, DELETE }

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

data class ScaleFormState(
    /** True until an edited scale has been read; a new one is never loading. */
    val loading: Boolean,
    val isNew: Boolean,
    val draft: ScaleDraft,
    /** The scale the three choices add up to: the live preview, the title, the range. Null until a tonic is picked. */
    val scale: Scale?,
    /** Tonics that would make a key of eight signs with the accidental and the kind picked: dimmed, deaf to taps. */
    val tonicsAllowed: Set<Tonic>,
    /** Octaves that still end on the instrument. */
    val octavesAllowed: Set<Int>,
    /** The very scale is in the repertoire already: «Такая гамма уже есть · Открыть». */
    val existingId: Long?,
    val canSave: Boolean,
    val dialog: ScaleFormDialog?,
    val maxNotesLength: Int,
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

    /** The key and the kind of a scale that exists are locked: «Это была бы другая гамма — добавьте новую». */
    data object ShowLocked : ScaleFormEffect
}
