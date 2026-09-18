package com.example.violintuner.core.data.repertoire

import java.io.File

/** Where the photographed sheet music lives. Pages store bare file names, not paths (spec 6). */
interface SheetFiles {
    /** A page and its thumbnail, both already on disk. */
    data class Stored(val fileName: String, val thumbFileName: String)

    /**
     * Copies the picture behind [sourceUri] into the app, upright and no larger than small print
     * needs, with a thumbnail beside it; null when it cannot be read or is not a picture.
     */
    suspend fun import(sourceUri: String): Stored?

    /** Null when the file is gone (cleared storage, restored backup without files). */
    fun existing(name: String): File?

    suspend fun delete(names: Collection<String>)

    /**
     * Removes files no page points at. Files touched within [minAgeMs] are left alone: one of
     * them may belong to an import that has not written its row yet.
     */
    suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long)

    /** A fresh temporary file for the system camera to write a shot into; import it, then delete it. */
    fun newCameraFile(): File
}
