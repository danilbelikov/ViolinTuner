package com.violinjourney.app.core.recording.overlay

import com.violinjourney.app.core.domain.Zone
import kotlin.math.ceil
import kotlin.math.floor

/** One particle of the dust at a moment: its middle, side and opacity in pixels of the frame, in the colour of [zone]. */
data class DustParticle(val x: Float, val y: Float, val side: Float, val alpha: Float, val zone: Zone)

/**
 * The dust of the notes that crossed the playhead (spec 3.37, 5.30, since 0.90): every [NotesVideoConfig.dustSegmentU] of a
 * capsule gives [NotesVideoConfig.dustPerSegment] particles as it crosses, and they ride off left with the lane, drift, fall
 * and fade within [NotesVideoConfig.dustLifeMs]. Nothing is kept between frames: a frame is worked out from its own time and a
 * hash of the note, the piece and the particle — the same at any frame rate, on Android, on iOS and in a preview.
 */
object NotesOverlayDust {
    fun particlesAt(nowMs: Long, overlay: NotesOverlay, geometry: NotesOverlayGeometry): Sequence<DustParticle> = sequence {
        val config = overlay.config
        val u = geometry.u
        val life = config.dustLifeMs
        val msPerSegment = config.dustSegmentU / config.speedUPerSecond * MS_PER_SECOND
        var index = overlay.firstEndingAfter(nowMs - life)
        while (index < overlay.notes.size) {
            val note = overlay.notes[index]
            if (note.startMs > nowMs) break
            // the capsule is cut short by the gap: only what is drawn crumbles
            val lengthU = (note.endMs - note.startMs) / MS_PER_SECOND * config.speedUPerSecond - config.pillGapU
            val segments = ceil(lengthU / config.dustSegmentU).toInt()
            // the pieces that crossed within the last life: piece k crosses at start + k × msPerSegment
            val first = ceil((nowMs - life - note.startMs) / msPerSegment).toInt().coerceAtLeast(0)
            val last = minOf(segments - 1, floor((nowMs - note.startMs) / msPerSegment).toInt())
            val centerY = geometry.pillCenterY(note.midi, overlay.lowMidi, overlay.highMidi)
            for (k in first..last) {
                val ageMs = nowMs - (note.startMs + k * msPerSegment)
                if (ageMs < 0 || ageMs >= life) continue
                val age = ageMs / MS_PER_SECOND
                val lived = (ageMs / life).toFloat()
                repeat(config.dustPerSegment) { j ->
                    val startY = centerY - geometry.pillHeight / 2 + hash01(index, k, j, HEIGHT).toFloat() * geometry.pillHeight
                    val driftX = -config.dustDriftU * hash01(index, k, j, DRIFT_X)
                    val driftY = config.dustSpreadU * (2 * hash01(index, k, j, DRIFT_Y) - 1)
                    val side = config.dustMinSideU + (config.dustMaxSideU - config.dustMinSideU) * hash01(index, k, j, SIDE)
                    yield(
                        DustParticle(
                            x = geometry.headX + ((-config.speedUPerSecond + driftX) * age * u).toFloat(),
                            y = startY + ((driftY * age + config.dustFallU * age * age / 2) * u).toFloat(),
                            side = (side * (1 - (1 - config.dustEndShare) * lived) * u).toFloat(),
                            alpha = (1 - lived) * (1 - lived),
                            zone = note.zone,
                        ),
                    )
                }
            }
            index++
        }
    }

    /**
     * A 32-bit hash of the note [n], the piece [k], the particle [j] and which value [v] → [0, 1) (spec 5.30). Int arithmetic
     * wraps modulo 2³² alike on the JVM and on Native, and `ushr` shifts without the sign — the same numbers as `Math.imul` and
     * `>>>` in the mockup.
     */
    internal fun hash01(n: Int, k: Int, j: Int, v: Int): Double {
        var h = (n + 1) * GOLDEN xor (k + 1) * MIX_K xor (j + 1) * MIX_J xor (v + 1) * MIX_V
        h = h xor (h ushr SHIFT_A)
        h *= MUL_A
        h = h xor (h ushr SHIFT_B)
        h *= MUL_B
        h = h xor (h ushr SHIFT_A)
        return (h.toLong() and UNSIGNED) / TWO_TO_32
    }

    // which value of a particle a hash is for (spec 5.30)
    private const val HEIGHT = 0
    private const val DRIFT_X = 1
    private const val DRIFT_Y = 2
    private const val SIDE = 3

    private const val GOLDEN = 0x9E3779B1.toInt()
    private const val MIX_K = 0x85EBCA77.toInt()
    private const val MIX_J = 0xC2B2AE3D.toInt()
    private const val MIX_V = 0x27D4EB2F
    private const val MUL_A = 0x7FEB352D
    private const val MUL_B = 0x846CA68B.toInt()
    private const val SHIFT_A = 16
    private const val SHIFT_B = 15
    private const val UNSIGNED = 0xFFFFFFFFL
    private const val TWO_TO_32 = 4_294_967_296.0
    private const val MS_PER_SECOND = 1_000.0
}
