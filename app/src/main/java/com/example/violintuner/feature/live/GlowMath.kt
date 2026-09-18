package com.example.violintuner.feature.live

import kotlin.math.exp

/**
 * Geometry and strengths of the glowing ring (spec 3.14; handoff `Live2.dc.html`, `layers` and
 * its `ring(...)` function). Everything is a function of two numbers: `glow` 0..1 — how much
 * the ring shines — and `level` 0..1 — how loud the violin is. Lengths are in shares of R, the
 * radius of the outline, so the ring looks the same at any size. Pure: no Compose, no colors.
 */
object GlowMath {
    /** One stop of a radial gradient: where (share of the gradient radius) and how opaque. */
    data class AlphaStop(val position: Float, val alpha: Float)

    // Outline: towards white and more opaque as the glow grows, so that "held" reads as
    // "brighter" rather than as another color.
    private const val OUTLINE_WHITE_SHARE = 0.45f
    private const val OUTLINE_ALPHA_BASE = 0.6f
    private const val OUTLINE_ALPHA_GAIN = 0.4f

    /** Below this the outline is on its way to the idle ring, which is fully opaque. */
    private const val OUTLINE_IDLE_BLEND_UNTIL = 0.25f

    // Soft stroke under the outline.
    private const val SOFT_STROKE_GAIN_DP = 10f
    private const val SOFT_STROKE_ALPHA = 0.25f

    // Outer halo: from the outline out to 1.4 R, breathing ±4 % with the loudness.
    const val HALO_RADIUS = 1.4f
    const val BREATH_SHARE = 0.04f
    private const val HALO_EDGE_ALPHA = 0.32f
    private const val HALO_MID_OFFSET = 0.12f
    private const val HALO_MID_ALPHA = 0.10f

    /**
     * The handoff frames show the halo's edge strength filling the inside of the ring as well —
     * a soft lit disc under the note — while its notes call the inside transparent. The frames
     * are what was approved; zero here gives the other reading.
     */
    private const val DISC_ALPHA = HALO_EDGE_ALPHA

    // Inner halo: a rim of light on the inside of the outline.
    private const val INNER_START = 0.6f
    private const val INNER_MID = 0.88f
    private const val INNER_MID_ALPHA = 0.07f
    private const val INNER_EDGE_ALPHA = 0.18f

    // Wave: a thin circle leaving the ring.
    const val WAVE_REACH = 1.35f
    const val WAVE_ALPHA_NEW_NOTE = 0.25f
    const val WAVE_ALPHA_REWARD = 0.35f

    /** The farthest anything is drawn from the centre, in R: the layout keeps this clear of the screen edge. */
    const val EXTENT = HALO_RADIUS * (1f + BREATH_SHARE)

    /** Share of white mixed into the zone color of the outline. */
    fun outlineWhiteShare(glow: Float): Float = OUTLINE_WHITE_SHARE * glow.coerceIn(0f, 1f)

    /** Opaque when idle, `.6 + .4·glow` while a note sounds, and no jump between the two. */
    fun outlineAlpha(glow: Float): Float {
        val g = glow.coerceIn(0f, 1f)
        val sounding = OUTLINE_ALPHA_BASE + OUTLINE_ALPHA_GAIN * g
        if (g >= OUTLINE_IDLE_BLEND_UNTIL) return sounding
        val share = g / OUTLINE_IDLE_BLEND_UNTIL
        return 1f + (OUTLINE_ALPHA_BASE + OUTLINE_ALPHA_GAIN * OUTLINE_IDLE_BLEND_UNTIL - 1f) * share
    }

    /** Width of the soft stroke in dp, given the outline's own width. */
    fun softStrokeWidthDp(outlineDp: Float, glow: Float): Float = outlineDp + SOFT_STROKE_GAIN_DP * glow.coerceIn(0f, 1f)

    fun softStrokeAlpha(glow: Float): Float = SOFT_STROKE_ALPHA * glow.coerceIn(0f, 1f)

    /** Radius of the outer halo in R: the only thing the loudness moves. */
    fun haloRadius(level: Float): Float = HALO_RADIUS * (1f + BREATH_SHARE * level.coerceIn(0f, 1f))

    /** Stops of the outer halo over [haloRadius]: the lit disc inside, the edge at the outline, then fading out. */
    fun haloStops(glow: Float, level: Float): List<AlphaStop> {
        val g = glow.coerceIn(0f, 1f)
        val outline = 1f / haloRadius(level)
        return listOf(
            AlphaStop(0f, DISC_ALPHA * g),
            AlphaStop(outline, HALO_EDGE_ALPHA * g),
            AlphaStop((outline + HALO_MID_OFFSET).coerceAtMost(1f), HALO_MID_ALPHA * g),
            AlphaStop(1f, 0f),
        )
    }

    /** Stops of the inner halo over R. */
    fun innerStops(glow: Float): List<AlphaStop> {
        val g = glow.coerceIn(0f, 1f)
        return listOf(
            AlphaStop(0f, 0f),
            AlphaStop(INNER_START, 0f),
            AlphaStop(INNER_MID, INNER_MID_ALPHA * g),
            AlphaStop(1f, INNER_EDGE_ALPHA * g),
        )
    }

    /** Radius of a wave in R at [progress] 0..1 of its life (already eased by the caller). */
    fun waveRadius(progress: Float): Float = 1f + (WAVE_REACH - 1f) * progress.coerceIn(0f, 1f)

    fun waveAlpha(startAlpha: Float, progress: Float): Float = startAlpha * (1f - progress.coerceIn(0f, 1f))

    /**
     * One frame of the glow following its target. The target itself moves all the time (it
     * grows with the hold, frame by frame), so a tween restarted on every change would never
     * get going; an exponential approach has no start to restart. Up is faster than down: a
     * short slip out of the zone must not put the ring out (spec 3.14, handoff 12h2).
     */
    fun follow(current: Float, target: Float, elapsedMs: Float, riseMs: Int, fallMs: Int): Float {
        if (elapsedMs <= 0f) return current
        val settleMs = if (target > current) riseMs else fallMs
        // A tween is "done" at its duration; an exponential is within 5 % after three time constants.
        val timeConstantMs = settleMs / SETTLE_TIME_CONSTANTS
        return current + (target - current) * (1f - exp(-elapsedMs / timeConstantMs))
    }

    private const val SETTLE_TIME_CONSTANTS = 3f
}

/** Waves are rare on purpose: a fast passage must not make the screen pulse (spec 3.14). */
class WaveGate(private val minIntervalMs: Long) {
    private var lastStartMs: Long? = null

    /** True when a wave may start at [nowMs]; remembers that it did. */
    fun tryStart(nowMs: Long): Boolean {
        val last = lastStartMs
        if (last != null && nowMs - last < minIntervalMs) return false
        lastStartMs = nowMs
        return true
    }
}
