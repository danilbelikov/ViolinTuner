package com.example.violintuner.core.data.profile

import java.io.File

/** Where the profile photo lives. The profile stores the bare file name, not a path (spec 6). */
interface AvatarFiles {
    /**
     * Copies the picture behind [sourceUri] into the app as a small square and returns its file
     * name; null when it cannot be read or is not a picture. Every import gets a new name, so
     * a changed photo is a changed name for whoever displays it.
     */
    suspend fun import(sourceUri: String): String?

    /** Null when the file is gone (cleared storage, restored backup without files). */
    fun existing(name: String): File?

    suspend fun delete(name: String)

    /** Removes every photo except [referenced]: what an interrupted import or replace left behind. */
    suspend fun deleteOrphans(referenced: String?)
}
