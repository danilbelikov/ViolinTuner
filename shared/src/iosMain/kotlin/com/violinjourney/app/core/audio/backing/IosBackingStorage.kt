package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.backup.BackupPaths
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingFiles
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.deleteAll
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.listNames
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.moveTo
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.open
import platform.posix.pread

/** `backings/<uuid>.<extension>` in Application Support — a folder of its own, so a copy of the data takes it whole (spec 3.20). */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingFiles(private val data: PlatformFile, private val clock: WallClock) : BackingFiles {
    private val directory get() = data.child(BackupPaths.BACKINGS).also { it.makeDirectories() }

    override fun newFile(extension: String): PlatformFile {
        val clean = extension.lowercase().filter { it.isLetterOrDigit() }.take(MAX_EXTENSION).ifEmpty { "audio" }
        return directory.child("${NSUUID().UUIDString}.$clean")
    }

    // names come from the database; a name with a path in it is not one of ours
    override fun existing(name: String): PlatformFile? = directory.child(name).takeIf { '/' !in name && it.exists() }

    override fun delete(name: String) {
        existing(name)?.deleteAll()
    }

    override fun deleteOrphans(kept: Set<String>) {
        val now = clock.millis()
        directory.listNames().filter { it !in kept && now - modifiedMs(directory.child(it)) > ORPHAN_MIN_AGE_MS }.forEach { directory.child(it).deleteAll() }
    }

    private companion object {
        const val MAX_EXTENSION = 5

        /** An import copies first and stores its row after: a file younger than this may be one about to get its row. */
        const val ORPHAN_MIN_AGE_MS = 60 * 60_000L
    }
}

/**
 * Takes a file picked in Files (spec 3.32) — the picker has copied it for the app already: looks into it with AVFoundation,
 * checks the length and the free space, and moves it in as it is, as `BackingImporter` does on Android.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingImporter(
    private val data: PlatformFile,
    private val files: BackingFiles,
    private val config: BackingConfig,
    private val clock: WallClock,
) : BackingFileImporter {
    override fun import(uri: String): BackingImport {
        val path = NSURL.URLWithString(uri)?.path ?: return BackingImport.Unreadable
        val source = PlatformFile(path)
        val probe = runCatching { AVAudioFile(forReading = NSURL.fileURLWithPath(path), error = null) }.getOrNull() ?: return BackingImport.Unreadable
        val rate = probe.fileFormat.sampleRate
        if (rate <= 0 || probe.length <= 0) return BackingImport.Unreadable
        val durationMs = (probe.length / rate * MS_PER_SECOND).roundToLong()
        if (durationMs > config.maxDurationMs) return BackingImport.TooLong
        val needed = source.sizeBytes() + config.spareBytes
        val free = freeBytes()
        if (free < needed) return BackingImport.NoSpace(needed - free)
        val name = path.substringAfterLast('/')
        val target = files.newFile(name.substringAfterLast('.', "audio"))
        if (!source.moveTo(target)) return BackingImport.Unreadable
        return BackingImport.Added(
            Backing(
                fileName = target.path.substringAfterLast('/'),
                title = name.substringBeforeLast('.').trim().ifEmpty { target.path.substringAfterLast('/').substringBeforeLast('.') },
                durationMs = durationMs,
                sampleRate = rate.roundToInt(),
                channels = probe.fileFormat.channelCount.toInt(),
                sizeBytes = target.sizeBytes(),
                addedAtEpochMs = clock.millis(),
            ),
        )
    }

    private fun freeBytes(): Long =
        (NSFileManager.defaultManager.attributesOfFileSystemForPath(data.path, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0

    private companion object {
        const val MS_PER_SECOND = 1_000.0
    }
}

/**
 * The backing as the mix needs it (spec 5.25), in the same form as on Android: 16-bit stereo, interleaved, at the rate
 * of the take, one plain file in Caches — decoded once by AVAudioFile, resampled once by [Resampler].
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingPcm(private val caches: PlatformFile, private val files: BackingFiles) : BackingPcm {
    private val directory get() = caches.child(DIRECTORY).also { it.makeDirectories() }

    override fun deleteOrphans(keptFiles: Set<String>) {
        val kept = keptFiles.mapTo(HashSet()) { it.substringBeforeLast('.') }
        directory.listNames().filter { !it.endsWith(PARTIAL) && it.substringBeforeLast(RATE_SEPARATOR) !in kept }.forEach { directory.child(it).deleteAll() }
    }

    override fun cached(backing: Backing, sampleRate: Int): PlatformFile? = fileOf(backing, sampleRate).takeIf { it.exists() }

    override fun prepare(backing: Backing, sampleRate: Int): PlatformFile? {
        cached(backing, sampleRate)?.let { return it }
        val source = files.existing(backing.fileName) ?: return null
        val target = fileOf(backing, sampleRate)
        val partial = directory.child(target.path.substringAfterLast('/') + PARTIAL)
        return try {
            if (decode(source, partial, sampleRate) && partial.moveTo(target)) target else null
        } finally {
            partial.deleteAll()
        }
    }

    private fun fileOf(backing: Backing, sampleRate: Int) = directory.child("${backing.fileName.substringBeforeLast('.')}$RATE_SEPARATOR$sampleRate.pcm")

    private fun decode(source: PlatformFile, target: PlatformFile, outRate: Int): Boolean {
        val file = runCatching { AVAudioFile(forReading = NSURL.fileURLWithPath(source.path), error = null) }.getOrNull() ?: return false
        val format = file.processingFormat
        val channels = format.channelCount.toInt().coerceAtLeast(1)
        val inRate = format.sampleRate.roundToInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = CHUNK.toUInt())
        val left = Resampler(inRate, outRate)
        val right = Resampler(inRate, outRate)
        val out = StereoWriter(target)
        try {
            while (true) {
                if (!file.readIntoBuffer(buffer, CHUNK.toUInt(), null)) {
                    if (file.framePosition >= file.length) break else return false
                }
                val frames = buffer.frameLength.toInt()
                if (frames == 0) break
                val data = buffer.floatChannelData ?: return false
                val first = data[0] ?: return false
                val second = if (channels > 1) data[1] ?: first else first
                out.push(left, right, FloatArray(frames) { first[it] }, FloatArray(frames) { second[it] }, frames)
            }
            out.finish(left, right)
            return true
        } finally {
            out.close()
        }
    }

    /** Runs both channels through their resamplers and writes them side by side; the two always give the same count. */
    private class StereoWriter(target: PlatformFile) {
        private val output = requireNotNull(target.openOutput()) { "cannot write ${target.path}" }
        private var lefts = FloatArray(0)
        private var rights = FloatArray(0)

        fun push(left: Resampler, right: Resampler, l: FloatArray, r: FloatArray, count: Int) {
            left.process(l, count) { chunk, n -> lefts += chunk.copyOf(n) }
            right.process(r, count) { chunk, n -> rights += chunk.copyOf(n) }
            write()
        }

        fun finish(left: Resampler, right: Resampler) {
            left.finish { chunk, n -> lefts += chunk.copyOf(n) }
            right.finish { chunk, n -> rights += chunk.copyOf(n) }
            write()
        }

        private fun write() {
            val n = minOf(lefts.size, rights.size)
            if (n == 0) return
            val bytes = ByteArray(n * BYTES_PER_FRAME)
            for (i in 0 until n) {
                putShort(bytes, i * BYTES_PER_FRAME, lefts[i])
                putShort(bytes, i * BYTES_PER_FRAME + 2, rights[i])
            }
            output.write(bytes, 0, bytes.size)
            lefts = lefts.copyOfRange(n, lefts.size)
            rights = rights.copyOfRange(n, rights.size)
        }

        fun close() = output.close()

        private fun putShort(bytes: ByteArray, at: Int, value: Float) {
            val s = (value * FULL_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[at] = s.toByte()
            bytes[at + 1] = (s shr 8).toByte()
        }
    }

    companion object {
        private const val DIRECTORY = "backing-pcm"
        private const val PARTIAL = ".partial"
        private const val RATE_SEPARATOR = '-'
        private const val CHUNK = 8_192
        const val FULL_SCALE = 32_768f
        const val BYTES_PER_FRAME = 4
    }
}

/**
 * Reads the prepared PCM of a backing at any position (spec 5.25), as `BackingPcmReader` on Android: before its start
 * and after its end there is silence. Not thread-safe: one reader per player or render.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingPcmReader(file: PlatformFile) : BackingSource, AutoCloseable {
    private val fd = open(file.path, O_RDONLY)
    val frames: Long = file.sizeBytes() / IosBackingPcm.BYTES_PER_FRAME
    private var bytes = ByteArray(0)

    init {
        require(fd >= 0) { "cannot open ${file.path}" }
    }

    override fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray) {
        left.fill(0f, 0, count)
        right.fill(0f, 0, count)
        val from = maxOf(position, 0L)
        val to = minOf(position + count, frames)
        if (from >= to) return
        val n = (to - from).toInt()
        val length = n * IosBackingPcm.BYTES_PER_FRAME
        if (bytes.size < length) bytes = ByteArray(length)
        val got = bytes.usePinned { pread(fd, it.addressOf(0), length.toULong(), from * IosBackingPcm.BYTES_PER_FRAME) }.toInt()
        val offset = (from - position).toInt()
        for (i in 0 until minOf(n, got / IosBackingPcm.BYTES_PER_FRAME)) {
            val at = i * IosBackingPcm.BYTES_PER_FRAME
            left[offset + i] = shortAt(at) / IosBackingPcm.FULL_SCALE * gain
            right[offset + i] = shortAt(at + 2) / IosBackingPcm.FULL_SCALE * gain
        }
    }

    private fun shortAt(at: Int): Short = ((bytes[at].toInt() and BYTE) or (bytes[at + 1].toInt() shl BITS_PER_BYTE)).toShort()

    override fun close() {
        close(fd)
    }

    private companion object {
        const val BYTE = 0xFF
        const val BITS_PER_BYTE = 8
    }
}

/** When a file was last written, in ms since the epoch; 0 for a file that is not there. */
@OptIn(ExperimentalForeignApi::class)
private fun modifiedMs(file: PlatformFile): Long =
    ((NSFileManager.defaultManager.attributesOfItemAtPath(file.path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0)
        .times(1_000).toLong()
