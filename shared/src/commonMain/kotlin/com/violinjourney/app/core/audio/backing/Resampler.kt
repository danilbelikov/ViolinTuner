package com.violinjourney.app.core.audio.backing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * One channel from [inRate] to [outRate] by a windowed sinc — the Lanczos kernel, as `LagSearch` uses to
 * look between the samples of the lag function, here with a cutoff lowered to the narrower of the two
 * rates so nothing folds back when going down (spec 5.25: a backing at 44.1 kHz under a take at 48, or
 * the other way). Streaming: feed chunks with [process], then [finish] once; pure Kotlin, one per channel.
 */
class Resampler internal constructor(
    private val inRate: Int,
    private val outRate: Int,
    private val halfWidth: Int,
    /** The most positions between input samples a weight table is made for (see [weights]); tests pass 0 to go without. */
    maxPhases: Int,
) {
    constructor(inRate: Int, outRate: Int, halfWidth: Int = HALF_WIDTH) : this(inRate, outRate, halfWidth, MAX_PHASES)

    private val step = inRate.toDouble() / outRate
    /** Going down, the band ends a little below the new Nyquist: the kernel's edge is not a wall, and what lies past it must not fold back. */
    private val cutoff = if (outRate < inRate) outRate.toDouble() / inRate * DOWN_MARGIN else 1.0
    /** Kernel half-width in input samples: widened when the cutoff is lowered, so the window still spans [halfWidth] zero crossings. */
    private val reach = (halfWidth / cutoff).toInt() + 1
    private val table = kernelTable()

    /** Input samples under the kernel of one output sample: from `base - reach + 1` to `base + reach`. */
    private val taps = 2 * reach

    /** Output samples fall between the input ones at this many positions, over and over: the rates' ratio in lowest terms. */
    private val phases = outRate / gcd(inRate, outRate)

    /**
     * The normalised weights of the [taps] of each of the [phases] positions, reckoned once. Reckoned anew for every sample
     * of every channel, the kernel held the unpack of a backing to about twice real time on the emulator. Null for rates
     * that share too little for a table (more than `maxPhases` positions): then the kernel is reckoned as it goes.
     */
    private val weights: DoubleArray? = if (inRate == outRate || phases > maxPhases) null else weightTable()

    /** Input kept for the kernel: samples from [bufferStart] on. */
    private var buffer = FloatArray(INITIAL_BUFFER)
    private var bufferStart = 0L
    private var bufferCount = 0
    /** Position of the next output sample, in input samples. */
    private var outIndex = 0L
    private var inputEnded = false

    val passThrough: Boolean get() = inRate == outRate

    fun process(input: FloatArray, count: Int, emit: (FloatArray, Int) -> Unit) {
        if (passThrough) {
            emit(input, count)
            return
        }
        append(input, count)
        drain(emit)
    }

    /** The last samples, which needed the input after them; zeros stand for what never came. */
    fun finish(emit: (FloatArray, Int) -> Unit) {
        if (passThrough) return
        inputEnded = true
        drain(emit)
    }

    private fun drain(emit: (FloatArray, Int) -> Unit) {
        val out = FloatArray(OUT_CHUNK)
        var written = 0
        val inputTotal = bufferStart + bufferCount
        // The last output sample lies at the last input sample: the length follows the rates exactly.
        val outTotal = if (inputEnded) floor((inputTotal - 1) / step).toLong() + 1 else Long.MAX_VALUE
        val weighed = weights
        while (outIndex < outTotal) {
            val base = baseOf(outIndex)
            if (!inputEnded && base + reach >= inputTotal) break
            out[written++] = if (weighed != null) weighedSampleAt(outIndex, base, weighed) else sampleAt(outIndex * step)
            outIndex++
            if (written == OUT_CHUNK) {
                emit(out, written)
                written = 0
            }
        }
        if (written > 0) emit(out, written)
        // what no future output sample can reach any more
        val keepFrom = baseOf(outIndex) - reach
        drop(keepFrom)
    }

    /**
     * The input sample at or before output sample [index]. With a table, in whole numbers: a position that falls right on
     * an input sample must find its own row of weights, which the rounding of `index * step` could miss.
     */
    private fun baseOf(index: Long): Long = if (weights != null) index * inRate / outRate else floor(index * step).toLong()

    /** [sampleAt] with the weights of the table: the same sum, without the kernel. */
    private fun weighedSampleAt(index: Long, base: Long, weights: DoubleArray): Float {
        val row = (index % phases).toInt() * taps
        // the buffer index of the first tap; taps outside the buffer count as silence, as in sampleAt
        val first = base - reach + 1 - bufferStart
        val from = maxOf(0L, -first).toInt()
        val until = minOf(taps.toLong(), bufferCount - first).toInt()
        var sum = 0.0
        for (t in from until until) sum += buffer[(first + t).toInt()] * weights[row + t]
        return sum.toFloat()
    }

    private fun weightTable(): DoubleArray {
        val out = DoubleArray(phases * taps)
        for (phase in 0 until phases) {
            // where between two input samples this position falls, exactly
            val fraction = (phase.toLong() * inRate % outRate).toDouble() / outRate
            val row = phase * taps
            var total = 0.0
            for (t in 0 until taps) {
                val weight = kernel((fraction - (t - reach + 1)) * cutoff)
                out[row + t] = weight
                total += weight
            }
            // normalised as in sampleAt: over every tap, in the buffer or not
            for (t in 0 until taps) out[row + t] = if (total == 0.0) 0.0 else out[row + t] / total
        }
        return out
    }

    private fun sampleAt(center: Double): Float {
        val base = floor(center).toLong()
        var sum = 0.0
        var weights = 0.0
        for (k in base - reach + 1..base + reach) {
            val weight = kernel((center - k) * cutoff)
            weights += weight
            val index = k - bufferStart
            if (index < 0 || index >= bufferCount) continue
            sum += buffer[index.toInt()] * weight
        }
        // normalised: the gain of a dc signal stays 1 whatever the fraction of the position
        return if (weights == 0.0) 0f else (sum / weights).toFloat()
    }

    private fun kernel(x: Double): Double {
        val ax = abs(x)
        if (ax >= halfWidth) return 0.0
        val position = ax * TABLE_STEPS
        val i = position.toInt()
        val fraction = position - i
        return table[i] + (table[i + 1] - table[i]) * fraction
    }

    private fun kernelTable(): DoubleArray = DoubleArray(halfWidth * TABLE_STEPS + 2) { i ->
        val x = i.toDouble() / TABLE_STEPS
        when {
            x == 0.0 -> 1.0
            x >= halfWidth -> 0.0
            else -> sinc(x) * sinc(x / halfWidth)
        }
    }

    private fun sinc(x: Double): Double = sin(PI * x) / (PI * x)

    private fun append(input: FloatArray, count: Int) {
        if (bufferCount + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, bufferCount + count))
        input.copyInto(buffer, bufferCount, 0, count)
        bufferCount += count
    }

    private fun drop(untilIndex: Long) {
        val n = (untilIndex - bufferStart).coerceIn(0, bufferCount.toLong()).toInt()
        if (n == 0) return
        buffer.copyInto(buffer, 0, n, bufferCount)
        bufferCount -= n
        bufferStart += n
    }

    private companion object {
        /** Sixteen zero crossings each side: flat to within a hair up to the edge of the band, no audible ringing. */
        const val HALF_WIDTH = 16
        const val TABLE_STEPS = 512
        const val DOWN_MARGIN = 0.95
        const val INITIAL_BUFFER = 8_192
        const val OUT_CHUNK = 4_096

        /** 44.1 and 48 kHz share 160 positions, 22.05 and 48 — 320, 11.025 and 48 — 640; a table at most ~0.3 MB. */
        const val MAX_PHASES = 1_024

        tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
