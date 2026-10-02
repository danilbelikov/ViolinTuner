package com.violinjourney.app.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * The data of the app as a copy sees them, in a folder of the test: a database of 1 000 bytes, a video of 400 KB. What
 * the manager asked for is written down in [calls], in order.
 */
internal class FakeBackupStore(private val root: File, private val now: Instant, private val counts: BackupCounts) : BackupStore {
    var free = Long.MAX_VALUE
    var cleaned = 0
    var readyMarked = false
    var wipeMarked = false
    var mediaDeleted = false
    var stagingDiscarded = 0
    var prepareCalls = 0

    /** What the platform throws beside the stream: SQLite refusing the snapshot, a folder that cannot be made, a mark… */
    var prepareFails: Exception? = null
    var stagingFails: Exception? = null
    var freeFails: Exception? = null
    var wipeFails: IOException? = null

    /** A database of the copy that the app would not open: a migration that is not there, a schema that does not match. */
    var settleFails: Exception? = null

    /** The date the unpacked settings were given; null — nothing was settled. */
    var settled: Long? = null

    /** «prepare», «settle», «deleteMedia», «mark» — in the order they came. */
    val calls = mutableListOf<String>()
    var onDeleteMedia: () -> Unit = {}
    var onSettle: () -> Unit = {}

    /** The next prepare waits for this deaf to cancellation, as a blocking write of the platform does. */
    var prepareGate: CompletableDeferred<Unit>? = null

    /** The next settle waits for this deaf to cancellation, as opening a big database does. */
    var settleGate: CompletableDeferred<Unit>? = null

    /** The next counting of what is in the app waits for this, as walking the folders of gigabytes of media does. */
    var contentsGate: CompletableDeferred<Unit>? = null

    /** The weight of the media now in the app: what the way without a net can free. */
    var mediaBytes = 400_000L

    val video = ByteArray(400_000) { (it % 97).toByte() }

    /** The snapshot of the database [prepare] takes and [cleanUp] removes: a copy written without it has no database. */
    private var snapshot = false

    override val databaseVersion = 6

    fun manifest(parts: Set<BackupPart>) = BackupManifest(
        1, "1.0", 6, now.toEpochMilliseconds(), "Pixel 7", parts + BackupPart.DATA, counts,
        mapOf(BackupPart.DATA to 1_000L, BackupPart.VIDEO to video.size.toLong()),
    )

    override suspend fun contents(): BackupContents {
        contentsGate?.let { gate ->
            contentsGate = null
            gate.await()
        }
        return BackupContents(counts, mapOf(BackupPart.DATA to 1_000L, BackupPart.VIDEO to mediaBytes))
    }

    override suspend fun prepare(parts: Set<BackupPart>): PreparedBackup {
        prepareCalls++
        calls += "prepare"
        prepareGate?.let { gate ->
            prepareGate = null
            withContext(NonCancellable) { gate.await() }
        }
        prepareFails?.let { throw it }
        snapshot = true
        return PreparedBackup(
            manifest(parts),
            listOfNotNull(
                BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, 1_000, required = true) { if (snapshot) ByteArrayInputStream(ByteArray(1_000) { 7 }) else null },
                BackupEntry("sessions/a.mp4", BackupPart.VIDEO, video.size.toLong()) { ByteArrayInputStream(video) }.takeIf { BackupPart.VIDEO in parts },
            ),
        )
    }

    override fun cleanUp() {
        cleaned++
        snapshot = false
    }

    override fun freeBytes() = freeFails?.let { throw it } ?: free

    override fun newStaging(): File = stagingFails?.let { throw it } ?: File(root, "staging").also {
        it.deleteRecursively()
        it.mkdirs()
    }

    override fun discardStaging() {
        stagingDiscarded++
        File(root, "staging").deleteRecursively()
    }

    override suspend fun settleStaging(copyMadeAtEpochMs: Long) {
        calls += "settle"
        settleGate?.let { gate ->
            settleGate = null
            withContext(NonCancellable) { gate.await() }
        }
        onSettle()
        settleFails?.let { throw it }
        settled = copyMadeAtEpochMs
    }

    override fun markStagingReady() {
        calls += "mark"
        readyMarked = true
    }

    override fun deleteMedia() {
        calls += "deleteMedia"
        onDeleteMedia()
        mediaDeleted = true
    }

    override fun markWipe() {
        wipeFails?.let { throw it }
        wipeMarked = true
    }

    override fun shareFile(fileName: String) = File(root, "share/$fileName").also { it.parentFile!!.mkdirs() }
}

/** Documents that live in memory; [failWith] breaks the writing half way. */
internal class FakeBackupDocuments : BackupDocuments {
    val written = HashMap<String, ByteArrayOutputStream>()
    val deleted = mutableListOf<String>()
    var failWith: IOException? = null
    var unreadable = false

    override fun openOutput(uri: String): OutputStream? {
        val sink = ByteArrayOutputStream().also { written[uri] = it }
        val failure = failWith ?: return sink
        return object : OutputStream() {
            override fun write(b: Int) = sink.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (sink.size() > 100_000) throw failure
                sink.write(b, off, len)
            }
        }
    }

    override fun openInput(uri: String): InputStream? = written[uri]?.takeIf { !unreadable }?.let { ByteArrayInputStream(it.toByteArray()) }

    override fun delete(uri: String) {
        deleted += uri
        written.remove(uri)
    }

    override fun placeOf(uri: String) = "Загрузки"

    override fun nameOf(uri: String) = "Интонация · копия.zip"

    override fun sizeOf(uri: String) = written[uri]?.size()?.toLong()
}

internal class FakeBackupPrefs : BackupPrefs {
    override val lastBackupAtEpochMs = MutableStateFlow<Long?>(null)
    var fails: IOException? = null

    override suspend fun setLastBackupAt(epochMs: Long) {
        fails?.let { throw it }
        lastBackupAtEpochMs.value = epochMs
    }
}
