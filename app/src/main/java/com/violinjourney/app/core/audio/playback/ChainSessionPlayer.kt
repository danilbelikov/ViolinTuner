package com.violinjourney.app.core.audio.playback

import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.violinjourney.app.core.audio.backing.BackingPcmReader
import com.violinjourney.app.core.audio.backing.PcmBackingSource
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [SessionPlayer] of our own making: decoder → [PlayerSound] → `AudioTrack`, on a thread of its
 * own. `MediaPlayer` could only play a file as it is; here the samples pass through the very
 * chain that also renders the file to be shared — what is heard is what is sent (spec 3.17).
 * [PlayerSound] is the same on iOS; the thread, the track and the wishes are Android's.
 *
 * The calls of the interface only leave wishes (play, seek, settings, A/B) for the thread, which
 * picks them up between two chunks of sound — about every 40 ms.
 *
 * While it plays it holds the phone's sound ([PlaybackFocus]): a call, another player or pulled-out headphones pause it,
 * and it gives the sound back on a pause, at the end of the recording and on [release]. Main thread, as its callers are.
 *
 * Its position is what is heard ([HeardClock]): the head of the track less the delay of the output, which
 * `AudioTrack.getTimestamp` tells. The picture of a video take, the slider and the cursor of the note roll follow it, so
 * none of them runs ahead of the ear — by 20–60 ms through the speaker, more in Bluetooth headphones (spec 5.13).
 */
class ChainSessionPlayer(
    private val config: SoundConfig,
    private val backingConfig: BackingConfig,
    private val focus: PlaybackFocus,
) : SessionPlayer {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val mutableMeters = MutableStateFlow<SoundMeters?>(null)
    override val meters: StateFlow<SoundMeters?> = mutableMeters.asStateFlow()

    private val lock = Object()
    private var worker: Worker? = null

    /** The end of the recording, told by the worker: the sound goes back unless a play came in first. */
    private val main = Handler(Looper.getMainLooper())
    private val giveBackIfQuiet = Runnable { if (!state.value.playing) focus.give() }

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
        // refused while a call is on: the button stays «play»
        if (!focus.take(onLost = ::pause)) return
        wish { wantPlaying = true }
        mutableState.update { it.copy(playing = true) }
    }

    override fun pause() {
        focus.give()
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
        focus.give()
        worker?.let { running ->
            // written before [Worker.unpacking] is read, as the worker writes that before it reads this: one of the two sees the other
            running.released = true
            synchronized(lock) { lock.notifyAll() }
            // A worker unpacking the backing holds no output and cannot stop mid-file (what it makes is kept for the next
            // time, spec 5.25): it goes by itself when done, and the main thread does not wait up to a second for it.
            // Any other one is out within a chunk.
            if (!running.unpacking) running.join(JOIN_TIMEOUT_MS)
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
        // Every write of the state checks this inside its update: release() sets it before it writes a fresh state, so a
        // worker let go never lands its «ready», «failed» or position in the state of the player's next file.
        @Volatile var released = false

        /** Inside the backing's unpack, which does not stop mid-file: [release] does not wait for it. */
        @Volatile var unpacking = false

        /**
         * When the track last began to play — between chunks or in the middle of one ([write]): a stamp from before it is
         * of sound that went before a pause, and carried on across the pause it would make the delay come out too short.
         * This thread's only.
         */
        private var playingSinceNanos = 0L

        override fun run() {
            val decoder = PcmDecoder.open(file)
            if (decoder == null) {
                fail()
                return
            }
            var track: AudioTrack? = null
            // the backing's sound at this recording's rate, prepared here, off the main thread; gone — the violin alone
            val pcm = backing?.let { unpack(it, decoder.sampleRate) }
            if (released) {
                // let go while the backing was unpacked: what was made stays for the next time (spec 5.25), nothing of it shows here
                decoder.release()
                return
            }
            val reader = pcm?.let(::readerOf)
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
            } catch (e: IOException) {
                // the backing's file cut short or gone while it plays: this thread is bare, a throw would end the app
                Log.w(TAG, "the backing of ${file.name} could not be read", e)
                fail()
            } finally {
                track?.release()
                decoder.release()
                reader?.close()
            }
        }

        private fun fail() {
            mutableState.update { if (released) it else PlayerState(failed = true, processed = it.processed, original = it.original) }
        }

        /**
         * The backing's sound at [rate]: made already, or made here — [unpacking] meanwhile. Null — the violin alone: it
         * could not be made (spec 5.25), or the player was let go first.
         */
        private fun unpack(given: PlayerBacking, rate: Int): File? {
            // written before [released] is read, as release() writes that before it reads this: one of the two sees the other
            unpacking = true
            try {
                if (released) return null
                given.cached(rate)?.let { return it }
                mutableState.update { if (released) it else it.copy(preparingBacking = true) }
                return given.pcm(rate)
            } catch (e: IOException) {
                Log.w(TAG, "the backing of ${file.name} could not be made", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "the backing of ${file.name} could not be made", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "the backing of ${file.name} could not be made", e)
            } finally {
                unpacking = false
            }
            return null
        }

        /** Null — the violin alone: the made sound went away before it could be opened. */
        private fun readerOf(pcm: File): BackingPcmReader? = try {
            BackingPcmReader(pcm)
        } catch (e: IOException) {
            Log.w(TAG, "the backing of ${file.name} could not be opened", e)
            null
        }

        private fun play(decoder: PcmDecoder, track: AudioTrack, reader: BackingPcmReader?) {
            val rate = decoder.sampleRate
            val sound = synchronized(lock) {
                settingsChanged = false
                backingChanged = false
                PlayerSound(
                    rate, config, backingConfig, reader?.let(::PcmBackingSource), backingOffsetMs, backingGainDb, backingHeard,
                    settings, original,
                )
            }
            val channels = if (sound.stereo) 2 else 1
            val durationMs = decoder.durationUs / US_PER_MS
            val heard = HeardClock(rate, MAX_OUTPUT_LAG_MS, LAG_WAIT_MS)
            val stamp = AudioTimestamp()
            var sinceStamp = 0
            val pcm = ShortArray(PlayerSound.CHUNK)
            val read: (FloatArray) -> Int = { into ->
                val count = decoder.read(pcm)
                if (count == PcmDecoder.END) 0 else count.also { for (i in 0 until it) into[i] = pcm[i] / FULL_SCALE }
            }
            var running = false

            mutableState.update {
                if (released) it else it.copy(ready = true, durationMs = durationMs, positionMs = 0, playing = false, failed = false, hasBacking = sound.stereo, preparingBacking = false)
            }

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
                newBacking?.let { (offset, gain, heardBacking) -> sound.changeBacking(offset, gain, heardBacking) }
                newSettings?.let(sound::changeSettings)
                if (seek != NO_SEEK) {
                    track.pause()
                    track.flush()
                    running = false
                    decoder.seekTo(seek * US_PER_MS)
                    sound.restartAt(seek * rate / MS_PER_SECOND)
                    heard.startAt(seek, head(track))
                }
                if (!playing) continue
                if (!running) {
                    track.play()
                    running = true
                    playingSinceNanos = System.nanoTime()
                }

                // — a chunk of sound —
                val count = sound.next(wantOriginal, read)
                if (!write(track, sound.output, count * channels)) continue // a wish came in mid-chunk: it goes first

                // what the output presents: at every chunk until it has said it once, then twice a second (the route may change)
                sinceStamp += count
                if (!heard.lagKnown || sinceStamp >= rate * LAG_CHECK_MS / MS_PER_SECOND) {
                    sinceStamp = 0
                    if (track.getTimestamp(stamp) && stamp.nanoTime >= playingSinceNanos) {
                        heard.stamp(head(track), stamp.framePosition, stamp.nanoTime, System.nanoTime())
                    }
                }
                val positionMs = heard.positionMs(head(track), count, durationMs)
                mutableState.update { if (it.playing && !released) it.copy(positionMs = positionMs) else it }
                if (sound.metersDue(count)) mutableMeters.value = sound.meters()

                if (sound.ended) {
                    // The end: let what is in the track play out, then back to the start, ready to play again (spec 3.10).
                    drain(track)
                    track.pause()
                    track.flush()
                    running = false
                    decoder.seekTo(0)
                    sound.restartAt(0)
                    heard.startAt(0, head(track))
                    synchronized(lock) { wantPlaying = false }
                    mutableMeters.value = null
                    mutableState.update { if (released) it else it.copy(playing = false, positionMs = 0) }
                    if (!released) main.post(giveBackIfQuiet)
                }
            }
        }

        /**
         * Without blocking: a paused track never takes a full buffer, and a blocking write would hang
         * the thread — and with it every wish — for good. False — a seek or a release came in the
         * middle; the rest of the chunk is dropped. While the track is full the thread waits on the
         * lock for as long as the rest takes to play, but never so long that the track runs low
         * ([TrackRoom]), not in a loop of 5 ms naps: a wish wakes it at once.
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
                            // as in a pause between chunks: nothing plays, so there is nothing to show (spec 5.11)
                            mutableMeters.value = null
                            while (!wantPlaying && seekToMs == NO_SEEK && !released) lock.wait()
                            if (seekToMs != NO_SEEK || released) return@synchronized true
                            track.play()
                            playingSinceNanos = System.nanoTime()
                        } else if (!released) {
                            // the track is full: wait until more of the rest fits; pause, seek and release wake this at once
                            lock.wait(TrackRoom.waitMs(count - offset, track.channelCount, track.sampleRate))
                            if (seekToMs != NO_SEEK || released) return@synchronized true
                        }
                        false
                    }
                    if (interrupted) return false
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
                .setAudioAttributes(MediaAttributes.MUSIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                // Twice the minimum: room for a hiccup, and still only ~0.1 s between a turned knob and the ear.
                .setBufferSizeInBytes(minimum.coerceAtLeast(PlayerSound.CHUNK * BYTES_PER_FLOAT * channels) * 2)
                .build()
        }
    }

    private companion object {
        const val TAG = "SessionPlayer"
        const val NO_SEEK = -1L
        const val FULL_SCALE = 32_768f
        const val BYTES_PER_FLOAT = 4
        const val MS_PER_SECOND = 1_000L
        const val US_PER_MS = 1_000L
        const val DRAIN_POLL_MS = 20L
        const val DRAIN_TIMEOUT_NS = 1_000_000_000L
        const val JOIN_TIMEOUT_MS = 1_000L
        const val UNSIGNED_INT = 0xFFFFFFFFL

        /** A delay of the output longer than this is a stamp that lies (spec 5.13). */
        const val MAX_OUTPUT_LAG_MS = 1_000L

        /** How often the delay is asked for again once known: headphones may come or go while it plays. */
        const val LAG_CHECK_MS = 500L

        /** At the first play the position waits this long of sound, at most, for the output to say its delay. */
        const val LAG_WAIT_MS = 500L
    }
}
