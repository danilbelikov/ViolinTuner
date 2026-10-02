package com.violinjourney.app.core.recording

import com.violinjourney.app.core.domain.session.RecordingRibbon

/** Why a file on its way in did not become a recording (spec 3.19, 5.28). */
enum class ImportFailure {
    NO_SOUND, TOO_LONG, CANNOT_OPEN, NO_NOTES, NO_SPACE,

    /** Not a failure of the file: the player stopped the analysis of a shot and has to say what becomes of it. */
    STOPPED,
}

/**
 * What the sheet of a file on its way to becoming a recording shows (spec 3.19, plan D19): a video — shot or picked — or a sound from a
 * file (spec 3.35, 5.28). One importer of each kind for the app, one file at a time; [owner] is whose recording it is to be, and a
 * screen shows only what is on its way for itself.
 */
sealed interface MediaImport {
    data object Idle : MediaImport

    data class Working(
        val owner: TakeOwner,
        /** Shot a moment ago: it exists nowhere else, so stopping it is a question, not a button. */
        val shot: Boolean,
        val copying: Boolean,
        /** False while a short analysis is given the chance to end before anything is shown: nothing blinks. */
        val visible: Boolean,
        val thumbPath: String? = null,
        val percent: Int = 0,
        /** The notes found so far, measured against the whole file: the strip fills up like a progress bar. */
        val bars: RecordingRibbon = RecordingRibbon.EMPTY,
        /** Null until the speed has been measured. */
        val remainingSec: Int? = null,
        /** «Остановить разбор?» is up; the analysis goes on underneath. */
        val asking: Boolean = false,
    ) : MediaImport

    data class Failed(
        val owner: TakeOwner,
        val reason: ImportFailure,
        /** The shot that can still be sent somewhere before it is deleted; null for a picked file — its original is where it was. */
        val rescuePath: String? = null,
        val missingMb: Int? = null,
    ) : MediaImport
}

/** Whose file is on its way in now; null when none is. */
val MediaImport.owner: TakeOwner?
    get() = when (this) {
        MediaImport.Idle -> null
        is MediaImport.Working -> owner
        is MediaImport.Failed -> owner
    }

/** What [this] import is for [owner]: itself, or nothing when it is another's. */
fun MediaImport.of(owner: TakeOwner): MediaImport = if (this.owner == owner) this else MediaImport.Idle
