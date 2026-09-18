package com.example.violintuner.feature.repertoire.piece

import com.example.violintuner.core.domain.repertoire.PieceStatus

/** What the top of the piece screen says. Fields the piece does not have are null or empty and are simply not shown. */
data class PieceHeader(
    val title: String,
    val composer: String,
    val keyName: String?,
    val tempoBpm: Int?,
    val status: PieceStatus,
)

/** One tile of the sheet strip. */
data class SheetTile(
    val pageId: Long,
    /** From one: what the corner of the tile says. */
    val number: Int,
    /** Null when the file is gone: the tile shows paper. */
    val thumbPath: String?,
)

data class PieceState(
    /** True until the piece has been read once. */
    val loading: Boolean,
    val header: PieceHeader?,
    val pages: List<SheetTile>,
    /** Photos being copied in right now: placeholder tiles at the end of the strip. */
    val importing: Int,
    val notes: String,
    val statusMenuOpen: Boolean,
    /** After this many lines the notes fold (spec 5.9). */
    val notesCollapsedLines: Int,
)

sealed interface PieceIntent {
    data object BackClicked : PieceIntent

    data object EditClicked : PieceIntent

    /** «Добавить заметку»: the form, with the cursor in the notes. */
    data object AddNotesClicked : PieceIntent

    data object StatusChipClicked : PieceIntent

    data class StatusSelected(val status: PieceStatus) : PieceIntent

    data object StatusMenuDismissed : PieceIntent

    /** Opens the music stand at that page (from zero). */
    data class PageClicked(val index: Int) : PieceIntent

    /** The system picker returned these content uris. */
    data class PhotosPicked(val uris: List<String>) : PieceIntent

    data object CameraClicked : PieceIntent

    /** The system camera came back; [saved] is false when the user backed out. */
    data class CameraFinished(val saved: Boolean) : PieceIntent
}

sealed interface PieceEffect {
    data object Close : PieceEffect

    data class OpenForm(val pieceId: Long, val focusNotes: Boolean) : PieceEffect

    data class OpenStand(val pieceId: Long, val pageIndex: Int) : PieceEffect

    /** [filePath] is where the shot has to be written; the route turns it into a content uri. */
    data class LaunchCamera(val filePath: String) : PieceEffect

    data object ShowPhotoFailed : PieceEffect
}
