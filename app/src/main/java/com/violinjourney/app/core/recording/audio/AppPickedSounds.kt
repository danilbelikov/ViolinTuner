package com.violinjourney.app.core.recording.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.violinjourney.app.core.audio.AudioProbe
import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.recording.video.VideoDates
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Sound files picked for a recording of an event on Android (spec 3.35, 5.28): the system's picker lends a content uri, read through
 * the resolver; the copy is `files/sessions/<uuid>.sound.<ext>`, beside the sound of the recordings, under the extension of the picked
 * file — or, when its name has none, the one its type is known by. The provider owns what it lends: there is nothing to let go.
 */
class AppPickedSounds @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PickedSounds {
    private val directory = File(context.filesDir, DataLayout.SESSIONS)

    override fun sizeOf(uri: String): Long? = query(uri.toUri(), OpenableColumns.SIZE) { cursor, column -> cursor.getLong(column) }

    override fun freeBytes(): Long {
        directory.mkdirs()
        return StatFs(directory.path).availableBytes
    }

    override fun probe(uri: String): SoundProbe? {
        val source = uri.toUri()
        val sound = when (val probe = AudioProbe.of(context, source)) {
            AudioProbe.Unreadable -> return null
            AudioProbe.NoSound -> return SoundProbe(durationMs = 0, hasSound = false, createdAtEpochMs = null, modifiedAtEpochMs = null)
            is AudioProbe.Sound -> probe
        }
        return SoundProbe(
            durationMs = sound.durationUs / US_PER_MS,
            hasSound = true,
            createdAtEpochMs = recordedAt(source),
            // documents say when they were changed; a provider that does not says nothing
            modifiedAtEpochMs = query(source, DocumentsContract.Document.COLUMN_LAST_MODIFIED) { cursor, column -> cursor.getLong(column) }?.takeIf { it > 0 },
        )
    }

    override suspend fun import(uri: String): File? = withContext(io) {
        val source = uri.toUri()
        directory.mkdirs()
        val target = File(directory, "${UUID.randomUUID()}${PickedSounds.MARK}${extensionOf(source)}")
        val part = File(directory, target.name + PARTIAL_SUFFIX)
        try {
            val input = context.contentResolver.openInputStream(source) ?: return@withContext null
            input.use { from ->
                part.outputStream().use { to ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = from.read(buffer)
                        if (read < 0) break
                        to.write(buffer, 0, read)
                    }
                }
            }
            if (part.renameTo(target)) target else null
        } catch (e: IOException) {
            Log.w(TAG, "cannot copy $uri", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "may not read $uri", e)
            null
        } finally {
            // whatever is left under the partial name — a failure, a cancellation — is not a sound
            part.delete()
        }
    }

    // a content: uri belongs to its provider — the app holds nothing of a pick until it is copied in
    override fun release(uri: String) = Unit

    override fun discard(file: File) {
        file.delete()
    }

    /** When the sound was recorded, by its own metadata (`METADATA_KEY_DATE`, as a video's); null when it does not say. */
    private fun recordedAt(source: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            VideoDates.parse(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "no date in $source", e)
            null
        } catch (e: RuntimeException) {
            // setDataSource throws a bare RuntimeException on what it cannot parse
            Log.w(TAG, "cannot read the date of $source", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * The extension the copy keeps: that of the name the provider gives the file — «Концерт.mp3» — or the one its type is known by
     * (`audio/mpeg` → `mp3`), or none worth the name: «audio».
     */
    private fun extensionOf(source: Uri): String {
        val named = query(source, OpenableColumns.DISPLAY_NAME) { cursor, column -> cursor.getString(column) }?.substringAfterLast('.', "")
        val typed = context.contentResolver.getType(source)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return listOfNotNull(named, typed).map { it.lowercase().filter(Char::isLetterOrDigit).take(MAX_EXTENSION) }.firstOrNull { it.isNotEmpty() } ?: FALLBACK_EXTENSION
    }

    /** One column of what the provider tells of [source]; null when it tells nothing, or refuses. */
    private fun <T> query(source: Uri, column: String, read: (android.database.Cursor, Int) -> T): T? = try {
        context.contentResolver.query(source, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) read(cursor, index) else null
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "no $column of $source", e)
        null
    } catch (e: IllegalArgumentException) {
        // a provider that has no such column says so by a throw
        Log.w(TAG, "no $column of $source", e)
        null
    }

    private companion object {
        const val TAG = "PickedSounds"
        const val PARTIAL_SUFFIX = ".part"
        const val COPY_BUFFER = 256 * 1024
        const val US_PER_MS = 1_000L
        const val MAX_EXTENSION = 5
        const val FALLBACK_EXTENSION = "audio"
    }
}
