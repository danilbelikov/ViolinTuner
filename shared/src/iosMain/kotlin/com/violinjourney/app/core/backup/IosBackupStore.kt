package com.violinjourney.app.core.backup

import androidx.room.execSQL
import androidx.room.useWriterConnection
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.deleteAll
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.listNames
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

/**
 * The data of the iOS app as a copy sees them — as `AppBackupStore` on Android, and in the same archive: `db/violin.db`,
 * `settings/…`, `profile/`, `repertoire/`, `sessions/`, `backings/`. Everything lies in Application Support; the
 * database is taken with `VACUUM INTO`, one consistent file, while the app goes on.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackupStore(
    private val data: PlatformFile,
    private val database: AppDatabase,
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val practice: PracticeRepository,
    private val trophies: TrophyRepository,
    private val progressConfig: ProgressConfig,
    private val clock: WallClock,
    private val io: CoroutineDispatcher,
) : BackupStore {
    private val caches = PlatformFile(NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String)
    private val snapshotDir = caches.child(SNAPSHOT_DIR)
    private val databaseFile = data.child(AppDatabase.FILE_NAME)
    private val settingsFile = data.child(IosRestoreSwap.SETTINGS_FILE)

    override val databaseVersion: Int = AppDatabase.VERSION

    override suspend fun contents(): BackupContents = withContext(io) {
        val all = sessions.sessions.first()
        val entries = practice.entries.first()
        val counts = BackupCounts(
            sessions = all.size,
            takes = all.count { it.pieceId != null },
            pieces = repertoire.pieces.first().size,
            pages = repertoire.pages.first().size,
            practiceDays = entries.map { it.date }.distinct().size,
            trophies = trophies.trophies.first().size,
            level = Progress.levelOf(Progress.totalMs(entries), progressConfig).level,
            withSound = all.count { it.audioPath != null && it.videoPath == null },
            videos = all.count { it.videoPath != null },
        )
        val dataBytes = databaseFile.sizeBytes() + data.child(AppDatabase.FILE_NAME + WAL).sizeBytes() + settingsFile.sizeBytes() +
            mediaOf(BackupPart.DATA).sumOf { it.second.sizeBytes() }
        BackupContents(
            counts,
            mapOf(BackupPart.DATA to dataBytes) +
                listOf(BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO).associateWith { part -> mediaOf(part).sumOf { it.second.sizeBytes() } },
        )
    }

    /** The folder a file lies in and the file — the folder is where it goes in the copy. */
    private fun mediaOf(part: BackupPart): List<Pair<String, PlatformFile>> = when (part) {
        BackupPart.DATA -> listing(BackupPaths.PROFILE)
        BackupPart.SHEETS -> listing(BackupPaths.SHEETS)
        // the sound of takes and the backings they were made under (spec 3.32): both are sound
        BackupPart.AUDIO -> listing(BackupPaths.SESSIONS).filter { it.second.path.endsWith(BackupPaths.AUDIO_EXTENSION) } + listing(BackupPaths.BACKINGS)
        // the video and the thumbnail that stands beside it
        BackupPart.VIDEO -> listing(BackupPaths.SESSIONS).filterNot { it.second.path.endsWith(BackupPaths.AUDIO_EXTENSION) }
    }

    // a half-written import is not data yet
    private fun listing(dir: String): List<Pair<String, PlatformFile>> =
        data.child(dir).listNames().filterNot { it.endsWith(PARTIAL) }.sorted().map { dir to data.child(dir).child(it) }

    override suspend fun prepare(parts: Set<BackupPart>): PreparedBackup = withContext(io) {
        val snapshot = snapshotDatabase()
        val now = contents()
        val entries = buildList {
            add(BackupEntry("${BackupPaths.DATABASE}/${AppDatabase.FILE_NAME}", BackupPart.DATA, snapshot.sizeBytes()) { snapshot.openInput() })
            if (settingsFile.exists()) {
                add(BackupEntry("${BackupPaths.SETTINGS}/${IosRestoreSwap.SETTINGS_FILE}", BackupPart.DATA, settingsFile.sizeBytes()) { settingsFile.openInput() })
            }
            // data first, then by weight: what matters most is in the archive soonest
            listOf(BackupPart.DATA, BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO).filter { it in parts || it == BackupPart.DATA }.forEach { part ->
                mediaOf(part).forEach { (dir, file) ->
                    add(BackupEntry("$dir/${file.path.substringAfterLast('/')}", part, file.sizeBytes()) { file.openInput() })
                }
            }
        }
        val manifest = BackupManifest(
            formatVersion = BackupManifest.FORMAT_VERSION,
            appVersion = (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String).orEmpty(),
            databaseVersion = databaseVersion,
            createdAtEpochMs = clock.millis(),
            device = UIDevice.currentDevice.model,
            parts = parts + BackupPart.DATA,
            counts = now.counts,
            bytes = now.bytes,
        )
        PreparedBackup(manifest, entries)
    }

    /** One file, consistent, whatever the app writes meanwhile: SQLite builds it itself. */
    private suspend fun snapshotDatabase(): PlatformFile {
        snapshotDir.deleteAll()
        snapshotDir.makeDirectories()
        val target = snapshotDir.child(AppDatabase.FILE_NAME)
        database.useWriterConnection { connection -> connection.execSQL("VACUUM INTO '${target.path.replace("'", "''")}'") }
        return target
    }

    override fun cleanUp() {
        snapshotDir.deleteAll()
    }

    override fun freeBytes(): Long =
        (NSFileManager.defaultManager.attributesOfFileSystemForPath(data.path, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0

    override fun newStaging(): PlatformFile = data.child(IosRestoreSwap.STAGING).also {
        it.deleteAll()
        it.makeDirectories()
    }

    override fun discardStaging() {
        data.child(IosRestoreSwap.STAGING).deleteAll()
    }

    override fun markStagingReady() {
        val staging = data.child(IosRestoreSwap.STAGING)
        // a copy without video, or without a photo, replaces those folders too — with empty ones
        IosRestoreSwap.MEDIA_DIRS.forEach { staging.child(it).makeDirectories() }
        data.child(IosRestoreSwap.READY_MARK).openOutput()?.close()
    }

    override fun deleteMedia() {
        listOf(BackupPaths.SESSIONS, BackupPaths.SHEETS, BackupPaths.BACKINGS, WAVEFORMS_DIR).forEach { data.child(it).deleteAll() }
    }

    override fun markWipe() {
        data.child(IosRestoreSwap.WIPE_MARK).openOutput()?.close()
    }

    override fun shareFile(fileName: String): PlatformFile {
        val folder = caches.child(SHARE_DIR).child(SHARE_BACKUP_DIR)
        folder.deleteAll()
        folder.makeDirectories()
        return folder.child(fileName)
    }

    private companion object {
        const val WAVEFORMS_DIR = "waveforms"
        const val SNAPSHOT_DIR = "backup-snapshot"
        const val SHARE_DIR = "share"
        const val SHARE_BACKUP_DIR = "backup"
        const val WAL = "-wal"
        const val PARTIAL = ".part"
    }
}

/**
 * Puts an unpacked copy in the place of the data on iOS (spec 3.20, 5.14), as `RestoreSwap` does on Android — before
 * the database is opened. Every step does only what is left, so a swap cut short is finished by the next run.
 */
internal object IosRestoreSwap {
    const val STAGING = "restore-staging"
    const val READY_MARK = "restore-ready"
    const val WIPE_MARK = "restore-wipe"
    const val SETTINGS_FILE = "user_settings.preferences_pb"
    private const val WAVEFORMS_DIR = "waveforms"
    val MEDIA_DIRS = listOf(BackupPaths.PROFILE, BackupPaths.SHEETS, BackupPaths.SESSIONS, BackupPaths.BACKINGS)

    enum class Outcome { NOTHING, RESTORED, WIPED }

    fun applyIfPending(data: PlatformFile): Outcome {
        val staging = data.child(STAGING)
        return when {
            data.child(WIPE_MARK).exists() -> {
                staging.deleteAll()
                data.child(READY_MARK).deleteAll()
                deleteDatabase(data)
                data.child(SETTINGS_FILE).deleteAll()
                MEDIA_DIRS.forEach { data.child(it).deleteAll() }
                data.child(WAVEFORMS_DIR).deleteAll()
                data.child(WIPE_MARK).deleteAll()
                Outcome.WIPED
            }
            data.child(READY_MARK).exists() -> {
                swap(staging, data)
                staging.deleteAll()
                data.child(READY_MARK).deleteAll()
                Outcome.RESTORED
            }
            else -> {
                // unpacking that never got to its mark — a cancelled or a failed restore
                if (staging.exists()) staging.deleteAll()
                Outcome.NOTHING
            }
        }
    }

    private fun swap(staging: PlatformFile, data: PlatformFile) {
        val database = staging.child(BackupPaths.DATABASE).child(AppDatabase.FILE_NAME)
        if (database.exists()) {
            deleteDatabase(data)
            move(database, data.child(AppDatabase.FILE_NAME))
        }
        val settings = staging.child(BackupPaths.SETTINGS).child(SETTINGS_FILE)
        if (settings.exists()) {
            data.child(SETTINGS_FILE).deleteAll()
            move(settings, data.child(SETTINGS_FILE))
        }
        MEDIA_DIRS.forEach { name ->
            val from = staging.child(name)
            // gone from the staging folder means moved already, by a run that was cut short after this step
            if (from.exists()) {
                data.child(name).deleteAll()
                move(from, data.child(name))
            }
        }
        data.child(WAVEFORMS_DIR).deleteAll()
    }

    private fun deleteDatabase(data: PlatformFile) {
        listOf("", "-wal", "-shm", "-journal", ".lck").forEach { data.child(AppDatabase.FILE_NAME + it).deleteAll() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun move(from: PlatformFile, to: PlatformFile) {
        NSFileManager.defaultManager.moveItemAtPath(from.path, to.path, null)
    }
}
