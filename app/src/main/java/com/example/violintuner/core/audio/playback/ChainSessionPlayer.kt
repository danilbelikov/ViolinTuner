package com.example.violintuner.core.audio.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.violintuner.core.audio.backing.BackingMixer
import com.example.violintuner.core.audio.backing.BackingPcmReader
import com.example.violintuner.core.audio.backing.PcmBackingSource
import com.example.violintuner.core.audio.fx.SoundChain
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.audio.fx.SoundMeters
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [SessionPlayer] of our own making: decoder → [SoundChain] → `AudioTrack`, on a thread of its
 * own. `MediaPlayer` could only play a file as it is; here the samples pass through the very
 * chain that also renders the file to be shared — what is heard is what is sent (spec 3.17).
 *
 * The calls of the interface only leave wishes (play, seek, settings, A/B) for the thread, which
 * picks them up between two chunks of sound — about every 40 ms.
 */
class ChainSessionPlayer(private val config: SoundConfig, private val backingConfig: BackingConfig = BackingConfig()) : SessionPlayer {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val mutableMeters = MutableStateFlow<SoundMeters?>(null)
    override val meters: StateFlow<SoundMeters?> = mutableMeters.asStateFlow()

    private val lock = Object()
    private var worker: Worker? = null

    // Wishes, written on the main thread and read by the worker under [lock].
    private var wantPlaying = false
    private var seekToMs = NO_SEEK
    private var settings: SoundSettings = SoundRules.off(config)
    private var settingsChanged = true
    private var original = false
    private var backingOffsetMs = 0
    private var backingGainDb = 0f
    private var backingHeard = true
    private var backingChanged = false

    override fun load(file: File) = loadWithBacking(file, null)

    override fun loadWithBacking(file: File, backing: PlayerBacking?) {
        release()
        synchronized(lock) {
            backingOffsetMs = backing?.offsetMs ?: 0
            backingGainDb = backing?.gainDb ?: 0f
            backingChanged = false
        }
        worker = Worker(file, backing).also { it.start() }
    }

    override fun setBackingMix(offsetMs: Int, gainDb: Float) = wish {
        backingOffsetMs = offsetMs
        backingGainDb = gainDb
        backingChanged = true
    }

    override fun setBackingHeard(heard: Boolean) {
        wish {
            backingHeard = heard
            backingChanged = true
        }
        mutableState.update { it.copy(backingHeard = heard) }
    }

    override fun play() {
        if (!state.value.ready || state.value.playing) return
        wish { wantPlaying = true }
        mutableState.update { it.copy(playing = true) }
    }

    override fun pause() {
        if (!state.value.playing) return
        wish { wantPlaying = false }
        mutableState.update { it.copy(playing = false) }
    }

    override fun seekTo(positionMs: Long) {
        if (!state.value.ready) return
        val target = positionMs.coerceIn(0, state.value.durationMs)
        wish { seekToMs = target }
        mutableState.update { it.copy(positionMs = target) }
    }

    override fun setSound(settings: SoundSettings) {
        wish {
            this.settings = settings
            settingsChanged = true
        }
        mutableState.update { it.copy(processed = !SoundRules.isNeutral(settings)) }
    }

    override fun setOriginal(original: Boolean) {
        wish { this.original = original }
        mutableState.update { it.copy(original = original) }
    }

    override fun release() {
        worker?.let { running ->
            running.released = true
            synchronized(lock) { lock.notifyAll() }
            running.join(JOIN_TIMEOUT_MS)
        }
        worker = null
        synchronized(lock) {
            wantPlaying = false
            seekToMs = NO_SEEK
        }
        mutableMeters.value = null
        mutableState.update { PlayerState(processed = it.processed, original = it.original, backingHeard = it.backingHeard) }
    }

    private inline fun wish(change: () -> Unit) = synchronized(lock) {
        change()
        lock.notifyAll()
    }

    private inner class Worker(private val file: File, private val backing: PlayerBacking?) : Thread("session-player") {
        @Volatile var released = false

        override fun run() {
            val decoder = PcmDecoder.open(file)
            if (decoder == null) {
                if (!released) mutableState.update { PlayerState(failed = true, processed = it.processed, original = it.original) }
                return
            }
            var track: AudioTrack? = null
            // the backing's sound at this recording's rate, prepared here, off the main thread; gone — the violin alone
            val pcm = backing?.let { runCatching { it.pcm(decoder.sampleRate) }.getOrNull() }
            val reader = pcm?.let { runCatching { BackingPcmReader(it) }.getOrNull() }
            try {
                track = newTrack(decoder.sampleRate, stereo = reader != null)
                play(decoder, track, reader)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "playback of ${file.name} broke down", e)
                fail()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "no audio output for ${file.name}", e)
                fail()
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "no audio output for ${file.name}", e)
                fail()
            } finally {
                track?.release()
                decoder.release()
                reader?.close()
            }
        }

        private fun fail() {
            if (!released) mutableState.update { PlayerState(failed = true, processed = it.processed, original = it.original) }
        }

        private fun play(decoder: PcmDecoder, track: AudioTrack, reader: BackingPcmReader?) {
            val rate = decoder.sampleRate
            val chain = SoundChain(rate, config)
            val backingMix = reader?.let {
                val (offset, gain, heard) = synchronized(lock) { backingChanged = false; Triple(backingOffsetMs, backingGainDb, backingHeard) }
                BackingMixer(rate, PcmBackingSource(it), offset, gain, heard, fadeSamples = (backingConfig.shiftFadeMs * rate / MS_PER_SECOND).toInt(), soundConfig = config)
            }
            val stereo = FloatArray(if (backingMix != null) CHUNK * 2 else 0)
            /** The violin's sample the next chunk starts at: where the backing is read from. */
            var violinPosition = 0L
            val mixer = AbMixer(chain.latencySamples, fadeSamples = (AB_FADE_MS * rate / MS_PER_SECOND).toInt())
            val durationMs = decoder.durationUs / US_PER_MS
            val pcm = ShortArray(CHUNK)
            val dry = FloatArray(CHUNK)
            val wet = FloatArray(CHUNK)

            var current = synchronized(lock) { settingsChanged = false; settings }
            chain.set(current, immediate = true)
            var processing = !SoundRules.isNeutral(current)
            mixer.jumpTo(if (processing && !synchronized(lock) { original }) 1f else 0f)

            var baseMs = 0L
            var headBase = 0L
            /** Samples of silence still to be pushed through after the end: the hall rings on, the delays empty out. */
            var tailLeft = NO_TAIL
            var running = false
            var sinceMeters = 0

            mutableState.update { it.copy(ready = true, durationMs = durationMs, positionMs = 0, playing = false, failed = false, hasBacking = backingMix != null) }

            while (!released) {
                // — wishes —
                var seek: Long
                var playing: Boolean
                var wantOriginal: Boolean
                var newSettings: SoundSettings? = null
                var newBacking: Triple<Int, Float, Boolean>? = null
                synchronized(lock) {
                    if (!wantPlaying && seekToMs == NO_SEEK && !settingsChanged && !backingChanged && !released) {
                        if (running) {
                            track.pause()
                            running = false
                            mutableMeters.value = null
                        }
                        lock.wait()
                    }
                    seek = seekToMs
                    seekToMs = NO_SEEK
                    playing = wantPlaying
                    wantOriginal = original
                    if (settingsChanged) {
                        newSettings = settings
                        settingsChanged = false
                    }
                    if (backingChanged) {
                        newBacking = Triple(backingOffsetMs, backingGainDb, backingHeard)
                        backingChanged = false
                    }
                }
                if (released) break
                newBacking?.let { (offset, gain, heard) -> backingMix?.set(offset, gain, heard) }

                newSettings?.let { fresh ->
                    val wasProcessing = processing
                    current = fresh
                    processing = !SoundRules.isNeutral(fresh)
                    // A chain that has been resting knows nothing of the last seconds: it starts clean, and the fade covers its first moment.
                    if (processing && !wasProcessing && mixer.originalOnly) chain.reset()
                    chain.set(fresh)
                }
                if (seek != NO_SEEK) {
                    track.pause()
                    track.flush()
                    running = false
                    decoder.seekTo(seek * US_PER_MS)
                    chain.reset()
                    mixer.reset()
                    backingMix?.reset()
                    violinPosition = seek * rate / MS_PER_SECOND
                    baseMs = seek
                    headBase = head(track)
                    tailLeft = NO_TAIL
                }
                if (!playing) continue
                if (!running) {
                    track.play()
                    running = true
                }

                // — a chunk of sound —
                var count = if (tailLeft == NO_TAIL) decoder.read(pcm) else PcmDecoder.END
                if (count == PcmDecoder.END) {
                    if (tailLeft == NO_TAIL) tailLeft = chain.latencySamples + (backingMix?.latencySamples ?: 0) + if (mixer.originalOnly) 0 else chain.tailSamples(current)
                    count = minOf(tailLeft, CHUNK)
                    tailLeft -= count
                    dry.fill(0f, 0, count)
                } else {
                    for (i in 0 until count) dry[i] = pcm[i] / FULL_SCALE
                }

                mixer.target = if (processing && !wantOriginal) 1f else 0f
                val out = if (mixer.originalOnly) {
                    mixer.passOriginal(dry, count)
                    dry
                } else {
                    dry.copyInto(wet, 0, 0, count)
                    chain.process(wet, count)
                    mixer.mix(dry, wet, count)
                    wet
                }
                val written = if (backingMix != null) {
                    backingMix.mix(out, count, violinPosition, stereo)
                    write(track, stereo, count * 2)
                } else {
                    write(track, out, count)
                }
                violinPosition += count
                if (!written) continue // a wish came in mid-chunk: it goes first

                val positionMs = (baseMs + (head(track) - headBase) * MS_PER_SECOND / rate).coerceIn(0, durationMs)
                mutableState.update { if (it.playing) it.copy(positionMs = positionMs) else it }
                sinceMeters += count
                if (sinceMeters >= rate / METERS_PER_SECOND) {
                    sinceMeters = 0
                    mutableMeters.value = if (mixer.originalOnly) null else chain.takeMeters()
                }

                if (tailLeft == 0) {
                    // The end: let what is in the track play out, then back to the start, ready to play again (spec 3.10).
                    drain(track)
                    track.pause()
                    track.flush()
                    running = false
                    decoder.seekTo(0)
                    chain.reset()
                    mixer.reset()
                    backingMix?.reset()
                    violinPosition = 0
                    baseMs = 0
                    headBase = head(track)
                    tailLeft = NO_TAIL
                    synchronized(lock) { wantPlaying = false }
                    mutableMeters.value = null
                    mutableState.update { it.copy(playing = false, positionMs = 0) }
                }
            }
        }

        /**
         * Without blocking: a paused track never takes a full buffer, and a blocking write would hang
         * the thread — and with it every wish — for good. False — a seek or a release came in the
         * middle; the rest of the chunk is dropped.
         */
        private fun write(track: AudioTrack, samples: FloatArray, count: Int): Boolean {
            var offset = 0
            while (offset < count) {
                val written = track.write(samples, offset, count - offset, AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) throw IllegalStateException("AudioTrack.write returned $written")
                offset += written
                if (offset < count) {
                    if (released) return false
                    val interrupted = synchronized(lock) {
                        if (seekToMs != NO_SEEK) return@synchronized true
                        if (!wantPlaying) {
                            // Paused mid-chunk: hold the rest until the sound goes on, so that not a sample is lost.
                            track.pause()
                            while (!wantPlaying && seekToMs == NO_SEEK && !released) lock.wait()
                            if (seekToMs != NO_SEEK || released) return@synchronized true
                            track.play()
                        }
                        false
                    }
                    if (interrupted) return false
                    sleep(WRITE_RETRY_MS)
                }
            }
            return true
        }

        private fun drain(track: AudioTrack) {
            val deadline = System.nanoTime() + DRAIN_TIMEOUT_NS
            var last = head(track)
            while (!released && System.nanoTime() < deadline) {
                sleep(DRAIN_POLL_MS)
                val now = head(track)
                if (now == last) return
                last = now
                if (synchronized(lock) { seekToMs != NO_SEEK }) return
            }
        }

        /** The head position is an unsigned 32-bit counter in a signed int. */
        private fun head(track: AudioTrack): Long = track.playbackHeadPosition.toLong() and UNSIGNED_INT

        private fun newTrack(rate: Int, stereo: Boolean): AudioTrack {
            val mask = if (stereo) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val channels = if (stereo) 2 else 1
            val minimum = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Twice the minimum: room for a hiccup, and still only ~0.1 s between a turned knob and the ear.
                .setBufferSizeInBytes(minimum.coerceAtLeast(CHUNK * BYTES_PER_FLOAT * channels) * 2)
                .build()
        }
    }

    private companion object {
        const val TAG = "SessionPlayer"
        const val NO_SEEK = -1L
        const val NO_TAIL = -1

        /** ~43 ms at 48 kHz: how often wishes are looked at and the position is told. */
        const val CHUNK = 2_048
        const val FULL_SCALE = 32_768f
        const val BYTES_PER_FLOAT = 4
        const val MS_PER_SECOND = 1_000L
        const val US_PER_MS = 1_000L
        const val AB_FADE_MS = 60L
        const val METERS_PER_SECOND = 30
        const val WRITE_RETRY_MS = 5L
        const val DRAIN_POLL_MS = 20L
        const val DRAIN_TIMEOUT_NS = 1_000_000_000L
        const val JOIN_TIMEOUT_MS = 1_000L
        const val UNSIGNED_INT = 0xFFFFFFFFL
    }
}
