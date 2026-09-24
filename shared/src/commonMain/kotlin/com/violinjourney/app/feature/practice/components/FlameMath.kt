package com.violinjourney.app.feature.practice.components

import kotlin.math.PI
import kotlin.math.cos

/**
 * The rules of the streak flame (spec 3.18, 5.12; handoff 19g, `anims`) — pure, with tests: which
 * flame a streak earns, how its two layers stand at a moment of the sway, how the sway dies down,
 * how it flares when the streak grows. Only shapes move: no colour and no alpha is ever animated.
 */
internal object FlameMath {
    enum class Stage { NONE, SMALL, FULL, HOT }

    fun stageOf(streakDays: Int): Stage = when {
        streakDays < PracticeMotion.FLAME_FROM_DAYS -> Stage.NONE
        streakDays < PracticeMotion.FLAME_FULL_FROM_DAYS -> Stage.SMALL
        streakDays < PracticeMotion.FLAME_HOT_FROM_DAYS -> Stage.FULL
        else -> Stage.HOT
    }

    /** The tongue leans from its base and breathes in height; the core breathes against it. */
    data class Pose(val tongueDegrees: Float, val tongueScaleY: Float, val coreScaleY: Float) {
        companion object {
            val REST = Pose(0f, 1f, 1f)
        }
    }

    // One cycle, as the handoff draws it: rest → left and taller → right and shorter → rest.
    private val keyTimes = floatArrayOf(0f, 0.3f, 0.6f, 1f)
    private val tongueDegrees = floatArrayOf(0f, -3f, 2.5f, 0f)
    private val tongueScaleY = floatArrayOf(1f, 1.05f, 0.97f, 1f)
    private val coreScaleY = floatArrayOf(1f, 0.94f, 1.06f, 1f)

    /** [phase] 0…1 of the cycle; [amplitude] 0…1 — how much of the sway is left (see [amplitudeAt]). */
    fun poseAt(phase: Float, amplitude: Float): Pose {
        val p = phase.coerceIn(0f, 1f)
        val a = amplitude.coerceIn(0f, 1f)
        if (a == 0f) return Pose.REST
        return Pose(
            tongueDegrees = track(tongueDegrees, p) * a,
            tongueScaleY = 1f + (track(tongueScaleY, p) - 1f) * a,
            coreScaleY = 1f + (track(coreScaleY, p) - 1f) * a,
        )
    }

    private fun track(values: FloatArray, phase: Float): Float {
        val next = (1 until keyTimes.size).first { phase <= keyTimes[it] }
        val from = keyTimes[next - 1]
        val local = (phase - from) / (keyTimes[next] - from)
        return values[next - 1] + (values[next] - values[next - 1]) * easeInOutSine(local)
    }

    fun phaseAt(swayMs: Long): Float = (swayMs % PracticeMotion.FLAME_CYCLE_MS) / PracticeMotion.FLAME_CYCLE_MS.toFloat()

    /** Full for [PracticeMotion.FLAME_ALIVE_MS], then down to stillness over the settle time: it comes to rest, it is not frozen mid-lean. */
    fun amplitudeAt(swayMs: Long): Float {
        val settling = swayMs - PracticeMotion.FLAME_ALIVE_MS
        return when {
            settling <= 0 -> 1f
            settling >= PracticeMotion.FLAME_SETTLE_MS -> 0f
            else -> 1f - easeInOutSine(settling / PracticeMotion.FLAME_SETTLE_MS.toFloat())
        }
    }

    val swayTotalMs: Long get() = PracticeMotion.FLAME_ALIVE_MS + PracticeMotion.FLAME_SETTLE_MS

    /** The flare of a streak that grew before the eyes: up to [PracticeMotion.FLAME_FLARE_SCALE] and back, from the base. */
    fun flareScaleAt(flareMs: Long): Float {
        val peak = PracticeMotion.FLAME_FLARE_SCALE
        val up = PracticeMotion.FLAME_FLARE_UP_MS
        val total = PracticeMotion.FLAME_FLARE_MS
        return when {
            flareMs <= 0 || flareMs >= total -> 1f
            flareMs < up -> 1f + (peak - 1f) * easeOut(flareMs / up.toFloat())
            else -> peak - (peak - 1f) * easeInOutSine((flareMs - up) / (total - up).toFloat())
        }
    }

    /** A flame that was not there a moment ago (the streak has just reached three) shows up first. */
    fun appearAlphaAt(appearMs: Long): Float = (appearMs / PracticeMotion.FLAME_APPEAR_MS.toFloat()).coerceIn(0f, 1f)

    private fun easeInOutSine(x: Float): Float = ((1 - cos(PI * x)) / 2).toFloat()

    private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)
}
