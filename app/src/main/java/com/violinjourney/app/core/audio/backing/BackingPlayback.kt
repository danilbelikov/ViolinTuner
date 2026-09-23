package com.violinjourney.app.core.audio.backing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Where the sound goes now, and when that changes (spec 3.32: no headphones — no take under the backing). */
interface AudioRoutes {
    fun current(): AudioRoute

    val changes: Flow<AudioRoute>
}

/** The kinds of outputs, as the platform names them, into the few that matter here. Pure. */
object AudioRouteRules {
    /** One output device: its platform type and the name it gives itself. */
    data class Device(val type: Int, val name: String?)

    /** The media go to the headphones plugged or paired last; failing that, wireless ones go first, the speaker last. */
    fun routeOf(devices: List<Device>): AudioRoute {
        val ranked = devices.mapNotNull { device -> outputOf(device.type)?.let { it to device } }
        val best = ranked.minByOrNull { (output, _) -> RANK.indexOf(output) } ?: return AudioRoute(BackingOutput.SPEAKER, null)
        return AudioRoute(best.first, best.second.name?.takeIf { best.first != BackingOutput.SPEAKER })
    }

    fun outputOf(type: Int): BackingOutput? = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, TYPE_BLE_HEADSET -> BackingOutput.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> BackingOutput.USB
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> BackingOutput.WIRED
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> BackingOutput.SPEAKER
        else -> null
    }

    private val RANK = listOf(BackingOutput.BLUETOOTH, BackingOutput.USB, BackingOutput.WIRED, BackingOutput.SPEAKER)

    /** `AudioDeviceInfo.TYPE_BLE_HEADSET`, API 31: the number, so the app still builds and runs below it. */
    private const val TYPE_BLE_HEADSET = 26
}

class AndroidAudioRoutes @Inject constructor(@ApplicationContext context: Context) : AudioRoutes {
    private val audio = context.getSystemService(AudioManager::class.java)

    override fun current(): AudioRoute = AudioRouteRules.routeOf(
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { AudioRouteRules.Device(it.type, it.productName?.toString()) },
    )

    override val changes: Flow<AudioRoute> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(current())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(current())
            }
        }
        trySend(current())
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()
}

/**
 * The backing played into the headphones while a take is recorded (spec 3.32). It reports the moment its first
 * frame left the output on `CLOCK_MONOTONIC` — what the shift of the take is measured from — and stops by itself,
 * rather than fall through to the speaker, when the headphones go.
 */
interface BackingPlayback {
    /** How far it has played, in ms; null while it does not play. */
    val position: StateFlow<Long?>

    /** Plays [pcm] — 16-bit stereo at [sampleRate], as [BackingPcmCache] makes it — from its start. Returns at once. */
    fun start(pcm: File, sampleRate: Int)

    /** When the first frame left the output; null until the output has said so. */
    val startNanos: Long?

    /** Stops; how far it had played, in ms. */
    fun stop(): Long
}

fun interface BackingPlaybackFactory {
    fun create(): BackingPlayback
}

class TrackBackingPlayback(private val routes: AudioRoutes) : BackingPlayback {
    private val mutablePosition = MutableStateFlow<Long?>(null)
    override val position: StateFlow<Long?> = mutablePosition.asStateFlow()

    @Volatile override var startNanos: Long? = null
        private set

    @Volatile private var worker: Worker? = null
    @Volatile private var playedMs = 0L

    override fun start(pcm: File, sampleRate: Int) {
        stop()
        startNanos = null
        playedMs = 0
        worker = Worker(pcm, sampleRate).also { it.start() }
    }

    override fun stop(): Long {
        worker?.let {
            it.stopped = true
            it.join(JOIN_TIMEOUT_MS)
        }
        worker = null
        mutablePosition.value = null
        return playedMs
    }

    private inner class Worker(private val pcm: File, private val rate: Int) : Thread("backing-playback") {
        @Volatile var stopped = false

        override fun run() {
            var track: AudioTrack? = null
            try {
                RandomAccessFile(pcm, "r").use { input ->
                    val newTrack = newTrack(rate)
                    track = newTrack
                    // the headphones went: the backing stops rather than fall through to the speaker and into the take
                    newTrack.addOnRoutingChangedListener({ _ ->
                        if (!routes.current().output.isHeadphones) stopped = true
                    }, Handler(Looper.getMainLooper()))
                    val buffer = ByteArray(CHUNK_FRAMES * BYTES_PER_FRAME)
                    val timestamp = AudioTimestamp()
                    var written = 0L
                    newTrack.play()
                    while (!stopped) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        var offset = 0
                        while (offset < read && !stopped) {
                            val n = newTrack.write(buffer, offset, read - offset)
                            if (n < 0) throw IllegalStateException("AudioTrack.write returned $n")
                            offset += n
                        }
                        written += read / BYTES_PER_FRAME
                        val head = newTrack.playbackHeadPosition.toLong() and UNSIGNED_INT
                        playedMs = head * MS_PER_SECOND / rate
                        mutablePosition.value = playedMs
                        if (startNanos == null && newTrack.getTimestamp(timestamp) && timestamp.framePosition > 0) {
                            startNanos = timestamp.nanoTime - timestamp.framePosition * NANOS_PER_SECOND / rate
                        }
                    }
                    // the end of the file: let what is in the track play out, the take goes on. A stream track
                    // holds back a tail shorter than its start threshold until it is told there is no more.
                    if (!stopped) newTrack.stop()
                    var lastHead = -1L
                    var stillSince = System.nanoTime()
                    while (!stopped) {
                        val head = newTrack.playbackHeadPosition.toLong() and UNSIGNED_INT
                        playedMs = head * MS_PER_SECOND / rate
                        mutablePosition.value = playedMs
                        if (head >= written) break
                        // an output that stopped moving will not finish: do not wait for it forever
                        if (head != lastHead) {
                            lastHead = head
                            stillSince = System.nanoTime()
                        } else if (System.nanoTime() - stillSince > STALL_NANOS) {
                            break
                        }
                        sleep(POLL_MS)
                    }
                    // played to its end: the bar stands full (spec 3.32)
                    if (!stopped) {
                        playedMs = written * MS_PER_SECOND / rate
                        mutablePosition.value = playedMs
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "cannot read the backing", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "the backing's output broke down", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "no output for the backing", e)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "no output for the backing", e)
            } finally {
                track?.let { runCatching { it.stop() }; it.release() }
            }
        }

        private fun newTrack(rate: Int): AudioTrack {
            val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            return AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                // the shortest the output allows: the less sits in the buffer, the less there is for the clocks to be wrong about
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minimum, CHUNK_FRAMES * BYTES_PER_FRAME) * 2)
                .build()
        }
    }

    private companion object {
        const val TAG = "BackingPlayback"
        const val CHUNK_FRAMES = 1_024
        const val BYTES_PER_FRAME = 4
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val UNSIGNED_INT = 0xFFFFFFFFL
        const val POLL_MS = 20L

        // longer than any output buffer: a head still for this long has stopped for good
        const val STALL_NANOS = 1_000_000_000L
        const val JOIN_TIMEOUT_MS = 1_000L
    }
}

/** The fake build (`-PfakePitch=true`, the emulator): pretend wireless headphones, so a take under the backing can be tried without any. */
class FakeHeadphoneRoutes : AudioRoutes {
    private val route = AudioRoute(BackingOutput.BLUETOOTH, "Emulator headphones")

    override fun current(): AudioRoute = route

    override val changes: Flow<AudioRoute> = kotlinx.coroutines.flow.flowOf(route)
}
