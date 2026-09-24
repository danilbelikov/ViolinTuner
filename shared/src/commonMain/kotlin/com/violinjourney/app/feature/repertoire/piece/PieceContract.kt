package com.violinjourney.app.feature.repertoire.piece

import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.Selection
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.feature.history.SelectionRules

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

/** One take in the list of the piece: the card of «Записи» plus what only matters here. */
data class TakeItem(
    val card: HistoryCard,
    /** Marked by the player as the best take of the piece: it stands first (spec 3.21). */
    val best: Boolean,
    /** Recorded a moment ago: highlighted until it settles into the list. */
    val isNew: Boolean,
    /** Made under the backing (spec 3.32): the card says so. */
    val underBacking: Boolean = false,
)

/** «последний 82 % · максимум 88 % · 6 дублей» and the little chart; there from two takes on. */
data class TakeProgress(val lastScore: Int, val maxScore: Int, val scores: List<Int>)

/** Why the microphone cannot be listened to right now; shown as a small line by the recording strip. */
enum class TakeProblem { TOO_NOISY, MIC_UNAVAILABLE }

/**
 * The recording of a take (spec 3.15). Blind on purpose: no note, no zone, no cents — a dot, a
 * timer and a neutral row of loudness. Kept apart from [PieceState]: it changes twenty times a
 * second while a take runs, and the rest of the screen has no business recomposing with it.
 */
data class TakeState(
    val recording: Boolean,
    /** Whole seconds: the timer shows nothing finer, and equal states stay equal within a second. */
    val elapsedSeconds: Long,
    /** Loudness of the last moments, 0..1, newest last. */
    val levels: List<Float>,
    val problem: TakeProblem?,
    /** False = the permission was refused: the row explains and offers to grant it. Null = not known yet. */
    val micPermission: Boolean?,
    /** Under a backing (spec 3.32): how far it has played and how long it is, for the thin bar under the timer. */
    val backingPlayedMs: Long? = null,
    val backingDurationMs: Long? = null,
) {
    companion object {
        fun idle(micPermission: Boolean?, bars: Int) =
            TakeState(recording = false, elapsedSeconds = 0, levels = List(bars) { 0f }, problem = null, micPermission = micPermission)
    }
}

data class PieceState(
    /** True until the piece has been read once. */
    val loading: Boolean,
    val header: PieceHeader?,
    val pages: List<SheetTile>,
    /** Photos being copied in right now: placeholder tiles at the end of the strip. */
    val importing: Int,
    val notes: String,
    /** The best one first, the rest newest first. */
    val takes: List<TakeItem>,
    /** Null with fewer than two takes. */
    val progress: TakeProgress?,
    val statusMenuOpen: Boolean,
    /** After this many lines the notes fold (spec 5.9). */
    val notesCollapsedLines: Int,
    /** Picking several takes to delete (spec 3.18). */
    val selection: Selection = Selection(),
    /** The element is a scale (spec 3.22): its notes are drawn by the app and stand first, above the photos. */
    val scale: Scale? = null,
    /** An element of «Гаммы», «Этюды» or «Штрихи»: the third step of its status reads «Выучено». */
    val exercise: Boolean = false,
) {
    val allSelected: Boolean get() = SelectionRules.allSelected(selection, takes.map { it.card.id })
}

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

    /** Starts a take, or stops the running one. */
    data object RecordClicked : PieceIntent

    /** The same from the music stand: never under the backing — that is the piece screen's (spec 3.32). */
    data object StandRecordClicked : PieceIntent

    data object GrantMicClicked : PieceIntent

    /** Reported by the route on every resume and after the system dialog. */
    data class MicPermissionChanged(val granted: Boolean) : PieceIntent

    /** Back on screen: what was thrown away while the app was away is made ready again. */
    data object ScreenResumed : PieceIntent

    data class TakeClicked(val sessionId: Long) : PieceIntent

    /** «Отметить лучшим» / «Снять отметку „лучший“» of the take's «⋯»: marks it, or clears the mark it has. */
    data class BestToggled(val sessionId: Long) : PieceIntent

    /** Everything of the selection mode; a plain [TakeClicked] inside it picks the take. */
    data class Select(val intent: SelectionIntent) : PieceIntent

    /** «Снять видео»: the system camera (spec 3.19). */
    data object VideoShootClicked : PieceIntent

    /** The app's own camera — for a piece with a backing, under it while the chip is on (spec 3.32). */
    data object OwnCameraClicked : PieceIntent

    /** The system camera came back; [saved] is false when the player backed out. */
    data class VideoShotFinished(val saved: Boolean) : PieceIntent

    /** The system picker returned this video; null when nothing was picked. */
    data class VideoPicked(val uri: String?) : PieceIntent

    data object VideoImportCancelClicked : PieceIntent

    data object VideoImportContinueClicked : PieceIntent

    data object VideoImportSendClicked : PieceIntent

    /** «Удалить» of a shot that did not become a take, and «Понятно» of any other failure. */
    data object VideoImportDismissed : PieceIntent

    /** «Добавить минусовку» and «Заменить»: the system picker of files (spec 3.32). */
    data object BackingAddClicked : PieceIntent

    /** The picker came back; null — nothing was picked. */
    data class BackingPicked(val uri: String?) : PieceIntent

    data object BackingPreviewClicked : PieceIntent

    data object BackingRemoveClicked : PieceIntent

    data object BackingRemoveConfirmed : PieceIntent

    data object BackingRemoveDismissed : PieceIntent

    data object BackingChipToggled : PieceIntent

    data object BackingProblemDismissed : PieceIntent

    /** «Проверить»: the sheet «Настроим наушники». */
}

sealed interface PieceEffect {
    data object Close : PieceEffect

    /** [scale] — the element is a scale: it has a form of its own. */
    data class OpenForm(val pieceId: Long, val focusNotes: Boolean, val scale: Boolean = false) : PieceEffect

    data class OpenStand(val pieceId: Long, val pageIndex: Int) : PieceEffect

    /** [filePath] is where the shot has to be written; the route turns it into a content uri. */
    data class LaunchCamera(val filePath: String) : PieceEffect

    data object ShowPhotoFailed : PieceEffect

    data object RequestMicPermission : PieceEffect

    /** A take stopped by the player had not a single note in it. */
    data object ShowNoNotesRecorded : PieceEffect

    data class OpenSession(val sessionId: Long) : PieceEffect

    /** [filePath] is where the video has to be written; the route turns it into a content uri. */
    data class LaunchVideoCamera(val filePath: String) : PieceEffect

    /** A shot that did not become a take, on its way to the system share sheet; [filePath] is under `cache/share/`. */
    data class ShareVideo(val filePath: String) : PieceEffect

    /** The system picker of audio files, for a backing (spec 3.32). */
    data object PickBackingFile : PieceEffect

    /** «Снять под минусовку»: the app's own camera (spec 3.32). */
    data class OpenCapture(val pieceId: Long) : PieceEffect
}

/** What the chain hands the screen for a frame of a blind take: how loud, and what is wrong, if anything. */
data class BlindShown(val levels: List<Float>, val problem: TakeProblem?)
