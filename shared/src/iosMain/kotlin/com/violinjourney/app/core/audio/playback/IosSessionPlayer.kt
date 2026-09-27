package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.IosAudioSession
import com.violinjourney.app.core.audio.asULong
import com.violinjourney.app.core.audio.backing.BackingMixer
import com.violinjourney.app.core.audio.backing.IosBackingPcmReader
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.interruptionEndsInput
import com.violinjourney.app.core.audio.routeChangeStopsSound
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.recording.IosPcmFileOpener
import com.violinjourney.app.core.recording.OpenedPcm
import com.violinjourney.app.core.recording.PcmSource
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionReasonKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL

/**
 * [SessionPlayer] of iOS, the same chain as `ChainSessionPlayer` on Android: the file is decoded by AVAudioFile, every
 * chunk passes through [SoundChain] and the A/B mixer — what is heard is what is sent (spec 3.17) — and goes to an
 * AVAudioPlayerNode, a few chunks ahead of the ear. One worker per loaded file; the calls leave wishes it picks up
 * between two chunks. A take under a backing (spec 3.32) is mixed with it by the same [BackingMixer] as on Android.
 * The output (the engine) is taken on play and paused half a second after the sound stops — a pause or the end of the
 * take — so an open screen with nothing playing does not keep the audio hardware running. The audio session, shared
 * with the microphones, is held through [IosAudioSession] from the first play until the player is released — another
 * file loaded keeps it, so other apps' music does not come back between two recordings picked on «Звук». When iOS stops
 * the engine by itself (a change of headphones) or a call, an alarm or Siri interrupts it, the player pauses where the
 * sound was; headphones taken off pause it too, rather than let it go on out of the loudspeaker.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSessionPlayer internal constructor(
    private val scope: CoroutineScope,
    private val config: SoundConfig,
    private val backingConfig: BackingConfig,
    /** The engine of each loaded file; a test gives one that renders by hand, with no audio hardware behind it. */
    private val newEngine: () -> AVAudioEngine,
) : SessionPlayer {
    constructor(scope: CoroutineScope, config: SoundConfig, backingConfig: BackingConfig = BackingConfig()) :
        this(scope, config, backingConfig, { AVAudioEngine() })

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
    private var outputLost = false

    /** Counted among the users of the app's audio session: from the first play until [release], across loads. */
    private var holdsSession = false

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null

    override fun load(file: PlatformFile) = loadWithBacking(file, null)

    override fun loadWithBacking(file: PlatformFile, backing: PlayerBacking?) {
        stopWorker()
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
        val ending = worker
        stopWorker()
        val held = lock.withLock { holdsSession.also { holdsSession = false } }
        if (!held) return
        // the last one using the session lets it go, and music another app had on comes back — once the worker has
        // stopped its engine: a session let go under running sound stops it with an error
        if (ending == null) IosAudioSession.leave() else ending.invokeOnCompletion { IosAudioSession.leave() }
    }

    /** The file let go, the session kept: what a new load and [release] have in common. */
    private fun stopWorker() {
        worker?.cancel()
        worker = null
        lock.withLock {
            wantPlaying = false
            seekToMs = NO_SEEK
            outputLost = false
        }
        mutableMeters.value = null
        mutableState.update { PlayerState(processed = it.processed, original = it.original, backingHeard = it.backingHeard) }
    }

    private inline fun wish(change: () -> Unit) {
        lock.withLock(change)
        wake.trySend(Unit)
    }

    /**
     * iOS stopped the engine by itself — the route's rate or channels changed, as headphones come and go — and said so
     * only by a notification: the sound on the node is gone with it. The player pauses where the ear was, as players do
     * when the route is lost, instead of showing «playing» in silence; the next play takes the output again.
     */
    private fun outputStopped() {
        wish {
            wantPlaying = false
            outputLost = true
        }
        mutableState.update { it.copy(playing = false) }
    }

    /** The output the sound went to is gone — headphones taken off: a pause where the ear was, as a tap on pause would. */
    private fun outputGone() {
        wish { wantPlaying = false }
        mutableState.update { it.copy(playing = false) }
    }

    private suspend fun run(file: PlatformFile, backing: PlayerBacking?) {
        // Every write of the state checks the worker inside its update, and every wish it clears checks it under the
        // lock: a new load and release() cancel it before they write a fresh state, so a worker let go never lands its
        // «ready», «failed», position or meters in the state of the player's next file, nor takes back its «play».
        val job = currentCoroutineContext().job
        val source = openSound(file)
        val rate = source?.rate ?: 0
        if (source == null || rate <= 0 || source.length <= 0) {
            source?.close()
            mutableState.update { if (!job.isActive) it else PlayerState(failed = true, processed = it.processed, original = it.original) }
            return
        }
        // the backing's sound at this recording's rate, prepared here, off the main thread; gone — the violin alone
        val pcm = backing?.let { given ->
            given.cached(rate) ?: run {
                mutableState.update { if (!job.isActive) it else it.copy(preparingBacking = true) }
                given.pcm(rate)
            }
        }
        if (!job.isActive) {
            // let go while the backing was made — a blocking call the cancel cannot stop: what was made stays for the next
            // time (spec 5.25), nothing of it shows here
            source.close()
            return
        }
        val reader = pcm?.let { runCatching { IosBackingPcmReader(it) }.getOrNull() }
        val engine = newEngine()
        val node = AVAudioPlayerNode()
        val format = AVAudioFormat(standardFormatWithSampleRate = rate.toDouble(), channels = if (reader != null) 2u else 1u)
        engine.attachNode(node)
        engine.connect(node, engine.mainMixerNode, format)
        val center = NSNotificationCenter.defaultCenter
        // posted on a thread of the system's: what is done there only leaves a wish; a player let go hears nothing more
        val observers = listOf(
            center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) { _ ->
                if (job.isActive && !engine.running) outputStopped()
            },
            // a call, an alarm, Siri: iOS stops the engine and says so only here — no completions come any more
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { note ->
                val type = note?.userInfo?.get(AVAudioSessionInterruptionTypeKey).asULong()
                val reason = note?.userInfo?.get(AVAudioSessionInterruptionReasonKey).asULong()
                if (job.isActive && interruptionEndsInput(type, reason, engine.running)) outputStopped()
            },
            center.addObserverForName(AVAudioSessionRouteChangeNotification, null, null) { note ->
                if (job.isActive && routeChangeStopsSound(note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey).asULong())) outputGone()
            },
        )
        try {
            Playback(source, rate, engine, node, format, reader, job).loop()
        } finally {
            observers.forEach(center::removeObserver)
            node.stop()
            engine.stop()
            reader?.close()
            source.close()
        }
    }

    private fun openSound(file: PlatformFile): SoundSource? {
        val recording = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // a file AVAudioFile cannot read makes its init return nil, which Kotlin/Native throws as an NPE
            try {
                AVAudioFile(forReading = NSURL.fileURLWithPath(file.path), error = error.ptr)
            } catch (_: NullPointerException) {
                null
            }?.takeIf { error.value == null }
        }
        if (recording != null) return FileSound(recording)
        // a video: AVAudioFile on iOS opens no file with a picture in it
        return IosPcmFileOpener.open(file)?.let { AssetSound(file, it) } ?: run {
            log("cannot read ${file.path}")
            null
        }
    }

    /** One file on one node: the state of the sound between two chunks. */
    private inner class Playback(
        private val source: SoundSource,
        private val rate: Int,
        private val engine: AVAudioEngine,
        private val node: AVAudioPlayerNode,
        private val format: AVAudioFormat,
        reader: IosBackingPcmReader?,
        /** The worker: once it is let go, nothing of this file is written into the state. */
        private val job: Job,
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
                if (!job.isActive) it else it.copy(ready = true, durationMs = durationMs, positionMs = 0, playing = false, failed = false, hasBacking = backingMix != null, preparingBacking = false)
            }

            while (kotlin.coroutines.coroutineContext.isActive) {
                val (seek, playing, wantOriginal, fresh, lost) = lock.withLock {
                    val wishes = Wishes(seekToMs, wantPlaying, original, if (settingsChanged) settings else null, outputLost)
                    seekToMs = NO_SEEK
                    settingsChanged = false
                    outputLost = false
                    if (backingChanged) {
                        backingChanged = false
                        backingMix?.set(backingOffsetMs, backingGainDb, backingHeard)
                    }
                    wishes
                }
                fresh?.let {
                    current = it
                    processing = !SoundRules.isNeutral(it)
                    chain.set(it)
                }
                if (lost) {
                    // the stopped engine took the sound on the node with it: on from where the ear was
                    val heard = heardMs()
                    restartAt(heard)
                    showMeters(null)
                    mutableState.update { if (!job.isActive) it else it.copy(positionMs = heard) }
                }
                if (seek != NO_SEEK) restartAt(seek)
                if (!playing) {
                    if (running) {
                        node.pause()
                        running = false
                        showMeters(null)
                    }
                    if (engine.running) {
                        // the output is let go only after a stretch of silence; any wish before that — a new look at them all
                        if (withTimeoutOrNull(OUTPUT_LINGER_MS) { wake.receive() } == null) engine.pause()
                        continue // a wake lost to the timeout is no matter: the wishes are read again anyway
                    }
                    wake.receive()
                    continue
                }
                if (!running) {
                    if (!startOutput()) {
                        // the wishes are the player's, not the file's: a worker let go leaves them to the next file
                        lock.withLock { if (job.isActive) wantPlaying = false }
                        mutableState.update { if (!job.isActive) it else PlayerState(failed = true, processed = it.processed, original = it.original) }
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
                    lock.withLock { if (job.isActive) wantPlaying = false }
                    showMeters(null)
                    mutableState.update { if (!job.isActive) it else it.copy(playing = false, positionMs = 0) }
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
                // a chain that has been resting — «A», or settings that did nothing — starts clean; the fade covers its first moment
                if (mixer.aim(if (processing && !wantOriginal) 1f else 0f)) chain.reset()
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
                        // the A/B line hands the violin over chain.latencySamples late, processed or not: the backing is read as far
                        // behind — as the render does by dropping those samples (spec 3.17: what is heard is what is sent)
                        backingMix.mix(out, count, violinPosition - chain.latencySamples, stereo)
                        scheduleStereo(stereo, count)
                    } else {
                        schedule(out, count)
                    }
                }
                violinPosition += count
                sinceMeters += count
                if (sinceMeters >= rate / METERS_PER_SECOND) {
                    sinceMeters = 0
                    showMeters(if (mixer.originalOnly) null else chain.takeMeters())
                }
                tellPosition()
            }
        }

        private fun decode(): Int = source.read(dry, CHUNK)

        /** The meters of this file — nothing of it once the player has let it go. */
        private fun showMeters(meters: SoundMeters?) {
            if (job.isActive) mutableMeters.value = meters
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
            source.seek(positionMs * rate / MS_PER_SECOND)
            chain.reset()
            mixer.reset()
            backingMix?.reset()
            violinPosition = positionMs * rate / MS_PER_SECOND
            baseMs = positionMs
            tailLeft = NO_TAIL
        }

        /** Where the sound the node has played is in the take. */
        private fun heardMs(): Long = (baseMs + played.value * MS_PER_SECOND / rate).coerceIn(0, durationMs)

        private fun tellPosition() {
            val positionMs = heardMs()
            mutableState.update { if (it.playing && job.isActive) it.copy(positionMs = positionMs) else it }
        }

        /**
         * The output is taken when the sound goes on, not when the screen opens: a recording looked at is not heard. It
         * is taken again on every play after the engine was paused — [OUTPUT_LINGER_MS] after a pause or the end of a
         * take — with the category and the activation set again every time, as on the first play: a microphone opened
         * meanwhile made the session its own. The session is the app's one ([IosAudioSession]): the first play of the
         * player counts it among its users, and the player leaves when it is released — not when another file is loaded;
         * a play after that takes it again ([IosAudioSession.retake]), which gives back the I/O buffer a microphone asked for.
         */
        private fun startOutput(): Boolean {
            if (engine.running) return true
            if (lock.withLock { holdsSession }) {
                IosAudioSession.retake(::takeForPlaying)
            } else {
                IosAudioSession.enter(::takeForPlaying)
                // released while it was entering: what was just taken goes at once, and nothing plays
                val kept = lock.withLock { job.isActive.also { if (it) holdsSession = true } }
                if (!kept) {
                    IosAudioSession.leave()
                    return false
                }
            }
            return memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                engine.startAndReturnError(error.ptr).also { if (!it) log("no audio output: ${error.value?.localizedDescription}") }
            }
        }
    }

    private fun takeForPlaying(session: AVAudioSession) {
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    // NSLog takes Objective-C objects for its arguments: the line is made whole here, its percent signs doubled.
    private fun log(line: String) = NSLog("SessionPlayer: $line".replace("%", "%%"))

    /** The sound of a take as the player reads it: from the start, a chunk at a time, anywhere after a seek. */
    private interface SoundSource {
        val rate: Int
        val length: Long

        /** Up to [max] samples, the first channel, into [into]; 0 at the end. */
        fun read(into: FloatArray, max: Int): Int

        fun seek(frame: Long)

        fun close()
    }

    /** A recording, by AVAudioFile. */
    private class FileSound(private val file: AVAudioFile) : SoundSource {
        override val rate = file.processingFormat.sampleRate.toInt()
        override val length = file.length
        private val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = CHUNK.toUInt())

        override fun read(into: FloatArray, max: Int): Int {
            buffer.frameLength = 0u
            if (!file.readIntoBuffer(buffer, minOf(max, CHUNK).toUInt(), null)) return 0
            val count = buffer.frameLength.toInt()
            val channel = buffer.floatChannelData?.get(0) ?: return 0
            for (i in 0 until count) into[i] = channel[i]
            return count
        }

        override fun seek(frame: Long) {
            file.framePosition = frame
        }

        override fun close() = Unit
    }

    /** The sound of a video, by AVAssetReader: it reads only forward, so a seek opens a new one where the sound is wanted. */
    private class AssetSound(private val file: PlatformFile, first: OpenedPcm) : SoundSource {
        private var pcm: OpenedPcm? = first
        override val rate = first.sampleRate
        override val length = first.totalSamples
        private var samples = ShortArray(0)

        override fun read(into: FloatArray, max: Int): Int {
            val source = pcm ?: return 0
            if (samples.size != max) samples = ShortArray(max)
            val count = source.read(samples)
            if (count == PcmSource.END) return 0
            for (i in 0 until count) into[i] = samples[i] / FULL_SCALE
            return count
        }

        override fun seek(frame: Long) {
            pcm?.release()
            pcm = IosPcmFileOpener.openAt(file, frame)
        }

        override fun close() {
            pcm?.release()
            pcm = null
        }
    }

    private data class Wishes(val seek: Long, val playing: Boolean, val original: Boolean, val settings: SoundSettings?, val outputLost: Boolean)

    internal companion object {
        private const val NO_SEEK = -1L
        private const val NO_TAIL = -1

        /** ~43 ms at 48 kHz, as on Android. */
        private const val CHUNK = 2_048

        /** Chunks on the node ahead of the ear: ~0.13 s between a turned knob and the sound. */
        private const val AHEAD = 3

        /**
         * Silence after a pause or the end of a take before the audio hardware is let go (the engine paused, not
         * stopped): what the node last played leaves the speaker whole, Bluetooth included, and a quick play or a turned
         * knob finds the output still on. A paused node alone does not stop an engine — its I/O went on rendering
         * silence for as long as the screen was open.
         */
        const val OUTPUT_LINGER_MS = 500L
        private const val MS_PER_SECOND = 1_000L
        private const val AB_FADE_MS = 60L
        private const val METERS_PER_SECOND = 30
        private const val FULL_SCALE = 32_768f
    }
}
