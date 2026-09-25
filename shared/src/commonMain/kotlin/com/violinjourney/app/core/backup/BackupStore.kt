package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile

/** What there is in the app: how much of what, and what it weighs. */
data class BackupContents(val counts: BackupCounts, val bytes: Map<BackupPart, Long>) {
    fun bytesOf(parts: Set<BackupPart>): Long = bytes.filterKeys { it in parts }.values.sum()

    val totalBytes: Long get() = bytes.values.sum()
}

/** A copy ready to be written: its passport and its files, the database among them as a snapshot taken just now. */
class PreparedBackup(val manifest: BackupManifest, val entries: List<BackupEntry>)

/** The data of the app as a copy sees them. The real ones are the platforms'; tests have a fake. */
interface BackupStore {
    suspend fun contents(): BackupContents

    /** Takes the snapshot of the database — consistent, all at once — and lists the files of [parts]. */
    suspend fun prepare(parts: Set<BackupPart>): PreparedBackup

    /** Removes the snapshot [prepare] left. */
    fun cleanUp()

    val databaseVersion: Int

    fun freeBytes(): Long

    /** A fresh, empty folder beside the data to unpack a copy into. */
    fun newStaging(): PlatformFile

    fun discardStaging()

    /** The unpacked copy is whole: from now on the next start of the process puts it in place. */
    fun markStagingReady()

    /** The way without a safety net: the media go first — that is where the room is. */
    fun deleteMedia()

    /** «Начать с чистого приложения»: the next start wipes everything. */
    fun markWipe()

    /** Where an archive is built for «Отправить…»: under `cache/share/`, the only place the file provider hands out. */
    fun shareFile(fileName: String): PlatformFile
}

/** The place the person picked, as the system hands it out — a content uri. */
interface BackupDocuments {
    fun openOutput(uri: String): ByteOutput?

    fun openInput(uri: String): ByteInput?

    /** An unfinished file is ours to remove. */
    fun delete(uri: String)

    /** «Загрузки», the name of a folder — as far as the provider tells; null when it does not. */
    fun placeOf(uri: String): String?

    fun nameOf(uri: String): String?

    fun sizeOf(uri: String): Long?
}
