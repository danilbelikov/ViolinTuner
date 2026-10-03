package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.io.PlatformFile

/** Where the sound of sessions lives. Sessions store the bare file name, not a path. */
interface SessionAudioFiles {
    fun newFile(): PlatformFile

    /** Null when the file is gone (cleared storage, restored backup without files). */
    fun existing(name: String): PlatformFile?

    fun delete(name: String)

    /**
     * The thumbnail beside the video stored under [name] (spec 3.38): the frame its tile shows in the lists; null without one. The
     * thumbnails are the videos' ([com.violinjourney.app.core.recording.video.VideoFiles] makes them); a store that keeps no videos
     * has none.
     */
    fun thumbOf(name: String): PlatformFile? = null

    /**
     * Removes files no session points at, e.g. after a crash in the middle of a take. Files
     * touched within [minAgeMs] are left alone: one of them may be the take being recorded.
     * Whatever is not plain session audio — a video, its thumbnail, a half-copied import — is
     * given longer (spec 5.13): it may be the only copy of a shot.
     */
    fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long)
}
