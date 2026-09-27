package com.violinjourney.app.core.audio.playback

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.violinjourney.app.core.recording.PcmSource
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The sound of a file as a stream of PCM: `MediaExtractor` + a `MediaCodec` decoder. What `MediaPlayer` did inside
 * itself, taken apart — the samples are needed in hand to run them through the sound chain, for the speaker and for the
 * file alike. Mono ([read]) for the player, the waveform, the render and the analysis; stereo ([readStereo]) for the
 * backing (spec 5.25).
 *
 * The rate, the channels and the kind of samples are the codec's, not the container's: HE-AAC keeps half its rate in the
 * container, which SBR doubles in the decoder (and a mono one comes out in two channels), and a WAV or FLAC above
 * 16 bits may come out as float or wider integers (some Android versions hand them out so; the API 37 emulator already
 * gives 16 bits). [open] decodes up to the first sound to learn them. Not thread-safe: one thread opens, reads, seeks and
 * releases.
 */
class PcmDecoder private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    trackRate: Int,
    trackChannels: Int,
    /** 0 when the file does not tell it and [open] was not asked for it. */
    val durationUs: Long,
) : PcmSource {
    /** The rate the codec puts out, settled by [open] before anyone builds a track or a resampler on it. */
    override var sampleRate: Int = trackRate
        private set
    private var rateSettled = false
    private var channels = trackChannels.coerceAtLeast(1)
    private var encoding = PcmSamples.PCM_16BIT
    private var frameBytes = channels * PcmSamples.bytesPerSample(encoding)

    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false

    /** Output buffer that was handed out only in part; the rest goes to the next read. */
    private var heldIndex = -1
    private var held: ByteBuffer? = null

    /** After a seek the codec starts at a frame boundary before the target: this many frames are dropped to land on it. */
    private var toSkip = 0L
    private var seekTargetUs = -1L

    override val totalSamples: Long get() = durationUs * sampleRate / MICROS

    /** Fills [out] with up to its size of mono samples; returns how many, or [END] once the sound has ended. */
    override fun read(out: ShortArray): Int {
        var written = 0
        while (written < out.size) {
            val buffer = held
            if (buffer != null) {
                written += drainMono(buffer, out, written)
                if (buffer.remaining() < frameBytes) releaseHeld()
                continue
            }
            if (outputDone) break
            pull()
        }
        return if (written == 0 && outputDone) END else written
    }

    /**
     * Fills [left] and [right] with up to the smaller size of frames in -1…1: the first channel goes left, the second
     * right, a mono sound to both, any further channels are dropped. Returns how many, or [END] once the sound has ended.
     */
    fun readStereo(left: FloatArray, right: FloatArray): Int {
        val size = minOf(left.size, right.size)
        var written = 0
        while (written < size) {
            val buffer = held
            if (buffer != null) {
                written += drainStereo(buffer, left, right, written, size)
                if (buffer.remaining() < frameBytes) releaseHeld()
                continue
            }
            if (outputDone) break
            pull()
        }
        return if (written == 0 && outputDone) END else written
    }

    /** The next read starts at [positionUs] of the sound, to the sample. */
    fun seekTo(positionUs: Long) {
        releaseHeld()
        val target = positionUs.coerceIn(0, durationUs)
        extractor.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false
        outputDone = false
        toSkip = 0
        seekTargetUs = target
    }

    fun release() {
        releaseHeld()
        runCatching { codec.stop() }.onFailure { Log.w(TAG, "codec did not stop cleanly", it) }
        codec.release()
        extractor.release()
    }

    /**
     * Decodes up to the first sound, so that [sampleRate] is the codec's before anyone builds a track or a resampler on
     * it. A codec that gives nothing for [PRIME_TRIES] turns — about a second — leaves the container's rate standing.
     */
    private fun prime() {
        var tries = 0
        while (held == null && !outputDone && tries++ < PRIME_TRIES) pull()
        if (!rateSettled) {
            rateSettled = true
            if (!outputDone) Log.w(TAG, "no sound after $PRIME_TRIES turns: the container's $sampleRate Hz stands")
        }
    }

    /** One turn of the codec: it is fed, and one output buffer — or the news of a new format — is taken. */
    private fun pull() {
        feed()
        val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> takeFormat(codec.outputFormat)
            index >= 0 -> {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                val buffer = if (info.size > 0) codec.getOutputBuffer(index) else null
                if (buffer == null) {
                    codec.releaseOutputBuffer(index, false)
                    return
                }
                // the format the first sound really comes in, if the codec did not announce it before
                if (!rateSettled) takeFormat(codec.getOutputFormat(index))
                if (seekTargetUs >= 0) {
                    toSkip = (seekTargetUs - info.presentationTimeUs).coerceAtLeast(0) * sampleRate / MICROS
                    seekTargetUs = -1
                }
                buffer.position(info.offset).limit(info.offset + info.size)
                held = buffer.order(ByteOrder.nativeOrder())
                heldIndex = index
            }
        }
    }

    /**
     * The codec's word on what it puts out. The channels (a mono HE-AAC comes out in two) and the kind of samples are
     * followed whenever they change — a format that does not name the kind is 16-bit, as `MediaCodec` defines it; the rate
     * only the first time: the player's track and the backing's resamplers are built on it, and AAC settles its rate at the
     * first frame.
     */
    private fun takeFormat(format: MediaFormat) {
        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else PcmSamples.PCM_16BIT
        frameBytes = channels * PcmSamples.bytesPerSample(encoding)
        if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) return
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (!rateSettled) {
            sampleRate = rate
            rateSettled = true
        } else if (rate != sampleRate) {
            Log.w(TAG, "the rate changed mid-stream, $sampleRate to $rate Hz: not followed")
        }
    }

    /**
     * Gives the codec everything it will take right now. One buffer a call, followed by a wait for
     * output, held decoding down to about the speed of the sound itself: the player ran dry and a
     * file took as long to render as to play. A codec with a full input never waits for nothing.
     */
    private fun feed() {
        while (!inputDone) {
            val index = codec.dequeueInputBuffer(0)
            if (index < 0) return
            val buffer = codec.getInputBuffer(index) ?: return
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
    }

    /**
     * Moves frames from the codec's buffer to [out], folding channels into one and dropping what a seek asked to skip.
     * 16-bit sound — our own recordings — keeps the integer sum it always had, bit for bit.
     */
    private fun drainMono(from: ByteBuffer, out: ShortArray, offset: Int): Int {
        var written = 0
        while (offset + written < out.size && from.remaining() >= frameBytes) {
            val sample = if (encoding == PcmSamples.PCM_16BIT) {
                var sum = 0
                repeat(channels) { sum += from.getShort() }
                (sum / channels).toShort()
            } else {
                var sum = 0f
                repeat(channels) { sum += PcmSamples.next(from, encoding) }
                PcmSamples.toShort(sum / channels)
            }
            if (toSkip > 0) {
                toSkip--
            } else {
                out[offset + written++] = sample
            }
        }
        return written
    }

    /** Moves frames from the codec's buffer to [left] and [right] from [offset] up to [size]; see [readStereo]. */
    private fun drainStereo(from: ByteBuffer, left: FloatArray, right: FloatArray, offset: Int, size: Int): Int {
        var written = 0
        while (offset + written < size && from.remaining() >= frameBytes) {
            val first = PcmSamples.next(from, encoding)
            val second = if (channels > 1) PcmSamples.next(from, encoding) else first
            if (channels > 2) from.position(from.position() + (channels - 2) * PcmSamples.bytesPerSample(encoding))
            if (toSkip > 0) {
                toSkip--
            } else {
                left[offset + written] = first
                right[offset + written] = second
                written++
            }
        }
        return written
    }

    private fun releaseHeld() {
        if (heldIndex >= 0) codec.releaseOutputBuffer(heldIndex, false)
        heldIndex = -1
        held = null
    }

    companion object {
        const val END = -1
        private const val TAG = "PcmDecoder"
        private const val MICROS = 1_000_000L
        private const val TIMEOUT_US = 10_000L

        /** Turns of [prime] at most: with [TIMEOUT_US] each, about a second for a codec that says nothing. */
        private const val PRIME_TRIES = 100

        /**
         * Null when the file cannot be opened, holds no sound this device can decode, or — when [lengthRequired] — does
         * not tell its length. Decodes up to the first sound before it returns (see [PcmDecoder]); blocking.
         */
        fun open(file: File, lengthRequired: Boolean = true): PcmDecoder? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(file.absolutePath)
                val track = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw IOException("no audio track")
                val format = extractor.getTrackFormat(track)
                extractor.selectTrack(track)
                // A track without a length (a stream written live, a webm from a browser) can be neither shown by the
                // player nor measured by the analysis: it cannot be opened for them, as a backing without one is refused
                // (BackingImporter.probe). `getLong` would throw a NullPointerException past every catch.
                val hasLength = format.containsKey(MediaFormat.KEY_DURATION)
                if (lengthRequired && !hasLength) throw IOException("no duration")
                if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) throw IOException("no sample rate")
                val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                return PcmDecoder(
                    extractor = extractor,
                    codec = codec,
                    trackRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    trackChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1,
                    durationUs = if (hasLength) format.getLong(MediaFormat.KEY_DURATION) else 0,
                ).also { it.prime() }
            } catch (e: IOException) {
                Log.w(TAG, "cannot open ${file.name}", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cannot decode ${file.name}", e)
            } catch (e: IllegalStateException) {
                // MediaCodec.CodecException among them, and a kind of samples not known here
                Log.w(TAG, "decoder refused ${file.name}", e)
            }
            codec?.release()
            extractor.release()
            return null
        }
    }
}
