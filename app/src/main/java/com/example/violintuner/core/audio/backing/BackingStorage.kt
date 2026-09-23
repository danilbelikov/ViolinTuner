package com.example.violintuner.core.audio.backing

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.core.net.toUri
import android.provider.OpenableColumns
import android.util.Log
import com.example.violintuner.core.backup.BackupPaths
import com.example.violintuner.core.domain.backing.Backing
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.BackingFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/** `files/backings/<uuid>.<extension>` — a folder of its own, so a copy of the data can take it whole (spec 3.20). */
class AppBackingFiles @Inject constructor(@ApplicationContext context: Context, private val clock: Clock) : BackingFiles {
    private val directory = File(context.filesDir, DIRECTORY)

    override fun newFile(extension: String): File {
        directory.mkdirs()
        val clean = extension.lowercase().filter { it.isLetterOrDigit() }.take(MAX_EXTENSION).ifEmpty { "audio" }
        return File(directory, "${UUID.randomUUID()}.$clean")
    }

    // Names come from the database; a name with a path in it is not one of ours.
    override fun existing(name: String): File? = File(directory, name).takeIf { it.parentFile == directory && it.isFile }

    override fun delete(name: String) {
        existing(name)?.delete()
    }

    override fun deleteOrphans(kept: Set<String>) {
        val now = clock.millis()
        directory.listFiles().orEmpty()
            .filter { it.name !in kept && now - it.lastModified() > ORPHAN_MIN_AGE_MS }
            .forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY = BackupPaths.BACKINGS
        private const val MAX_EXTENSION = 5
        /** An import copies first and stores its row after: a file younger than this may be one about to get its row. */
        private const val ORPHAN_MIN_AGE_MS = 60 * 60_000L
    }
}

/** The backing's sound as the mix reads it, prepared at a rate (spec 5.25). */
interface BackingPcm {
    fun cached(backing: Backing, sampleRate: Int): File?

    /** The PCM of [backing] at [sampleRate], made if need be; null when its copy is gone or cannot be decoded. Blocking. */
    fun prepare(backing: Backing, sampleRate: Int): File?

    /**
     * Throws away every prepared backing older than [minAgeMs] (spec 5.25): each is made again from its copy when it
     * is needed, and a backing is heavy — some 45 MB for four minutes. One being made just now is left alone.
     */
    fun clear(minAgeMs: Long)
}

/** Takes a picked file in as a backing. Blocking. */
fun interface BackingFileImporter {
    fun import(uri: String): BackingImport
}

/** What adding a backing came to (spec 3.32): the sheet says why it did not. */
sealed interface BackingImport {
    data class Added(val backing: Backing) : BackingImport

    /** Not a sound this phone can decode, or not a file at all. */
    data object Unreadable : BackingImport

    data object TooLong : BackingImport

    data class NoSpace(val neededBytes: Long) : BackingImport
}

/**
 * Takes a file the player picked (spec 3.32): looks into it with the platform's extractor, checks the length
 * and the free space, and copies it as it is — no re-encoding. Blocking; call it off the main thread.
 */
class BackingImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: BackingFiles,
    private val config: BackingConfig,
    private val clock: Clock,
) : BackingFileImporter {
    override fun import(uri: String): BackingImport {
        val source = uri.toUri()
        val resolver = context.contentResolver
        val (displayName, size) = describe(source)
        val probe = probe(source) ?: return BackingImport.Unreadable
        if (probe.durationUs / US_PER_MS > config.maxDurationMs) return BackingImport.TooLong
        val needed = (size ?: 0L) + config.spareBytes
        if (context.filesDir.usableSpace < needed) return BackingImport.NoSpace(needed - context.filesDir.usableSpace)

        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() } ?: probe.extension
        val target = files.newFile(extension)
        val partial = File(target.parentFile, target.name + PARTIAL)
        try {
            val input = resolver.openInputStream(source) ?: return BackingImport.Unreadable
            input.use { stream -> partial.outputStream().use { stream.copyTo(it) } }
            if (!partial.renameTo(target)) throw IOException("rename failed")
        } catch (e: IOException) {
            Log.w(TAG, "cannot copy the backing", e)
            partial.delete()
            target.delete()
            return BackingImport.Unreadable
        } catch (e: SecurityException) {
            Log.w(TAG, "no access to the backing", e)
            partial.delete()
            return BackingImport.Unreadable
        }
        return BackingImport.Added(
            Backing(
                fileName = target.name,
                title = displayName?.substringBeforeLast('.')?.trim()?.ifEmpty { null } ?: target.nameWithoutExtension,
                durationMs = probe.durationUs / US_PER_MS,
                sampleRate = probe.sampleRate,
                channels = probe.channels,
                sizeBytes = target.length(),
                addedAtEpochMs = clock.millis(),
            ),
        )
    }

    private fun describe(uri: Uri): Pair<String?, Long?> = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { cursor.getString(it) }
            val size = cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getLong(it) }
            name to size
        } ?: (null to null)
    } catch (e: SecurityException) {
        Log.w(TAG, "cannot describe the backing", e)
        null to null
    }

    private class Probe(val durationUs: Long, val sampleRate: Int, val channels: Int, val extension: String)

    /** Null when there is no audio track this phone can decode. */
    private fun probe(uri: Uri): Probe? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            // the codec is only asked for, not started: can this phone decode it at all
            MediaCodec.createDecoderByType(mime).release()
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return null
            return Probe(
                durationUs = format.getLong(MediaFormat.KEY_DURATION),
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                extension = mime.substringAfter('/'),
            )
        } catch (e: IOException) {
            Log.w(TAG, "cannot open the backing", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot read the backing", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "no decoder for the backing", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "no access to the backing", e)
        } finally {
            extractor.release()
        }
        return null
    }

    private companion object {
        const val TAG = "BackingImporter"
        const val PARTIAL = ".partial"
        const val US_PER_MS = 1_000L
    }
}

/**
 * The backing as the mix needs it (spec 5.25): 16-bit stereo, interleaved, at the rate of the take, one plain
 * file in the cache — decoded once, resampled once, then read at any position without a codec. A mono backing
 * is doubled to both sides. Rebuilt from the copy whenever the cache has been cleared.
 */
class BackingPcmCache @Inject constructor(
    @ApplicationContext context: Context,
    private val files: BackingFiles,
    private val clock: Clock,
) : BackingPcm {
    private val directory = File(context.cacheDir, DIRECTORY)

    override fun clear(minAgeMs: Long) {
        val now = clock.millis()
        directory.listFiles().orEmpty()
            .filter { !it.name.endsWith(PARTIAL) && now - it.lastModified() >= minAgeMs }
            .forEach { it.delete() }
    }

    // used now: touched, so the sweep of a cold start (older than an hour — left by a killed run) does not take it
    override fun cached(backing: Backing, sampleRate: Int): File? =
        fileOf(backing, sampleRate).takeIf { it.isFile }?.also { it.setLastModified(clock.millis()) }

    override fun prepare(backing: Backing, sampleRate: Int): File? {
        cached(backing, sampleRate)?.let { return it }
        val source = files.existing(backing.fileName) ?: return null
        directory.mkdirs()
        val target = fileOf(backing, sampleRate)
        val partial = File(directory, target.name + PARTIAL)
        return try {
            decode(source, partial, sampleRate)
            if (partial.renameTo(target)) target else null
        } catch (e: IOException) {
            Log.w(TAG, "cannot decode ${backing.fileName}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot decode ${backing.fileName}", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "decoder refused ${backing.fileName}", e)
            null
        } finally {
            partial.delete()
        }
    }

    private fun fileOf(backing: Backing, sampleRate: Int) = File(directory, "${backing.fileName.substringBeforeLast('.')}-$sampleRate.pcm")

    private fun decode(source: File, target: File, outRate: Int) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val inRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val left = Resampler(inRate, outRate)
            val right = Resampler(inRate, outRate)
            RandomAccessFile(target, "rw").use { out ->
                out.setLength(0)
                val writer = StereoWriter(out)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            val buffer = checkNotNull(decoder.getInputBuffer(index))
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        index >= 0 -> {
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            val buffer = decoder.getOutputBuffer(index)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset).limit(info.offset + info.size)
                                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                                val frames = shorts.remaining() / channels
                                val l = FloatArray(frames)
                                val r = FloatArray(frames)
                                for (i in 0 until frames) {
                                    val a = shorts.get() / FULL_SCALE
                                    val b = if (channels > 1) shorts.get() / FULL_SCALE else a
                                    repeat((channels - 2).coerceAtLeast(0)) { shorts.get() }
                                    l[i] = a
                                    r[i] = b
                                }
                                writer.push(left, right, l, r, frames)
                            }
                            decoder.releaseOutputBuffer(index, false)
                        }
                    }
                }
                writer.finish(left, right)
            }
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }

    /** Runs both channels through their resamplers and writes them side by side; the two always give the same count. */
    private class StereoWriter(private val out: RandomAccessFile) {
        private val lefts = Floats()
        private val rights = Floats()

        fun push(left: Resampler, right: Resampler, l: FloatArray, r: FloatArray, count: Int) {
            left.process(l, count, lefts::add)
            right.process(r, count, rights::add)
            write()
        }

        fun finish(left: Resampler, right: Resampler) {
            left.finish(lefts::add)
            right.finish(rights::add)
            write()
        }

        private fun write() {
            val n = minOf(lefts.size, rights.size)
            if (n == 0) return
            val bytes = ByteBuffer.allocate(n * BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) {
                bytes.putShort(toShort(lefts.values[i]))
                bytes.putShort(toShort(rights.values[i]))
            }
            out.write(bytes.array())
            lefts.dropFirst(n)
            rights.dropFirst(n)
        }

        private fun toShort(value: Float): Short = (value * FULL_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    /** A growing run of floats without boxing. */
    private class Floats {
        var values = FloatArray(INITIAL)
        var size = 0

        fun add(chunk: FloatArray, count: Int) {
            if (size + count > values.size) values = values.copyOf(maxOf(values.size * 2, size + count))
            chunk.copyInto(values, size, 0, count)
            size += count
        }

        fun dropFirst(n: Int) {
            values.copyInto(values, 0, n, size)
            size -= n
        }

        private companion object {
            const val INITIAL = 8_192
        }
    }

    companion object {
        private const val TAG = "BackingPcmCache"
        private const val DIRECTORY = "backing-pcm"
        private const val PARTIAL = ".partial"
        private const val TIMEOUT_US = 10_000L
        const val FULL_SCALE = 32_768f
        const val BYTES_PER_FRAME = 4
    }
}

/**
 * Reads the prepared PCM of a backing at any position (spec 5.25): before its start and after its end there is
 * silence, so a shift either way is only a position. Not thread-safe: one reader per player or render.
 */
class BackingPcmReader(file: File) : AutoCloseable {
    private val input = RandomAccessFile(file, "r")
    val frames: Long = input.length() / BackingPcmCache.BYTES_PER_FRAME
    private var bytes = ByteArray(0)

    /**
     * Fills [left] and [right] with [count] frames starting at [position] (a frame index of the backing, which
     * may be negative or past the end), scaled by [gain].
     */
    fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray) {
        left.fill(0f, 0, count)
        right.fill(0f, 0, count)
        val from = maxOf(position, 0L)
        val to = minOf(position + count, frames)
        if (from >= to) return
        val n = (to - from).toInt()
        if (bytes.size < n * BackingPcmCache.BYTES_PER_FRAME) bytes = ByteArray(n * BackingPcmCache.BYTES_PER_FRAME)
        input.seek(from * BackingPcmCache.BYTES_PER_FRAME)
        input.readFully(bytes, 0, n * BackingPcmCache.BYTES_PER_FRAME)
        val shorts = ByteBuffer.wrap(bytes, 0, n * BackingPcmCache.BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val offset = (from - position).toInt()
        for (i in 0 until n) {
            left[offset + i] = shorts.get() / BackingPcmCache.FULL_SCALE * gain
            right[offset + i] = shorts.get() / BackingPcmCache.FULL_SCALE * gain
        }
    }

    override fun close() = input.close()
}
