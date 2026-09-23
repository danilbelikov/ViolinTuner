package com.violinjourney.app.core.audio.recording

import java.io.File

/** Where the sound of sessions lives. Sessions store the bare file name, not a path. */
interface SessionAudioFiles {
    fun newFile(): File

    /** Null when the file is gone (cleared storage, restored backup without files). */
    fun existing(name: String): File?

    fun delete(name: String)

    /**
     * Removes files no session points at, e.g. after a crash in the middle of a take. Files
     * touched within [minAgeMs] are left alone: one of them may be the take being recorded.
     * Whatever is not plain session audio — a video, its thumbnail, a half-copied import — is
     * given longer (spec 5.13): it may be the only copy of a shot.
     */
    fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long)
}
