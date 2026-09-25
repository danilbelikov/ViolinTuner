package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.backing.BackingMixer
import com.violinjourney.app.core.audio.backing.IosBackingPcmReader
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSURL

/**
 * [SessionPlayer] of iOS, the same chain as `ChainSessionPlayer` on Android: the file is decoded by AVAudioFile, every
 * chunk passes through [SoundChain] and the A/B mixer — what is heard is what is sent (spec 3.17) — and goes to an
 * AVAudioPlayerNode, a few chunks ahead of the ear. One worker per loaded file; the calls leave wishes it picks up
 * between two chunks. A take under a backing (spec 3.32) is mixed with it by the same [BackingMixer] as on Android.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionPlayer(
    private val scope: CoroutineScope,
    private val config: SoundConfig,
    private val backingConfig: BackingConfig = BackingConfig(),
) : SessionPlayer {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val mutableMeters = MutableStateFlow<SoundMeters?>(null)
    override val meters: StateFlow<SoundMeters?> = mutableMeters.asStateFlow()

    private val lock = PlatformLock()
    private var wantPlaying = false
    private var seekToMs = NO_SEEK
    private var settings: SoundSettings = SoundRules.off(config)
    private var settingsChanged = true
    private var original = false
    private var backingOffsetMs = 0
    private var backingGainDb = 0f
    private var backingHeard = true
    private var backingChanged = false

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null

    override fun load(file: PlatformFile) = loadWithBacking(file, null)

    override fun loadWithBacking(file: PlatformFile, backing: PlayerBacking?) {
        release()
        lock.withLock {
            backingOffsetMs = backing?.offsetMs ?: 0
            backingGainDb = backing?.gainDb ?: 0f
            backingChanged = false
        }
        worker = scope.launch(Dispatchers.Default) { run(file, backing) }
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
        worker?.cancel()
        worker = null
        lock.withLock {
            wantPlaying = false
            seekToMs = NO_SEEK
        }
        mutableMeters.value = null
        mutableState.update { PlayerState(processed = it.processed, original = it.original, backingHeard = it.backingHeard) }
    }

    private inline fun wish(change: () -> Unit) {
        lock.withLock(change)
        wake.trySend(Unit)
    }

    private suspend fun run(file: PlatformFile, backing: PlayerBacking?) {
        val source = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioFile(forReading = NSURL.fileURLWithPath(file.path), error = error.ptr).takeIf { error.value == null }
                .also { if (it == null) log("cannot read ${file.path}: ${error.value?.localizedDescription}") }
        }
        val rate = source?.processingFormat?.sampleRate?.toInt() ?: 0
        if (source == null || rate <= 0 || source.length <= 0) {
            mutableState.update { PlayerState(failed = true, processed = it.processed, original = it.original) }
            return
        }
        // the backing's sound at this recording's rate, prepared here, off the main thread; gone — the violin alone
        val pcm = backing?.let { given ->
            given.cached(rate) ?: run {
                mutableState.update { it.copy(preparingBacking = true) }
                given.pcm(rate)
            }
        }
        val reader = pcm?.let { runCatching { IosBackingPcmReader(it) }.getOrNull() }
        val engine = AVAudioEngine()
        val node = AVAudioPlayerNode()
        val format = AVAudioFormat(standardFormatWithSampleRate = rate.toDouble(), channels = if (reader != null) 2u else 1u)
        engine.attachNode(node)
        engine.connect(node, engine.mainMixerNode, format)
        try {
            Playback(source, rate, node, format, reader) { startOutput(engine) }.loop()
        } finally {
            node.stop()
            engine.stop()
            reader?.close()
        }
    }

    /** One file on one node: the state of the sound between two chunks. */
    private inner class Playback(
        private val source: AVAudioFile,
        private val rate: Int,
        private val node: AVAudioPlayerNode,
        private val format: AVAudioFormat,
        reader: IosBackingPcmReader?,
        private val startOutput: () -> Boolean,
    ) {
        private val backingMix = reader?.let {
            val (offset, gain, heard) = lock.withLock { backingChanged = false; Triple(backingOffsetMs, backingGainDb, backingHeard) }
            BackingMixer(rate, it, offset, gain, heard, fadeSamples = (backingConfig.shiftFadeMs * rate / MS_PER_SECOND).toInt(), soundConfig = config)
        }
        private val stereo = FloatArray(if (backingMix != null) CHUNK * 2 else 0)

        /** The violin's sample the next chunk starts at: where the backing is read from. */
        private var violinPosition = 0L
        private val chain = SoundChain(rate, config)
        private val mixer = AbMixer(chain.latencySamples, fadeSamples = (AB_FADE_MS * rate / MS_PER_SECOND).toInt())
        private val durationMs = source.length * MS_PER_SECOND / rate
        private val read = AVAudioPCMBuffer(pCMFormat = source.processingFormat, frameCapacity = CHUNK.toUInt())
        private val dry = FloatArray(CHUNK)
        private val wet = FloatArray(CHUNK)

        /** Buffers on the node not played yet; the completions of a flushed generation do not count. */
        private val queued = AtomicInt(0)
        private val played = AtomicLong(0)
        private val generation = AtomicInt(0)
        private var baseMs = 0L
        private var tailLeft = NO_TAIL
        private var running = false
        private var sinceMeters = 0

        suspend fun loop() {
            var current = lock.withLock { settingsChanged = false; settings }
            chain.set(current, immediate = true)
            var processing = !SoundRules.isNeutral(current)
            mixer.jumpTo(if (processing && !lock.withLock { original }) 1f else 0f)
            mutableState.update {
                it.copy(ready = true, durationMs = durationMs, positionMs = 0, playing = false, failed = false, hasBacking = backingMix != null, preparingBacking = false)
            }

            while (kotlin.coroutines.coroutineContext.isActive) {
                val (seek, playing, wantOriginal, fresh) = lock.withLock {
                    val wishes = Wishes(seekToMs, wantPlaying, original, if (settingsChanged) settings else null)
                    seekToMs = NO_SEEK
                    settingsChanged = false
                    if (backingChanged) {
                        backingChanged = false
                        backingMix?.set(backingOffsetMs, backingGainDb, backingHeard)
                    }
                    wishes
                }
                fresh?.let {
                    val wasProcessing = processing
                    current = it
                    processing = !SoundRules.isNeutral(it)
                    if (processing && !wasProcessing && mixer.originalOnly) chain.reset()
                    chain.set(it)
                }
                if (seek != NO_SEEK) restartAt(seek)
                if (!playing) {
                    if (running) {
                        node.pause()
                        running = false
                        mutableMeters.value = null
                    }
                    wake.receive()
                    continue
                }
                if (!running) {
                    if (!startOutput()) {
                        lock.withLock { wantPlaying = false }
                        mutableState.update { PlayerState(failed = true, processed = it.processed, original = it.original) }
                        return
                    }
                    node.play()
                    running = true
                }
                if (queued.value >= AHEAD) {
                    tellPosition()
                    wake.receive()
                    continue
                }
                if (tailLeft == 0) {
                    // everything is scheduled: wait for the node to play it out, then back to the start (spec 3.10)
                    if (queued.value > 0) {
                        tellPosition()
                        wake.receive()
                        continue
                    }
                    restartAt(0)
                    lock.withLock { wantPlaying = false }
                    mutableMeters.value = null
                    mutableState.update { it.copy(playing = false, positionMs = 0) }
                    continue
                }

                // — a chunk of sound —
                var count = if (tailLeft == NO_TAIL) decode() else 0
                if (count == 0) {
                    if (tailLeft == NO_TAIL) tailLeft = chain.latencySamples + (backingMix?.latencySamples ?: 0) + if (mixer.originalOnly) 0 else chain.tailSamples(current)
                    count = minOf(tailLeft, CHUNK)
                    tailLeft -= count
                    dry.fill(0f, 0, count)
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
                if (count > 0) {
                    if (backingMix != null) {
                        backingMix.mix(out, count, violinPosition, stereo)
                        scheduleStereo(stereo, count)
                    } else {
                        schedule(out, count)
                    }
                }
                violinPosition += count
                sinceMeters += count
                if (sinceMeters >= rate / METERS_PER_SECOND) {
                    sinceMeters = 0
                    mutableMeters.value = if (mixer.originalOnly) null else chain.takeMeters()
                }
                tellPosition()
            }
        }

        private fun decode(): Int {
            read.frameLength = 0u
            if (!source.readIntoBuffer(read, CHUNK.toUInt(), null)) return 0
            val count = read.frameLength.toInt()
            val channel = read.floatChannelData?.get(0) ?: return 0
            for (i in 0 until count) dry[i] = channel[i]
            return count
        }

        private fun schedule(samples: FloatArray, count: Int) {
            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = count.toUInt())
            buffer.frameLength = count.toUInt()
            val channel = buffer.floatChannelData?.get(0) ?: return
            for (i in 0 until count) channel[i] = samples[i]
            enqueue(buffer, count)
        }

        /** [samples] interleaved left and right, as the mix of the backing gives them. */
        private fun scheduleStereo(samples: FloatArray, count: Int) {
            val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = count.toUInt())
            buffer.frameLength = count.toUInt()
            val channels = buffer.floatChannelData ?: return
            val left = channels[0] ?: return
            val right = channels[1] ?: return
            for (i in 0 until count) {
                left[i] = samples[2 * i]
                right[i] = samples[2 * i + 1]
            }
            enqueue(buffer, count)
        }

        private fun enqueue(buffer: AVAudioPCMBuffer, count: Int) {
            val mine = generation.value
            queued.incrementAndGet()
            node.scheduleBuffer(buffer) {
                if (mine == generation.value) {
                    queued.decrementAndGet()
                    played.addAndGet(count.toLong())
                }
                wake.trySend(Unit)
            }
        }

        private fun restartAt(positionMs: Long) {
            generation.incrementAndGet()
            node.stop()
            running = false
            queued.value = 0
            played.value = 0
            source.framePosition = positionMs * rate / MS_PER_SECOND
            chain.reset()
            mixer.reset()
            backingMix?.reset()
            violinPosition = positionMs * rate / MS_PER_SECOND
            baseMs = positionMs
            tailLeft = NO_TAIL
        }

        private fun tellPosition() {
            val positionMs = (baseMs + played.value * MS_PER_SECOND / rate).coerceIn(0, durationMs)
            mutableState.update { if (it.playing) it.copy(positionMs = positionMs) else it }
        }
    }

    /** The output is taken when the sound first goes on, not when the screen opens: a recording looked at is not heard. */
    private fun startOutput(engine: AVAudioEngine): Boolean {
        if (engine.running) return true
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(error.ptr).also { if (!it) log("no audio output: ${error.value?.localizedDescription}") }
        }
    }

    // NSLog takes Objective-C objects for its arguments: the line is made whole here, its percent signs doubled.
    private fun log(line: String) = NSLog("SessionPlayer: $line".replace("%", "%%"))

    private data class Wishes(val seek: Long, val playing: Boolean, val original: Boolean, val settings: SoundSettings?)

    private companion object {
        const val NO_SEEK = -1L
        const val NO_TAIL = -1

        /** ~43 ms at 48 kHz, as on Android. */
        const val CHUNK = 2_048

        /** Chunks on the node ahead of the ear: ~0.13 s between a turned knob and the sound. */
        const val AHEAD = 3
        const val MS_PER_SECOND = 1_000L
        const val AB_FADE_MS = 60L
        const val METERS_PER_SECOND = 30
    }
}
