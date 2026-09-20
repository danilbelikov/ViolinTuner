package com.example.violintuner.core.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.data.AppDatabase
import com.example.violintuner.core.data.DatabaseMigrations
import com.example.violintuner.core.data.session.SessionEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The snapshot of a live Room database and the way back, on a real disk. Works in a folder of
 * its own and on a database of its own name: the data of whoever runs the tests are not touched.
 */
@RunWith(AndroidJUnit4::class)
class AppBackupStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "backup-store-test")
    private val files = File(root, "files")
    private val databases = File(root, "databases")
    private val liveFile = File(databases, RestoreSwap.DATABASE_FILE)
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        root.deleteRecursively()
        files.mkdirs()
        databases.mkdirs()
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, liveFile.path).addMigrations(*DatabaseMigrations.ALL).build()

    private fun session(title: String, audio: String?) = SessionEntity(
        title = title, startedAtEpochMs = 1_790_000_000_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 6.5, biasCents = -2.0, previewZones = "ININ", audioPath = audio,
    )

    /** The snapshot the store takes, reproduced on this database: copy under an exclusive lock, then fold the journal in. */
    private fun snapshot(): File {
        val target = File(root, "snapshot/violin.db").also { it.parentFile!!.mkdirs() }
        val live = database.openHelper.writableDatabase
        live.beginTransaction()
        try {
            liveFile.copyTo(target, overwrite = true)
            File(liveFile.path + "-wal").takeIf { it.isFile }?.copyTo(File(target.path + "-wal"), overwrite = true)
        } finally {
            live.endTransaction()
        }
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
            copy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        return target
    }

    @Test
    fun aCopyOfALiveDatabaseComesBackThroughTheSwapWithEverythingInIt() = runBlocking {
        // what is written a moment ago still lies in the journal, not in the main file: the snapshot has to carry it
        database.sessionDao().insert(session("Гаммы", "one.m4a"), bucketMs = 50, samples = ByteArray(30) { it.toByte() })
        database.sessionDao().insert(session("Этюд", null), bucketMs = 50, samples = ByteArray(9))
        val sound = ByteArray(200_000) { (it % 251).toByte() }
        File(files, "sessions").mkdirs()
        File(files, "sessions/one.m4a").writeBytes(sound)

        // whatever the schema is today: the copy carries it, and comes back with it
        val schema = database.openHelper.readableDatabase.version
        val snapshotFile = snapshot()
        assertFalse(File(snapshotFile.path + "-wal").exists())
        val manifest = BackupManifest(1, "test", schema, 1_790_000_000_000, "test", BackupPart.entries.toSet(), BackupCounts(sessions = 2), mapOf(BackupPart.DATA to snapshotFile.length(), BackupPart.AUDIO to sound.size.toLong()))
        val archive = ByteArrayOutputStream()
        BackupWriter.write(
            archive, manifest,
            listOf(
                BackupEntry("db/violin.db", BackupPart.DATA, snapshotFile.length()) { snapshotFile.inputStream() },
                BackupEntry("sessions/one.m4a", BackupPart.AUDIO, sound.size.toLong()) { File(files, "sessions/one.m4a").inputStream() },
            ),
        ) {}

        // life goes on after the copy: one recording deleted, another made
        database.sessionDao().delete(listOf(1L))
        database.sessionDao().insert(session("Лишняя", "two.m4a"), bucketMs = 50, samples = ByteArray(3))
        File(files, "sessions/one.m4a").delete()
        File(files, "sessions/two.m4a").writeBytes(ByteArray(10))
        database.close()

        // the restore: unpack beside the data, mark, and let the start of the next process swap
        val staging = File(files, RestoreSwap.STAGING)
        BackupReader.extract(ByteArrayInputStream(archive.toByteArray()), staging, manifest.totalBytes) {}
        RestoreSwap.MEDIA_DIRS.forEach { File(staging, it).mkdirs() }
        File(files, RestoreSwap.READY_MARK).createNewFile()
        assertEquals(RestoreSwap.Outcome.RESTORED, RestoreSwap.applyIfPending(files, databases))

        database = open()
        val sessions = database.sessionDao().observeAllOnce()
        assertEquals(listOf("Гаммы", "Этюд"), sessions.mapNotNull { it.title }.sorted())
        assertArrayEquals(ByteArray(30) { it.toByte() }, database.sessionDao().samples(sessions.first { it.title == "Гаммы" }.id)!!.data)
        assertArrayEquals(sound, File(files, "sessions/one.m4a").readBytes())
        assertFalse("what was made after the copy is gone with the data it belonged to", File(files, "sessions/two.m4a").exists())
        assertEquals(schema, database.openHelper.readableDatabase.version)
    }

    private suspend fun com.example.violintuner.core.data.session.SessionDao.observeAllOnce() = observeAll().first()
}
