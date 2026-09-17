package com.example.violintuner.core.audio.recording

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
     */
    fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long)
}
