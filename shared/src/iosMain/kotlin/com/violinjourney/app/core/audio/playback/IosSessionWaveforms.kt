package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileName
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The waveforms of the mini player on iOS, as `AppSessionWaveforms` keeps them on Android: reckoned once from the
 * sound by [WaveformBuilder] and kept a byte a bar in `waveforms/<audio name>.wave`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSessionWaveforms(private val directory: () -> String, private val io: CoroutineDispatcher) : SessionWaveforms {
    override suspend fun of(audio: PlatformFile): FloatArray? = withContext(io) {
        val cache = "${directory()}/${audio.fileName}$EXTENSION"
        stored(cache) ?: reckon(audio) { ensureActive() }?.let { reckoned ->
            val kept = WaveformBuilder.encode(reckoned)
            store(cache, kept)
            WaveformBuilder.decode(kept)
        }
    }

    override suspend fun deleteOrphans(audioNames: Set<String>) = withContext(io) {
        val files = NSFileManager.defaultManager
        val folder = directory()
        files.contentsOfDirectoryAtPath(folder, null).orEmpty().mapNotNull { it as? String }
            .filter { it.removeSuffix(EXTENSION) !in audioNames }
            .forEach { files.removeItemAtPath("$folder/$it", null) }
    }

    private fun stored(cache: String): FloatArray? {
        val data = NSData.dataWithContentsOfFile(cache) ?: return null
        if (data.length.toInt() != SessionWaveforms.BARS) return null
        val bytes = ByteArray(SessionWaveforms.BARS)
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return WaveformBuilder.decode(bytes)
    }

    private fun store(cache: String, waveform: ByteArray) {
        // whole or not at all: a half-written file would be taken for a waveform next time
        val data = waveform.usePinned { NSData.create(bytes = it.addressOf(0), length = waveform.size.toULong()) }
        data.writeToFile(cache, atomically = true)
    }

    private inline fun reckon(audio: PlatformFile, checkpoint: () -> Unit): FloatArray? {
        val file = runCatching { AVAudioFile(forReading = NSURL.fileURLWithPath(audio.path), error = null) }.getOrNull() ?: return null
        if (file.length <= 0) return null
        val builder = WaveformBuilder(file.length, SessionWaveforms.BARS)
        val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = CHUNK.toUInt())
        val chunk = ShortArray(CHUNK)
        while (true) {
            checkpoint()
            if (!file.readIntoBuffer(buffer, CHUNK.toUInt(), null)) return null
            val count = buffer.frameLength.toInt()
            if (count == 0) break
            val channel = buffer.floatChannelData?.get(0) ?: return null
            for (i in 0 until count) chunk[i] = (channel[i].coerceIn(-1f, 1f) * FULL_SCALE).roundToInt().toShort()
            builder.add(chunk, count)
        }
        return builder.build()
    }

    private companion object {
        const val EXTENSION = ".wave"
        const val CHUNK = 8_192
        const val FULL_SCALE = 32_767f
    }
}
