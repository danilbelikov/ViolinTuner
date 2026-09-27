package com.violinjourney.app.core.audio

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.HopAudioTap
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.concurrent.Volatile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionReasonAppWasSuspended
import platform.AVFAudio.AVAudioSessionInterruptionReasonKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.inputLatency
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

/**
 * The microphone of iOS (spec 6): AVAudioEngine on a session made for measuring — play and record, so that a backing can
 * sound in the headphones of a take (spec 3.32), wireless ones as output only — no automatic gain, no noise
 * suppression, the counterpart of the UNPROCESSED source on Android. The input goes into an AVAudioSinkNode whose block
 * is C ([IosMicRing]): every I/O cycle — one hop: while a microphone is open the session asks for an I/O buffer that long
 * ([IosAudioSession.enterMicrophone]) — is copied into a ring on the audio thread, and Kotlin reads the ring on a thread
 * of its own. The rest — hops of 512, the digital-silence watchdog, FrameAnalyzer and the detector — is the same chain
 * with the same numbers as on Android, and Live follows the violin hop by hop, as there. Each collection opens its own
 * engine and closes it when cancelled; the session is the app's one ([IosAudioSession]), let go by the last one using
 * it. One source per screen, as on Android (`pitchSources`): the sound of a take and its clock belong to that screen alone.
 *
 * Fails with [MicUnavailableException] when the engine does not start, when the system takes the input away
 * (a call, Siri, an alarm: an interruption; a reset of the media services), when the engine stops by itself (the
 * route's rate or channels changed — headphones), when no sound at all comes for as long as the watchdog allows
 * zeros, when the reading falls a whole ring behind the input, and when the input gives exact zeros for longer than
 * that. The screen then says the microphone is unavailable, a take is saved as it is, and the input is opened again
 * (spec 3.4). The sound of a take is taken hop by hop, on the same sample clock as the frames.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosMicPitchSource(
    private val detectorFactory: PitchDetectorFactory,
    encoderFactory: PcmEncoderFactory,
    /** The owner's .debug app (`IosBuild.isDevApp`): a line per second of what the detector saw (FrameStats), for tuning the thresholds. */
    private val logStats: Boolean,
    /** The rate the input opened at, told on every opening: what the backing is made ready at next time ([IosRecordingRate]). */
    private val onInputRate: (Int) -> Unit = {},
    /** Where an encoder of a take that could not be made is told (spec 3.34). */
    analytics: Analytics = NoOpAnalytics(),
) : PitchSource {

    override val requiresMicPermission: Boolean = true

    private val tap = HopAudioTap(encoderFactory, Dispatchers.IO, analytics)
    override val audioTap: AudioTap get() = tap

    /** The ring of the collection running now, whose stamps are the clock of a take. */
    @Volatile private var current: IosMicRing? = null

    // the host time a block was captured at, against the index of its first sample: the clock of a take (spec 5.25)
    override val clock: SampleClock = SampleClock { tMs -> current?.nanosAt(tMs, AVAudioSession.sharedInstance().inputLatency) }

    override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
        val engine = AVAudioEngine()
        var entered = false
        val center = NSNotificationCenter.defaultCenter
        val observers = ArrayList<NSObjectProtocol>()
        // the node, once it is in the engine: only an attached node may be detached
        var attached: IosMicRing? = null
        try {
            val ioBufferSeconds = config.hopSizeSamples * IO_BUFFER_HOPS / PREFERRED_RATE_HZ
            val session = IosAudioSession.enterMicrophone(ioBufferSeconds, ::openSession)
            entered = true
            val input = engine.inputNode
            val format = input.outputFormatForBus(0u)
            val sampleRateHz = format.sampleRate.toInt()
            if (sampleRateHz <= 0 || format.channelCount == 0u) {
                throw unavailable(MicUnavailableReason.OPEN_FAILED, "the input has no format ($sampleRateHz Hz, ${format.channelCount} channels)")
            }
            onInputRate(sampleRateHz)
            // The audio thread never waits: a block that does not fit means the reading fell a whole ring behind, which
            // is a failure, not a reason to drop sound — the frame clock counts every sample.
            val opened = IosMicRing(RING_SAMPLES, sampleRateHz)
            current = opened
            observers += watch(center, session, engine, opened::fail)
            engine.attachNode(opened.node)
            attached = opened
            // the first channel is the microphone; a second one, where there is one, is the same sound
            engine.connect(input, opened.node, format)
            engine.prepare()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!engine.startAndReturnError(error.ptr)) {
                    throw unavailable(MicUnavailableReason.OPEN_FAILED, "AVAudioEngine did not start: ${error.value?.localizedDescription}")
                }
            }

            val splitter = HopSplitter(config.hopSizeSamples)
            val analyzer = FrameAnalyzer(detectorFactory.create(config), config, sampleRateHz)
            val watchdog = DigitalSilenceWatchdog(config, sampleRateHz)
            val stats = if (logStats) FrameStats(config, "ios rate=$sampleRateHz", ::log) else null
            val ready = ArrayList<PitchFrame>()
            val chunk = FloatArray(READ_CHUNK)
            val context = currentCoroutineContext()
            var samplesRead = 0L
            var countedTo = 0L
            // No sound at all for as long as exact zeros are allowed: the engine stopped without a word (spec 3.4).
            val stallLimitMs = config.digitalSilenceTimeoutMs
            while (true) {
                val count = opened.read(chunk)
                if (count > 0) {
                    splitter.push(chunk, count) { hop ->
                        if (watchdog.isDead(hop, hop.size)) {
                            throw unavailable(MicUnavailableReason.DIGITAL_SILENCE, "input is digitally silent, reopening")
                        }
                        // the same sample clock as FrameAnalyzer: the hop after a frame starts at that frame's time
                        tap.onHop(hop, hop.size, hopStartTMs = samplesRead * MS_PER_SECOND / sampleRateHz, sampleRateHz)
                        samplesRead += hop.size
                        analyzer.push(hop)?.let(ready::add)
                    }
                    for (frame in ready) {
                        stats?.add(frame)
                        emit(frame)
                    }
                    ready.clear()
                    if (logStats && samplesRead - countedTo >= sampleRateHz) {
                        countedTo = samplesRead
                        // a gap is an I/O cycle missed: it shifts the rest of a take against its backing (spec 5.25)
                        val counters = opened.takeCounters()
                        log("input blocks=${counters.blocks} largest=${counters.largest} gaps=${counters.gaps}")
                    }
                    continue
                }
                // what came before the end is taken first, as it is on Android
                opened.failure?.let { throw it }
                if (opened.isBehind) throw unavailable(MicUnavailableReason.READ_FAILED, "the analysis fell behind the input")
                val sound = awaitSound(opened::await, stallLimitMs * NANOS_PER_MILLI, WAIT_SLICE_NANOS) { context.isActive }
                if (!sound) {
                    context.ensureActive()
                    throw unavailable(MicUnavailableReason.READ_FAILED, "no sound from the input for $stallLimitMs ms")
                }
            }
        } finally {
            tap.onStreamEnded()
            observers.forEach(center::removeObserver)
            engine.stop()
            attached?.let { engine.detachNode(it.node) }
            current = null
            // the last one using the session lets it go, and music another app had on comes back
            if (entered) IosAudioSession.leaveMicrophone()
        }
    }.flowOn(Dispatchers.IO) // the reading waits on the ring, as AudioRecord.read waits on Android

    /**
     * What ends the input from outside: an interruption that begins (a call, Siri, an alarm), a reset of the media
     * services, and the engine stopping by itself — iOS stops it and only says so when the input's or the output's
     * rate or channels change, as headphones come and go. An engine stopped because another user in the app took the
     * session for playing (a player started while a Live that went away still listens for its two seconds) is not a
     * lost input and is not counted as one (spec 5.27; [engineStopEndsInput]): only a line is logged, and the
     * collection, which its screen no longer holds, is mostly cancelled before the no-sound limit — the limit ends it
     * otherwise, so a screen still open never freezes (and then it is counted: docs/notes/backing.md names when).
     */
    private fun watch(center: NSNotificationCenter, session: AVAudioSession, engine: AVAudioEngine, fail: (MicUnavailableException) -> Unit) = listOf(
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
            val type = note?.userInfo?.get(AVAudioSessionInterruptionTypeKey).asULong()
            val reason = note?.userInfo?.get(AVAudioSessionInterruptionReasonKey).asULong()
            if (interruptionEndsInput(type, reason, engine.running)) {
                fail(unavailable(MicUnavailableReason.READ_FAILED, "the system took the input away: an interruption (type $type, reason $reason)"))
            }
        },
        center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, NSOperationQueue.mainQueue) { _ ->
            fail(unavailable(MicUnavailableReason.READ_FAILED, "the system took the input away: the media services were reset"))
        },
        center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, NSOperationQueue.mainQueue) { _ ->
            val running = engine.running
            val category = session.category
            when {
                engineStopEndsInput(running, category) ->
                    fail(unavailable(MicUnavailableReason.READ_FAILED, "the audio engine stopped: its input or output changed"))
                !running -> log("the audio engine stopped: the session was taken for $category")
            }
        },
    )

    private fun openSession(session: AVAudioSession): AVAudioSession = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        // a session that records sends its sound to the earpiece unless told otherwise: whatever plays while the
        // microphone is open (a take, a backing listened to) goes to the loudspeaker, or to headphones when there are some
        val options = AVAudioSessionCategoryOptionAllowBluetoothA2DP or AVAudioSessionCategoryOptionDefaultToSpeaker
        val ok = session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, options, error.ptr) &&
            session.setPreferredSampleRate(PREFERRED_RATE_HZ, error.ptr) &&
            session.setActive(true, error.ptr)
        if (!ok) throw unavailable(MicUnavailableReason.OPEN_FAILED, "the audio session would not open: ${error.value?.localizedDescription}")
        session
    }

    /** Logged here because the presenter turns the exception into a screen state and retries. */
    private fun unavailable(reason: MicUnavailableReason, message: String): MicUnavailableException {
        log(message)
        return MicUnavailableException(reason, message)
    }

    // NSLog takes its variadic arguments as Objective-C objects, which Kotlin strings are not across that border:
    // the line is made whole here and given as the format, with its percent signs doubled.
    private fun log(line: String) = NSLog("$TAG: $line".replace("%", "%%"))

    private companion object {
        const val TAG = "MicPitchSource"
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L

        /**
         * Sound waiting for the analysis before it counts as fallen behind: 2^19 samples, about eleven seconds at 48 kHz —
         * a take being finished holds the reading for seconds.
         */
        const val RING_SAMPLES = 1 shl 19

        /** The I/O buffer the session is asked for, in hops: one — a hop per cycle; two would halve the cycles if a phone heats. */
        const val IO_BUFFER_HOPS = 1

        /** What is read from the ring at once. */
        const val READ_CHUNK = 4_096

        /** How long a wait for sound is before it looks whether its collection is still wanted. */
        const val WAIT_SLICE_NANOS = 20_000_000L
        const val PREFERRED_RATE_HZ = 48_000.0
    }
}

/**
 * Whether an interruption notification ends the input. One that begins does: a call, Siri or an alarm took the input.
 * One that ends does not — it can come after the input was already opened again, and ending that healthy input would
 * cost the player another pause. A late «began» for the time the app was suspended ends it only if the engine did
 * stop. A notification without a type is taken as a beginning.
 */
internal fun interruptionEndsInput(type: ULong?, reason: ULong?, engineRunning: Boolean): Boolean = when {
    type == AVAudioSessionInterruptionTypeEnded -> false
    reason == AVAudioSessionInterruptionReasonAppWasSuspended -> !engineRunning
    else -> true
}

/**
 * Whether a change of the audio engine's configuration ends the input: only when the engine did stop and the session
 * is still the microphone's (play and record) — iOS stopped it for a change of the route. An engine stopped because
 * another user in the app set the session for playing (the player of a recording, while a Live that went away still
 * listens) is not a lost input (spec 5.27 counts those); nor is one with no category to tell — the no-sound limit of
 * the reading ends the input later if it is really gone.
 */
internal fun engineStopEndsInput(engineRunning: Boolean, category: String?): Boolean =
    !engineRunning && category == AVAudioSessionCategoryPlayAndRecord
