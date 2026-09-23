package com.example.violintuner.core.audio.backing

import android.content.Context
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.Calibration
import com.example.violintuner.core.domain.backing.CalibrationConfig
import com.example.violintuner.core.domain.backing.CalibrationResult
import com.example.violintuner.core.domain.backing.OnsetDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** How «Настроим наушники» stands (spec 3.32). */
sealed interface CalibrationProgress {
    /** [answered] — one per working click, true once a note answered it; [clicksDone] of them have sounded. */
    data class Listening(val answered: List<Boolean>, val clicksDone: Int) : CalibrationProgress

    data class Done(val result: CalibrationResult) : CalibrationProgress
}

/** Plays the clicks into the headphones and listens for the notes played to them; ends with a [CalibrationProgress.Done]. */
fun interface HeadphoneCalibrator {
    fun run(intonation: IntonationConfig): Flow<CalibrationProgress>
}

/** The clicks as the output plays them: short sine bursts, 16-bit stereo, with room after the last one for a late note. Pure. */
object ClickTrack {
    fun pcm(config: CalibrationConfig, sampleRate: Int, tailMs: Int = config.windowAfterMs + TAIL_EXTRA_MS): ByteArray {
        val count = config.leadInClicks + config.clicks
        val beat = config.beatMs * sampleRate / MS_PER_SECOND
        val frames = (beat * (count - 1) + (config.clickMs + tailMs).toLong() * sampleRate / MS_PER_SECOND).toInt()
        val amplitude = 10.0.pow(config.clickDbfs / DB_PER_DECADE) * Short.MAX_VALUE
        val click = (config.clickMs.toLong() * sampleRate / MS_PER_SECOND).toInt()
        val buffer = ByteBuffer.allocate(frames * BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val inBeat = (i % beat).toInt()
            val sounding = i / beat < count && inBeat < click
            // a raised-cosine envelope: a click without a click of its own at the edges
            val value = if (sounding) {
                val envelope = 0.5 - 0.5 * kotlin.math.cos(2 * PI * inBeat / click)
                (amplitude * envelope * sin(2 * PI * config.clickHz * inBeat / sampleRate)).roundToInt().toShort()
            } else {
                0
            }
            buffer.putShort(value)
            buffer.putShort(value)
        }
        return buffer.array()
    }

    private const val MS_PER_SECOND = 1_000L
    private const val DB_PER_DECADE = 20.0
    private const val BYTES_PER_FRAME = 4
    private const val TAIL_EXTRA_MS = 200
}

class DeviceHeadphoneCalibrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pitchSource: PitchSource,
    private val playbackFactory: BackingPlaybackFactory,
    private val config: BackingConfig,
) : HeadphoneCalibrator {
    override fun run(intonation: IntonationConfig): Flow<CalibrationProgress> = channelFlow {
        val calibration = config.calibration
        val file = File(context.cacheDir, CLICKS_FILE)
        file.writeBytes(ClickTrack.pcm(calibration, RATE))
        val playback = playbackFactory.create()
        val onsets = ArrayList<Long>()
        val detector = OnsetDetector(calibration)
        val clicksTotal = calibration.leadInClicks + calibration.clicks
        var done = false
        playback.start(file, RATE)
        send(CalibrationProgress.Listening(List(calibration.clicks) { false }, 0))
        val listening = launch {
            pitchSource.frames(intonation).collect { frame ->
                val nanos = pitchSource.clock?.nanosAt(frame.tMs) ?: System.nanoTime()
                val db = if (frame.rms > 0) DB_PER_DECADE * log10(frame.rms) else SILENCE_DB
                if (detector.add(nanos, db)) onsets += nanos
                val start = playback.startNanos ?: return@collect
                val clicks = Calibration.clickTimesNanos(start, calibration)
                val sounded = clicks.count { it <= System.nanoTime() }
                val last = clicks.last() + calibration.windowAfterMs * NANOS_PER_MS
                if (!done && System.nanoTime() > last) {
                    done = true
                    send(CalibrationProgress.Done(Calibration.measure(clicks, onsets, calibration)))
                    playback.stop()
                    channel.close()
                } else if (!done) {
                    send(CalibrationProgress.Listening(Calibration.answered(clicks, onsets, calibration), (sounded - calibration.leadInClicks).coerceIn(0, clicksTotal)))
                }
            }
        }
        awaitClose {
            listening.cancel()
            playback.stop()
            file.delete()
        }
    }

    private companion object {
        const val CLICKS_FILE = "calibration-clicks.pcm"
        const val RATE = 48_000
        const val DB_PER_DECADE = 20.0
        const val SILENCE_DB = -120.0
        const val NANOS_PER_MS = 1_000_000L
    }
}
