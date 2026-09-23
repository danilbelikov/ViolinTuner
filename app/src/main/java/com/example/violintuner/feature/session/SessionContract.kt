package com.example.violintuner.feature.session

import com.example.violintuner.core.audio.playback.PlayerState
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.StringFinger
import com.example.violintuner.feature.sound.SoundCaption

/** A note on the piano roll. Times are from the session start. */
data class RollSegment(
    val note: Note,
    val startMs: Long,
    val endMs: Long,
    val meanCents: Double,
    val minCents: Double,
    val maxCents: Double,
    /** Zone of [meanCents]: the color of the bar. */
    val zone: Zone,
    /** False when the note wanders by more than the "near" limit around its mean, whatever the mean. */
    val steady: Boolean,
    /** Deviation over time inside the note, one value per sample. */
    val contour: List<Double>,
    val position: StringFinger,
)

data class ProblemNoteUi(val note: Note, val string: ViolinString, val meanCents: Double, val zone: Zone)

/** Everything the session screen shows (spec 3.10). Numbers are raw; the screen formats them. */
data class SessionContent(
    /** Null = default name built from the start date. */
    val title: String?,
    /** Title of the piece this session is a take of (spec 3.15): part of its default name. */
    val pieceTitle: String? = null,
    /** A take of a piece that still exists: only it can be marked as the best (spec 3.21). */
    val pieceId: Long? = null,
    /** This take carries the «лучший» mark of its piece: the star of the top bar is filled. */
    val best: Boolean = false,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val toleranceCents: Double,
    val scorePercent: Int,
    val nearPercent: Int,
    val offPercent: Int,
    val maeCents: Double,
    val biasCents: Double,
    /** Null when the bias is too small to mention ("без смещения"). */
    val biasZone: Zone?,
    /** Score per string, null where nothing was played; the zone is the color of the number. */
    val perString: Map<ViolinString, Pair<Int, Zone>?>,
    val problemNotes: List<ProblemNoteUi>,
    /** Notes of the roll, highest first: the rows. */
    val rollNotes: List<Note>,
    val segments: List<RollSegment>,
    val hasAudio: Boolean,
)

enum class SessionDialog { RENAME, DELETE }

sealed interface SessionState {
    data object Loading : SessionState

    /** Deleted meanwhile, or a stale link. */
    data object NotFound : SessionState

    data class Loaded(
        val content: SessionContent,
        /** Index in [SessionContent.segments] of the note whose details are open. */
        val selectedSegment: Int? = null,
        val dialog: SessionDialog? = null,
        /** Null when the session has no sound, its file is gone or cannot be played. */
        val player: PlayerState? = null,
        /** The player waits for the backing's sound to be made (spec 5.25): «Готовим минусовку…», and the picture waits too. */
        val preparingBacking: Boolean = false,
        /** What the row «Звук» under the player says; there with the player only. */
        val sound: SoundRow? = null,
        /** Null for a recording that is sound only (spec 3.19). */
        val video: VideoUi? = null,
        /** The video fills the screen; the rest of the screen waits underneath. */
        val fullscreen: Boolean = false,
    ) : SessionState
}

sealed interface SessionIntent {
    data object BackClicked : SessionIntent

    data object PlayPauseClicked : SessionIntent

    /** The slider was released at this position. */
    data class SeekRequested(val positionMs: Long) : SessionIntent

    /** A/B of the player: true — the recording as recorded, false — with its processing. */
    data class OriginalSelected(val original: Boolean) : SessionIntent

    /** «с минусовкой / только скрипка» of a take under a backing (spec 3.32). */
    data class BackingHeardSelected(val heard: Boolean) : SessionIntent

    /** The row «Звук» under the player. */
    data object SoundClicked : SessionIntent

    /** The icon in the top bar; there for a recording with sound only. */
    data object ShareClicked : SessionIntent

    /** The star of the top bar, there for a take only: marks it as the best of its piece, or clears the mark. */
    data object BestClicked : SessionIntent

    /** The screen is no longer visible: the sound stops (spec 3.10). */
    data object ScreenStopped : SessionIntent

    data class SegmentClicked(val index: Int) : SessionIntent

    data object NoteSheetDismissed : SessionIntent

    data object RenameClicked : SessionIntent

    data class RenameConfirmed(val title: String) : SessionIntent

    data object DeleteClicked : SessionIntent

    data object DeleteConfirmed : SessionIntent

    data object DialogDismissed : SessionIntent

    /** «На весь экран» and «свернуть»; the system back in the mode is the latter. */
    data class FullscreenChanged(val fullscreen: Boolean) : SessionIntent

    /** «Смотреть это место» / «Слушать это место» of the note sheet: a second before the note, and play (spec 5.13). */
    data class PlaySegmentClicked(val index: Int) : SessionIntent
}

sealed interface SessionEffect {
    data object Close : SessionEffect

    data class OpenSound(val sessionId: Long) : SessionEffect

    data class Share(val sessionId: Long) : SessionEffect

    /** The take has just been marked as the best; [moved] when the mark was taken from another take. */
    data class ShowBestMarked(val moved: Boolean) : SessionEffect
}

/** The picture of a video take, as the screen needs it. */
data class VideoUi(
    /** The file is gone: «Видео не найдено — остался разбор». The sound went with it — it was the same file. */
    val lost: Boolean = false,
    /** As it is seen; zero until the file has been looked into. */
    val width: Int = 0,
    val height: Int = 0,
    /** A frame is on the surface: the placeholder may go. */
    val showing: Boolean = false,
    /** This device cannot decode the picture; the sound and the analysis are there all the same. */
    val undecodable: Boolean = false,
    val sizeBytes: Long = 0,
) {
    /** There is a picture to show: the frame, the full screen, «Смотреть это место». */
    val pictured: Boolean get() = !lost && !undecodable
}

/** How the recording is made to sound, in a line (spec 3.17): whose settings, and which. */
data class SoundRow(val caption: SoundCaption, val own: Boolean)
