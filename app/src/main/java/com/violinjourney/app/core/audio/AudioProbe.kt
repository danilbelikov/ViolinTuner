package com.violinjourney.app.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.IOException

/** What the extractor of the platform finds in a file of sound (spec 3.32, 5.28). */
sealed interface AudioProbe {
    /** The file cannot be opened or read at all. */
    data object Unreadable : AudioProbe

    /** It opens, but it has no track of sound this phone can decode. */
    data object NoSound : AudioProbe

    /** Its first track of sound: the length, the rate, the channels and the kind of its sound (`audio/mpeg`). */
    data class Sound(val durationUs: Long, val sampleRate: Int, val channels: Int, val mime: String) : AudioProbe

    companion object {
        private const val TAG = "AudioProbe"
        private const val SOUND_KIND = "audio/"

        /**
         * Looks into [uri] with `MediaExtractor` — the extractor the analysis and the player read with: its first track of sound, and a
         * decoder of this phone for it, asked for and let go at once. A track that does not say its length is no sound one can take in:
         * the length is what decides whether it may come in. Blocking.
         */
        fun of(context: Context, uri: Uri): AudioProbe {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(SOUND_KIND) == true }
                    ?: return NoSound
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return NoSound
                // the codec is only asked for, not started: can this phone decode it at all
                MediaCodec.createDecoderByType(mime).release()
                if (!format.containsKey(MediaFormat.KEY_DURATION)) return Unreadable
                return Sound(
                    durationUs = format.getLong(MediaFormat.KEY_DURATION),
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    mime = mime,
                )
            } catch (e: IOException) {
                Log.w(TAG, "cannot open the sound", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cannot read the sound", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "no decoder for the sound", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "no access to the sound", e)
            } finally {
                extractor.release()
            }
            return Unreadable
        }
    }
}
