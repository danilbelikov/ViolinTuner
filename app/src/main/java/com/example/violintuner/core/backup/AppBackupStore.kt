package com.example.violintuner.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.example.violintuner.BuildConfig
import com.example.violintuner.R
import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.practice.PracticeRepository
import com.example.violintuner.core.domain.progress.Progress
import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.TrophyRepository
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AppBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val practice: PracticeRepository,
    private val trophies: TrophyRepository,
    private val progressConfig: ProgressConfig,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupStore {
    private val files = context.filesDir
    private val snapshotDir = File(context.cacheDir, SNAPSHOT_DIR)
    private val databaseFile get() = context.getDatabasePath(AppDatabase.FILE_NAME)
    private val settingsFile get() = File(File(files, DATASTORE_DIR), RestoreSwap.SETTINGS_FILE)

    override val databaseVersion: Int get() = database.openHelper.readableDatabase.version

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
        val data = databaseFile.length() + File(databaseFile.path + WAL).length() + settingsFile.length() + mediaOf(BackupPart.DATA).sumOf { it.length() }
        BackupContents(
            counts,
            mapOf(BackupPart.DATA to data) + listOf(BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO).associateWith { part -> mediaOf(part).sumOf { it.length() } },
        )
    }

    /** Files of a part as they lie under `files/`; for [BackupPart.DATA] — the photo of the profile only, the database and the settings go apart. */
    private fun mediaOf(part: BackupPart): List<File> = when (part) {
        BackupPart.DATA -> listing(BackupPaths.PROFILE)
        BackupPart.SHEETS -> listing(BackupPaths.SHEETS)
        // the sound of takes and the backings they were made under (spec 3.32): both are sound
        BackupPart.AUDIO -> listing(BackupPaths.SESSIONS).filter { it.name.endsWith(BackupPaths.AUDIO_EXTENSION) } + listing(BackupPaths.BACKINGS)
        // the video and the thumbnail that stands beside it
        BackupPart.VIDEO -> listing(BackupPaths.SESSIONS).filterNot { it.name.endsWith(BackupPaths.AUDIO_EXTENSION) }
    }

    // a half-written import is not data yet
    private fun listing(dir: String): List<File> = File(files, dir).listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(PARTIAL) }.sortedBy { it.name }

    override suspend fun prepare(parts: Set<BackupPart>): PreparedBackup = withContext(io) {
        val snapshot = snapshotDatabase()
        val now = contents()
        val entries = buildList {
            add(BackupEntry("${BackupPaths.DATABASE}/${RestoreSwap.DATABASE_FILE}", BackupPart.DATA, snapshot.length()) { snapshot.inputStreamOrNull() })
            settingsFile.takeIf { it.isFile }?.let { file ->
                add(BackupEntry("${BackupPaths.SETTINGS}/${RestoreSwap.SETTINGS_FILE}", BackupPart.DATA, file.length()) { file.inputStreamOrNull() })
            }
            // data first, then by weight: what matters most is in the archive soonest
            listOf(BackupPart.DATA, BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO).filter { it in parts || it == BackupPart.DATA }.forEach { part ->
                // the folder a file lies in under `files/` is the folder it goes into in the copy
                mediaOf(part).forEach { file -> add(BackupEntry("${file.parentFile?.name}/${file.name}", part, file.length()) { file.inputStreamOrNull() }) }
            }
        }
        val manifest = BackupManifest(
            formatVersion = BackupManifest.FORMAT_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            databaseVersion = databaseVersion,
            createdAtEpochMs = clock.millis(),
            device = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ").replaceFirstChar { it.uppercase() },
            parts = parts + BackupPart.DATA,
            counts = now.counts,
            bytes = now.bytes,
        )
        PreparedBackup(manifest, entries)
    }

    // A file deleted since the list was made is not an error of the copy (spec 3.20): the writer skips it.
    private fun File.inputStreamOrNull(): InputStream? = try {
        inputStream()
    } catch (_: FileNotFoundException) {
        null
    }

    /**
     * The database and its journal are copied under an exclusive lock — nobody writes meanwhile, so
     * the pair is consistent — and the copy is then folded into one file. `VACUUM INTO` would be
     * neater and is not there before API 30.
     */
    private fun snapshotDatabase(): File {
        snapshotDir.deleteRecursively()
        snapshotDir.mkdirs()
        val target = File(snapshotDir, RestoreSwap.DATABASE_FILE)
        val live = database.openHelper.writableDatabase
        live.beginTransaction()
        try {
            databaseFile.copyTo(target, overwrite = true)
            File(databaseFile.path + WAL).takeIf { it.isFile }?.copyTo(File(target.path + WAL), overwrite = true)
        } finally {
            live.endTransaction()
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            // one file, whatever mode the next reader opens it in
            copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        listOf(WAL, "-shm", "-journal").forEach { File(target.path + it).delete() }
        return target
    }

    override fun cleanUp() {
        snapshotDir.deleteRecursively()
    }

    override fun freeBytes(): Long = StatFs(files.path).availableBytes

    override fun newStaging(): File = File(files, RestoreSwap.STAGING).also {
        it.deleteRecursively()
        it.mkdirs()
    }

    override fun discardStaging() {
        File(files, RestoreSwap.STAGING).deleteRecursively()
    }

    override fun markStagingReady() {
        val staging = File(files, RestoreSwap.STAGING)
        // a copy without video, or without a photo, replaces those folders too — with empty ones
        RestoreSwap.MEDIA_DIRS.forEach { File(staging, it).mkdirs() }
        File(files, RestoreSwap.READY_MARK).createNewFile()
    }

    override fun deleteMedia() {
        listOf(BackupPaths.SESSIONS, BackupPaths.SHEETS, BackupPaths.BACKINGS, WAVEFORMS_DIR).forEach { File(files, it).deleteRecursively() }
    }

    override fun markWipe() {
        File(files, RestoreSwap.WIPE_MARK).createNewFile()
    }

    override fun shareFile(fileName: String): File = File(File(File(context.cacheDir, SHARE_DIR), SHARE_BACKUP_DIR), fileName).also {
        it.parentFile?.deleteRecursively()
        it.parentFile?.mkdirs()
    }

    private companion object {
        const val DATASTORE_DIR = "datastore"
        const val WAVEFORMS_DIR = "waveforms"
        const val SNAPSHOT_DIR = "backup-snapshot"
        const val SHARE_DIR = "share"
        const val SHARE_BACKUP_DIR = "backup"
        const val WAL = "-wal"
        const val PARTIAL = ".part"
    }
}

class AppBackupDocuments @Inject constructor(@ApplicationContext private val context: Context) : BackupDocuments {
    private val resolver get() = context.contentResolver

    override fun openOutput(uri: String): OutputStream? = attempt("open for writing", uri) { resolver.openOutputStream(uri.toUri(), "w") }

    override fun openInput(uri: String): InputStream? = attempt("open for reading", uri) { resolver.openInputStream(uri.toUri()) }

    override fun delete(uri: String) {
        attempt("delete", uri) { DocumentsContract.deleteDocument(resolver, uri.toUri()) }
    }

    override fun placeOf(uri: String): String? = when (uri.toUri().authority) {
        "com.android.providers.downloads.documents" -> context.getString(R.string.backup_place_downloads)
        // «primary:Download/…», «1A2B-3C4D:Копии/…» — the folder the file went into
        "com.android.externalstorage.documents" -> attempt("read the path", uri) { DocumentsContract.getDocumentId(uri.toUri()) }
            ?.substringAfter(':', "")?.substringBeforeLast('/', "")?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
            ?.let { if (it == DOWNLOADS) context.getString(R.string.backup_place_downloads) else it }
        else -> null
    }

    override fun nameOf(uri: String): String? = column(uri, OpenableColumns.DISPLAY_NAME) { it.getString(0) }

    override fun sizeOf(uri: String): Long? = column(uri, OpenableColumns.SIZE) { if (it.isNull(0)) null else it.getLong(0) }

    private fun <T> column(uri: String, name: String, read: (android.database.Cursor) -> T?): T? = attempt("query $name", uri) {
        resolver.query(uri.toUri(), arrayOf(name), null, null, null)?.use { if (it.moveToFirst()) read(it) else null }
    }

    // A provider is somebody else's code: it answers with whatever exception it likes. Said once here, as "could not".
    private fun <T> attempt(what: String, uri: String, block: () -> T?): T? = try {
        block()
    } catch (e: IOException) {
        Log.w(TAG, "cannot $what: $uri", e)
        null
    } catch (e: SecurityException) {
        Log.w(TAG, "may not $what: $uri", e)
        null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "cannot $what: $uri", e)
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "cannot $what: $uri", e)
        null
    } catch (e: UnsupportedOperationException) {
        Log.w(TAG, "provider cannot $what: $uri", e)
        null
    }

    private companion object {
        const val TAG = "BackupDocuments"
        const val DOWNLOADS = "Download"
    }
}
