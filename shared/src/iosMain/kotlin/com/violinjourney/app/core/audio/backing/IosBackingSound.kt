package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.io.PlatformFile
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionPortHeadphones
import platform.AVFAudio.AVAudioSessionPortUSBAudio
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioTime
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputLatency
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.NSObject

/** The host clock of iOS in nanoseconds: what the start of a take and of its backing are both measured on (spec 5.25). */
internal object HostClock {
    fun nanosOf(hostTime: ULong): Long = (AVAudioTime.secondsForHostTime(hostTime) * NANOS_PER_SECOND).toLong()

    fun hostTimeOf(nanos: Long): ULong = AVAudioTime.hostTimeForSeconds(nanos / NANOS_PER_SECOND)

    /** `CACurrentMediaTime` is the host clock in seconds. */
    fun nowNanos(): Long = (CACurrentMediaTime() * NANOS_PER_SECOND).toLong()

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}

/** Where the sound goes on iOS, from the route of the audio session: the headphones of every kind, or the phone itself. */
internal class IosAudioRoutes : AudioRoutes {
    override fun current(): AudioRoute {
        val outputs = AVAudioSession.sharedInstance().currentRoute.outputs.filterIsInstance<AVAudioSessionPortDescription>()
        val best = outputs.mapNotNull { port -> outputOf(port.portType)?.let { it to port } }.minByOrNull { (output, _) -> RANK.indexOf(output) }
            ?: return AudioRoute(BackingOutput.SPEAKER, null)
        return AudioRoute(best.first, best.second.portName.takeIf { best.first != BackingOutput.SPEAKER })
    }

    override val changes: Flow<AudioRoute> = callbackFlow {
        trySend(current())
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { _ ->
            trySend(current())
        }
        awaitClose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }.distinctUntilChanged()

    private fun outputOf(type: String?): BackingOutput? = when (type) {
        AVAudioSessionPortBluetoothA2DP, AVAudioSessionPortBluetoothHFP, AVAudioSessionPortBluetoothLE -> BackingOutput.BLUETOOTH
        AVAudioSessionPortUSBAudio -> BackingOutput.USB
        AVAudioSessionPortHeadphones -> BackingOutput.WIRED
        else -> null
    }

    private companion object {
        val RANK = listOf(BackingOutput.BLUETOOTH, BackingOutput.USB, BackingOutput.WIRED, BackingOutput.SPEAKER)
    }
}

/** The backing listened to on the piece screen (spec 3.32): the file as it is, through whatever output there is. */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingPreview : BackingPreview {
    private val mutablePlaying = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = mutablePlaying.asStateFlow()
    private var player: AVAudioPlayer? = null
    private val ended = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = stop()
    }

    override fun toggle(file: PlatformFile) {
        if (player != null) {
            stop()
            return
        }
        val next = runCatching { AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(file.path), error = null) }.getOrNull() ?: return
        next.delegate = ended
        if (next.play()) {
            player = next
            mutablePlaying.value = true
        }
    }

    override fun stop() {
        player?.stop()
        player = null
        mutablePlaying.value = false
    }
}

/**
 * The backing played into the headphones while a take is recorded (spec 3.32), as `TrackBackingPlayback` does on
 * Android: the prepared PCM, a few chunks ahead on an AVAudioPlayerNode of its own engine, started at a host time
 * that is known — the moment its first frame leaves the output is that time plus the output's latency. The
 * headphones going stop it rather than let it fall through to the speaker.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBackingPlayback(private val routes: AudioRoutes) : BackingPlayback {
    private val mutablePosition = MutableStateFlow<Long?>(null)
    override val position: StateFlow<Long?> = mutablePosition.asStateFlow()

    override var startNanos: Long? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var engine: AVAudioEngine? = null
    private var node: AVAudioPlayerNode? = null
    private val playedFrames = AtomicLong(0)
    private var rate = 0

    override fun start(pcm: PlatformFile, sampleRate: Int) {
        stop()
        rate = sampleRate
        val reader = runCatching { IosBackingPcmReader(pcm) }.getOrNull() ?: return
        val stereo = AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = 2u)
        val engine = AVAudioEngine().also { this.engine = it }
        val node = AVAudioPlayerNode().also { this.node = it }
        engine.attachNode(node)
        engine.connect(node, engine.mainMixerNode, stereo)
        if (!engine.startAndReturnError(null)) {
            reader.close()
            return
        }
        playedFrames.value = 0
        val wake = Channel<Unit>(Channel.CONFLATED)
        val queued = AtomicInt(0)
        var position = 0L
        // the first chunks go in before the start, so the start is what was asked for
        fun scheduleNext(): Boolean {
            if (position >= reader.frames) return false
            val count = minOf(CHUNK.toLong(), reader.frames - position).toInt()
            val buffer = AVAudioPCMBuffer(pCMFormat = stereo, frameCapacity = count.toUInt())
            buffer.frameLength = count.toUInt()
            val channels = buffer.floatChannelData ?: return false
            val left = FloatArray(count)
            val right = FloatArray(count)
            reader.read(position, count, 1f, left, right)
            val l = channels[0] ?: return false
            val r = channels[1] ?: return false
            for (i in 0 until count) {
                l[i] = left[i]
                r[i] = right[i]
            }
            position += count
            queued.incrementAndGet()
            node.scheduleBuffer(buffer) {
                queued.decrementAndGet()
                playedFrames.addAndGet(count.toLong())
                mutablePosition.value = playedFrames.value * MS_PER_SECOND / rate
                wake.trySend(Unit)
            }
            return true
        }
        repeat(AHEAD) { scheduleNext() }
        val at = HostClock.nowNanos() + START_DELAY_NANOS
        node.playAtTime(AVAudioTime(hostTime = HostClock.hostTimeOf(at)))
        startNanos = at + (AVAudioSession.sharedInstance().outputLatency * NANOS_PER_SECOND).toLong()
        mutablePosition.value = 0
        job = scope.launch {
            launch {
                // off the headphones: the backing stops, the take goes on (spec 3.32)
                routes.changes.collect { route -> if (!route.output.isHeadphones) node.stop() }
            }
            try {
                while (isActive) {
                    wake.receive()
                    while (queued.value < AHEAD && scheduleNext()) Unit
                    if (queued.value == 0 && position >= reader.frames) break
                }
            } finally {
                reader.close()
            }
        }
    }

    override fun stop(): Long {
        job?.cancel()
        job = null
        node?.stop()
        engine?.stop()
        node = null
        engine = null
        val played = if (rate > 0) playedFrames.value * MS_PER_SECOND / rate else 0
        mutablePosition.value = null
        startNanos = null
        return played
    }

    private companion object {
        const val CHUNK = 4_096
        const val AHEAD = 4
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Long enough for the first chunks to be in the node before the start; short enough not to be heard as a wait. */
        const val START_DELAY_NANOS = 50_000_000L
    }
}
