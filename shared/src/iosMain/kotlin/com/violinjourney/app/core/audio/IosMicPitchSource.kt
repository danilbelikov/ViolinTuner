package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.backing.HostClock
import com.violinjourney.app.core.audio.recording.AudioTap
import kotlin.concurrent.Volatile
import com.violinjourney.app.core.audio.recording.HopAudioTap
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.inputLatency
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

/**
 * The microphone of iOS (spec 6): AVAudioEngine on a session made for measuring — play and record, so that a backing can
 * sound in the headphones of a take (spec 3.32), wireless ones as output only — no automatic gain, no noise
 * suppression, the counterpart of the UNPROCESSED source on Android. The system hands over float blocks of its own
 * length on its audio thread; they are only copied there, and the rest — hops of 512, the digital-silence watchdog,
 * FrameAnalyzer and the detector — runs on the collector's thread, the same chain with the same numbers as on
 * Android. Each collection opens its own engine and session and closes them when cancelled.
 *
 * Fails with [MicUnavailableException] when the engine does not start, when the system takes the input away
 * (a call, Siri, an alarm: an interruption; a reset of the media services) and when the input gives exact zeros
 * for longer than the watchdog allows. The sound of a take is taken hop by hop, on the same sample clock as the frames.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosMicPitchSource(
    private val detectorFactory: PitchDetectorFactory,
    encoderFactory: PcmEncoderFactory,
    /** Debug builds: a line per second of what the detector saw (FrameStats), for tuning the thresholds. */
    private val logStats: Boolean,
) : PitchSource {

    override val requiresMicPermission: Boolean = true

    private val tap = HopAudioTap(encoderFactory, Dispatchers.IO)
    override val audioTap: AudioTap get() = tap

    /** The last word of the input on its time: a frame it had captured, and when on the host clock. */
    private class Anchor(val frame: Long, val nanos: Long, val rate: Int)

    @Volatile private var anchor: Anchor? = null

    override val clock: SampleClock = SampleClock { tMs ->
        anchor?.let { at -> at.nanos + ((tMs * at.rate / MS_PER_SECOND) - at.frame) * NANOS_PER_SECOND.toLong() / at.rate }
    }

    override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
        // The audio thread must never wait: a full queue means the analysis fell far behind, which is a failure,
        // not a reason to drop sound — the frame clock counts every sample.
        val blocks = Channel<FloatArray>(QUEUED_BLOCKS, BufferOverflow.SUSPEND)
        val session = AVAudioSession.sharedInstance()
        val engine = AVAudioEngine()
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(AVAudioSessionInterruptionNotification, AVAudioSessionMediaServicesWereResetNotification).map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue) { _ ->
                blocks.close(unavailable(MicUnavailableReason.READ_FAILED, "the system took the input away: $name"))
            }
        }
        try {
            openSession(session)
            val input = engine.inputNode
            val format = input.outputFormatForBus(0u)
            val sampleRateHz = format.sampleRate.toInt()
            if (sampleRateHz <= 0 || format.channelCount == 0u) {
                throw unavailable(MicUnavailableReason.OPEN_FAILED, "the input has no format ($sampleRateHz Hz, ${format.channelCount} channels)")
            }
            var delivered = 0L
            anchor = null
            input.installTapOnBus(0u, TAP_BUFFER_FRAMES, format) { buffer, time ->
                val data = buffer?.floatChannelData ?: return@installTapOnBus
                val count = buffer.frameLength.toInt()
                // the host time the block was captured at, against the frames delivered before it: the clock of a take (spec 5.25)
                if (time != null && time.hostTimeValid) {
                    anchor = Anchor(delivered, HostClock.nanosOf(time.hostTime) - (session.inputLatency * NANOS_PER_SECOND).toLong(), sampleRateHz)
                }
                delivered += count
                val channel = data[0] ?: return@installTapOnBus
                // the first channel is the microphone; a second one, where there is one, is the same sound
                val block = FloatArray(count) { channel[it] }
                val sent = blocks.trySend(block)
                if (sent.isFailure && !sent.isClosed) {
                    blocks.close(unavailable(MicUnavailableReason.READ_FAILED, "the analysis fell behind the input"))
                }
            }
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
            var samplesRead = 0L
            for (block in blocks) {
                splitter.push(block) { hop ->
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
            }
        } finally {
            tap.onStreamEnded()
            observers.forEach(center::removeObserver)
            engine.inputNode.removeTapOnBus(0u)
            engine.stop()
            blocks.close()
            // let music the player had on come back
            session.setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
        }
    }

    private fun openSession(session: AVAudioSession) = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val ok = session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionAllowBluetoothA2DP, error.ptr) &&
            session.setPreferredSampleRate(PREFERRED_RATE_HZ, error.ptr) &&
            session.setActive(true, error.ptr)
        if (!ok) throw unavailable(MicUnavailableReason.OPEN_FAILED, "the audio session would not open: ${error.value?.localizedDescription}")
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
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val QUEUED_BLOCKS = 64
        const val TAP_BUFFER_FRAMES = 4096u
        const val PREFERRED_RATE_HZ = 48_000.0
    }
}
