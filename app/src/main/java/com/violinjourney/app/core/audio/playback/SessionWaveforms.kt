package com.violinjourney.app.core.audio.playback

import android.content.Context
import android.util.Log
import com.violinjourney.app.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * [SessionWaveforms] in the app's storage. Reckoned once — the whole
 * file has to be decoded for it, which for an hour of sound takes a good while — and kept as
 * `files/waveforms/<audio name>.wave`, a byte a bar. Files of recordings that are gone are
 * swept out at start.
 */
@Singleton
class AppSessionWaveforms @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SessionWaveforms {
    private val directory = File(context.filesDir, DIRECTORY)

    override suspend fun of(audio: File): FloatArray? = withContext(io) {
        val cache = File(directory, audio.name + EXTENSION)
        // What is handed out is always what the byte-a-bar file holds: the first look and every later one are the same picture.
        stored(cache) ?: reckon(audio) { ensureActive() }?.let { reckoned ->
            val kept = WaveformBuilder.encode(reckoned)
            store(cache, kept)
            WaveformBuilder.decode(kept)
        }
    }

    override suspend fun deleteOrphans(audioNames: Set<String>) = withContext(io) {
        directory.listFiles().orEmpty()
            .filter { it.name.removeSuffix(EXTENSION) !in audioNames }
            .forEach { it.delete() }
    }

    private fun stored(cache: File): FloatArray? = try {
        cache.takeIf { it.isFile && it.length() == BARS.toLong() }?.readBytes()?.let(WaveformBuilder::decode)
    } catch (e: IOException) {
        Log.w(TAG, "cannot read ${cache.name}", e)
        null
    }

    private fun store(cache: File, waveform: ByteArray) {
        try {
            directory.mkdirs()
            // Whole or not at all: a half-written file would be taken for a waveform next time.
            val part = File(directory, cache.name + PART)
            part.writeBytes(waveform)
            if (!part.renameTo(cache)) part.delete()
        } catch (e: IOException) {
            Log.w(TAG, "cannot keep ${cache.name}", e) // it will be reckoned again, that is all
        }
    }

    private inline fun reckon(audio: File, checkpoint: () -> Unit): FloatArray? {
        val decoder = PcmDecoder.open(audio) ?: return null
        try {
            val builder = WaveformBuilder(decoder.totalSamples, BARS)
            val chunk = ShortArray(CHUNK)
            while (true) {
                checkpoint()
                val count = decoder.read(chunk)
                if (count == PcmDecoder.END) break
                builder.add(chunk, count)
            }
            return builder.build()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "decoding ${audio.name} broke down", e)
            return null
        } finally {
            decoder.release()
        }
    }

    private companion object {
        const val BARS = SessionWaveforms.BARS
        const val TAG = "SessionWaveforms"
        const val DIRECTORY = "waveforms"
        const val EXTENSION = ".wave"
        const val PART = ".part"
        const val CHUNK = 8_192
    }
}
