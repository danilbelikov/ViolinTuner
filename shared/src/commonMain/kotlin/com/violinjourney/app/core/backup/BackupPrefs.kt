package com.violinjourney.app.core.backup

import kotlinx.coroutines.flow.Flow

/** Remembers when the last copy was made. */
interface BackupPrefs {
    val lastBackupAtEpochMs: Flow<Long?>

    suspend fun setLastBackupAt(epochMs: Long)
}
