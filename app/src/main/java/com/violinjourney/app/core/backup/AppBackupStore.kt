package com.violinjourney.app.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.room.RoomDatabase
import com.violinjourney.app.BuildConfig
import com.violinjourney.app.R
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.di.appDatabaseBuilder
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.settings.DataStoreBackupPrefs
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
    private val events: EventRepository,
    private val progressConfig: ProgressConfig,
    private val clock: WallClock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupStore {
    private val files = context.filesDir
    private val snapshotDir = File(context.cacheDir, SNAPSHOT_DIR)
    private val databaseFile get() = context.getDatabasePath(AppDatabase.FILE_NAME)
    private val settingsFile get() = File(File(files, RestoreSwap.DATASTORE_DIR), DataLayout.SETTINGS_FILE)

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
            events = events.events.first().size,
            ownKinds = events.kinds.first().count { it.ref is KindRef.Custom },
        )
        val data = databaseFile.length() + File(databaseFile.path + DatabaseSnapshot.WAL).length() + settingsFile.length() + mediaOf(BackupPart.DATA).sumOf { it.length() }
        BackupContents(
            counts,
            mapOf(BackupPart.DATA to data) + listOf(BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO).associateWith { part -> mediaOf(part).sumOf { it.length() } },
        )
    }

    /** Files of a part as they lie under `files/`; for [BackupPart.DATA] — the photo of the profile only, the database and the settings go apart. */
    private fun mediaOf(part: BackupPart): List<File> = when (part) {
        BackupPart.DATA -> listing(BackupPaths.PROFILE)
        BackupPart.SHEETS -> listing(BackupPaths.SHEETS)
        // the sound of takes and of files of events, and the backings they were made under (spec 3.32, plan D17): all of it is sound
        BackupPart.AUDIO -> listing(BackupPaths.SESSIONS).filter { BackupPaths.isSound(it.name) } + listing(BackupPaths.BACKINGS)
        // the video and the thumbnail that stands beside it
        BackupPart.VIDEO -> listing(BackupPaths.SESSIONS).filterNot { BackupPaths.isSound(it.name) }
    }

    // a half-written import is not data yet
    private fun listing(dir: String): List<File> = File(files, dir).listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(PARTIAL) }.sortedBy { it.name }

    override suspend fun prepare(parts: Set<BackupPart>): PreparedBackup = withContext(io) {
        val snapshot = snapshotDatabase()
        val now = contents()
        val entries = buildList {
            // the snapshot and the settings are the copy itself: vanished on the way, they fail it (spec 5.14)
            add(BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, snapshot.length(), required = true) { snapshot.inputStreamOrNull() })
            settingsFile.takeIf { it.isFile }?.let { file ->
                add(BackupEntry("${BackupPaths.SETTINGS}/${DataLayout.SETTINGS_FILE}", BackupPart.DATA, file.length(), required = true) { file.inputStreamOrNull() })
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

    private fun snapshotDatabase(): File {
        snapshotDir.deleteRecursively()
        snapshotDir.mkdirs()
        return DatabaseSnapshot.take(database, databaseFile, File(snapshotDir, AppDatabase.FILE_NAME))
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

    override suspend fun settleStaging(copyMadeAtEpochMs: Long) = withContext(io) {
        StagedCopy.settle(context, File(files, RestoreSwap.STAGING), copyMadeAtEpochMs, io)
    }

    override fun markStagingReady() {
        val staging = File(files, RestoreSwap.STAGING)
        // a copy without video, or without a photo, replaces those folders too — with empty ones
        DataLayout.MEDIA_DIRS.forEach { File(staging, it).mkdirs() }
        File(files, RestoreSwap.READY_MARK).createNewFile()
    }

    override fun deleteMedia() {
        listOf(DataLayout.SESSIONS, DataLayout.SHEETS, DataLayout.BACKINGS, DataLayout.WAVEFORMS).forEach { File(files, it).deleteRecursively() }
    }

    override fun markWipe() {
        File(files, RestoreSwap.WIPE_MARK).createNewFile()
    }

    override fun shareFile(fileName: String): File = File(File(File(context.cacheDir, SHARE_DIR), SHARE_BACKUP_DIR), fileName).also {
        it.parentFile?.deleteRecursively()
        it.parentFile?.mkdirs()
    }

    private companion object {
        const val SNAPSHOT_DIR = "backup-snapshot"
        const val SHARE_DIR = "share"
        const val SHARE_BACKUP_DIR = "backup"
        const val PARTIAL = ".part"
    }
}

/**
 * The database of a copy (spec 5.14): the file of the live database [database] and its journal are copied under an
 * exclusive lock — nobody writes meanwhile, so the pair is consistent — and the copy is then folded into one file at
 * [target]. `VACUUM INTO` would be neater and is not there before API 30. Apart from the store so that its test runs this
 * very code on a database of its own.
 */
internal object DatabaseSnapshot {
    const val WAL = "-wal"
    private const val SHM = "-shm"
    private const val JOURNAL = "-journal"

    fun take(database: RoomDatabase, live: File, target: File): File {
        val writable = database.openHelper.writableDatabase
        writable.beginTransaction()
        try {
            live.copyTo(target, overwrite = true)
            File(live.path + WAL).takeIf { it.isFile }?.copyTo(File(target.path + WAL), overwrite = true)
        } finally {
            writable.endTransaction()
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            // one file, whatever mode the next reader opens it in
            copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        listOf(WAL, SHM, JOURNAL).forEach { File(target.path + it).delete() }
        return target
    }
}

class AppBackupDocuments @Inject constructor(@ApplicationContext private val context: Context) : BackupDocuments {
    private val resolver get() = context.contentResolver

    // «wt» empties a file the person chose to replace, where «w» may leave the tail of the old archive after the new one;
    // a provider that knows no «t» gets the «w» it always had
    override fun openOutput(uri: String): OutputStream? =
        attempt("open for writing", uri) { resolver.openOutputStream(uri.toUri(), "wt") } ?: attempt("open for writing", uri) { resolver.openOutputStream(uri.toUri(), "w") }

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

/**
 * An unpacked copy opened the way the next start will open it, before its mark is left (spec 5.14): a copy whose database
 * does not open — a migration that does not take it, a schema that does not match, pages that do not pass their check — is
 * a failed restore with the data in place, not an app that falls at every start after the swap.
 */
internal object StagedCopy {
    private const val TAG = "StagedCopy"
    private const val CHECK_PASSED = "ok"

    suspend fun settle(context: Context, staging: File, copyMadeAtEpochMs: Long, io: CoroutineDispatcher) {
        val database = File(File(staging, BackupPaths.DATABASE), AppDatabase.FILE_NAME)
        if (!database.isFile) throw IOException("no database among what was unpacked")
        // A file that is no database, or a damaged one, is told first, by a connection whose handler of corruption leaves
        // the file alone: Android's own handler deletes such a file and starts an empty database, which would pass every
        // check after it — and put an empty database in the place of the person's data.
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS) { /* the file stays as it is */ }.use { raw ->
            raw.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != CHECK_PASSED) throw IOException("the unpacked database does not pass its check")
            }
        }
        // Then as the app will open it: the same builder, the same migrations, the same check of the schema. TRUNCATE, not
        // the journal of the app: once closed the file is whole alone, and the swap moves only the file.
        val opened = appDatabaseBuilder(context, database.path).setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
        try {
            opened.openHelper.writableDatabase
        } finally {
            opened.close()
        }
        // a copy without settings brings none: no file is made here, or the swap would replace the settings with an empty one
        val settings = File(File(staging, BackupPaths.SETTINGS), DataLayout.SETTINGS_FILE)
        if (settings.isFile) {
            DataStoreBackupPrefs.stamp(settings.path, copyMadeAtEpochMs, io) { Log.w(TAG, "the settings of the copy could not be read and start over", it) }
        }
    }
}
