package com.example.violintuner.core.backup

import java.io.File

/**
 * Puts an unpacked copy in the place of the data (spec 3.20, 5.14). Runs at the very start of
 * the process, before anything has opened the database: Room lives in a singleton that cannot be
 * closed and reopened under a running app, and a swap that is half done when the process dies
 * has to be finished by the next one. Every step looks at what is still in the staging folder
 * and does only what is left, so running it twice is the same as running it once; the mark that
 * asks for the swap goes last. Plain files — no Android in here, which is what lets a JVM test
 * kill it half way.
 */
object RestoreSwap {
    const val STAGING = "restore-staging"
    const val READY_MARK = "restore-ready"
    const val WIPE_MARK = "restore-wipe"
    const val DATABASE_FILE = "violin.db"
    const val SETTINGS_FILE = "user_settings.preferences_pb"
    private const val DATASTORE_DIR = "datastore"

    /** Caches that belong to the data that is going: recomputed from the new data when asked for. */
    private const val WAVEFORMS_DIR = "waveforms"

    /** Folders of `files/` that a copy replaces whole. Unpacking creates every one of them in the staging folder, empty if need be. */
    val MEDIA_DIRS = listOf(BackupPaths.PROFILE, BackupPaths.SHEETS, BackupPaths.SESSIONS)

    enum class Outcome { NOTHING, RESTORED, WIPED }

    fun applyIfPending(filesDir: File, databasesDir: File): Outcome {
        val staging = File(filesDir, STAGING)
        val ready = File(filesDir, READY_MARK)
        val wipe = File(filesDir, WIPE_MARK)
        return when {
            wipe.exists() -> {
                staging.deleteRecursively()
                ready.delete()
                clear(filesDir, databasesDir)
                wipe.delete()
                Outcome.WIPED
            }
            ready.exists() -> {
                swap(staging, filesDir, databasesDir)
                staging.deleteRecursively()
                ready.delete()
                Outcome.RESTORED
            }
            else -> {
                // unpacking that never got to its mark — a cancelled or a failed restore, a process that died under it
                if (staging.exists()) staging.deleteRecursively()
                Outcome.NOTHING
            }
        }
    }

    private fun swap(staging: File, filesDir: File, databasesDir: File) {
        val database = File(File(staging, BackupPaths.DATABASE), DATABASE_FILE)
        if (database.exists()) {
            deleteDatabase(databasesDir)
            databasesDir.mkdirs()
            move(database, File(databasesDir, DATABASE_FILE))
        }
        val settings = File(File(staging, BackupPaths.SETTINGS), SETTINGS_FILE)
        if (settings.exists()) {
            val target = File(File(filesDir, DATASTORE_DIR), SETTINGS_FILE)
            target.parentFile?.mkdirs()
            target.delete()
            move(settings, target)
        }
        MEDIA_DIRS.forEach { name ->
            val from = File(staging, name)
            // gone from the staging folder means moved already, by a run that was cut short after this step
            if (from.exists()) {
                val to = File(filesDir, name)
                to.deleteRecursively()
                move(from, to)
            }
        }
        File(filesDir, WAVEFORMS_DIR).deleteRecursively()
    }

    private fun clear(filesDir: File, databasesDir: File) {
        deleteDatabase(databasesDir)
        File(File(filesDir, DATASTORE_DIR), SETTINGS_FILE).delete()
        MEDIA_DIRS.forEach { File(filesDir, it).deleteRecursively() }
        File(filesDir, WAVEFORMS_DIR).deleteRecursively()
    }

    private fun deleteDatabase(databasesDir: File) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(databasesDir, DATABASE_FILE + it).delete() }
    }

    // One volume, so this is a rename; the copy is for the day it is not.
    private fun move(from: File, to: File) {
        if (from.renameTo(to)) return
        from.copyRecursively(to, overwrite = true)
        from.deleteRecursively()
    }
}
