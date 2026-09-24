package com.violinjourney.app.feature.onboarding.art

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * How a layer of a scene lives (handoff series 36, `anims`). Time is in seconds since its page came into
 * view: what moves once — the sun, the walker, the bars, the copy — moves once per visit. `still` is the
 * system's «убрать анимации»: every layer stands where its motion ends, the phone on the stand is green.
 */
internal sealed interface ArtMotion {
    fun alpha(t: Float, still: Boolean): Float = 1f

    fun shift(t: Float, still: Boolean): ArtShift = ArtShift.None

    /** Stars: 1 → 0.3 → 1 over [period], starting after [delay]. */
    data class Twinkle(val period: Float, val delay: Float) : ArtMotion {
        override fun alpha(t: Float, still: Boolean): Float {
            if (still || t < delay) return 1f
            val phase = ((t - delay) / period) % 1f
            return 1f - TWINKLE_DEPTH * (1f - cos(2f * PI.toFloat() * phase)) / 2f
        }
    }

    /** The sun and its glow go down by [dy] over [duration], once. */
    data class Sink(val duration: Float, val dy: Float) : ArtMotion {
        override fun shift(t: Float, still: Boolean) = ArtShift(0f, dy * progress(t, 0f, duration, still, EaseInOut))
    }

    /** The bars on the road come one by one. */
    data class Appear(val delay: Float) : ArtMotion {
        override fun alpha(t: Float, still: Boolean) = progress(t, delay, APPEAR_S, still, Linear)
    }

    /** The walker with the case takes a few steps up the road. */
    data object Walk : ArtMotion {
        override fun shift(t: Float, still: Boolean): ArtShift {
            val p = progress(t, 0f, WALK_S, still, EaseInOut)
            return ArtShift(WALK_DX * p, WALK_DY * p)
        }
    }

    /** The copy flies from the phone into the folder along an arc, once; before it sets off it is not there. */
    data object Copy : ArtMotion {
        override fun alpha(t: Float, still: Boolean) = if (still || t >= COPY_DELAY_S) 1f else 0f

        override fun shift(t: Float, still: Boolean): ArtShift {
            val p = progress(t, COPY_DELAY_S, COPY_S, still, Emphasized)
            // a quadratic arc through the handoff's middle point (−50, −130) at its half
            val u = 1f - p
            val cx = 2f * COPY_MID_X - COPY_FROM_X / 2f
            val cy = 2f * COPY_MID_Y - COPY_FROM_Y / 2f
            return ArtShift(u * u * COPY_FROM_X + 2f * u * p * cx, u * u * COPY_FROM_Y + 2f * u * p * cy)
        }
    }

    /** The mark on the phone's ring: seen while the demo shows its zone. */
    data class Mark(val kind: LiveDemo.Shown) : ArtMotion {
        override fun alpha(t: Float, still: Boolean) = LiveDemo.weight(kind, t, still)
    }

    companion object {
        /** `twinkle:3.2:0`, `sink:20:14`, `appear:.4`, `walk`, `copy`, `mark:dot|up|down`. */
        fun parse(spec: String?): ArtMotion? {
            if (spec == null) return null
            val parts = spec.split(':')
            return when (parts[0]) {
                "twinkle" -> Twinkle(parts[1].toFloat(), parts[2].toFloat())
                "sink" -> Sink(parts[1].toFloat(), parts[2].toFloat())
                "appear" -> Appear(parts[1].toFloat())
                "walk" -> Walk
                "copy" -> Copy
                "mark" -> Mark(
                    when (parts[1]) {
                        "dot" -> LiveDemo.Shown.IN_TUNE
                        "up" -> LiveDemo.Shown.SHARP
                        "down" -> LiveDemo.Shown.FLAT
                        else -> error("unknown mark «${parts[1]}»")
                    },
                )
                else -> error("unknown motion «$spec»")
            }
        }

        const val TWINKLE_DEPTH = 0.7f
        const val APPEAR_S = 0.8f
        const val WALK_S = 2.4f
        const val WALK_DX = 22f
        const val WALK_DY = -11f
        const val COPY_DELAY_S = 0.4f
        const val COPY_S = 1.6f
        const val COPY_FROM_X = -120f
        const val COPY_FROM_Y = -100f
        const val COPY_MID_X = -50f
        const val COPY_MID_Y = -130f

        private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        private val Linear = CubicBezierEasing(0f, 0f, 1f, 1f)

        private fun progress(t: Float, delay: Float, duration: Float, still: Boolean, easing: CubicBezierEasing): Float =
            if (still) 1f else easing.transform(((t - delay) / duration).coerceIn(0f, 1f))
    }
}

internal data class ArtShift(val dx: Float, val dy: Float) {
    companion object {
        val None = ArtShift(0f, 0f)
    }
}

/**
 * The phone on the stand (36b) goes round «в строе → выше → в строе → ниже», [PHASE_S] a colour, the
 * colour crossfading over the last [CROSSFADE_S] of each. Sharp is the arrow up and flat the arrow down,
 * as on Live (spec 3.1) — the handoff had them the other way round.
 */
internal object LiveDemo {
    enum class Shown { IN_TUNE, SHARP, FLAT }

    const val PHASE_S = 4f
    const val CROSSFADE_S = 0.4f
    val cycle = listOf(Shown.IN_TUNE, Shown.SHARP, Shown.IN_TUNE, Shown.FLAT)

    /** What is shown at [t]: going [from] one zone [to] the next, [fraction] of the way. */
    data class Mix(val from: Shown, val to: Shown, val fraction: Float)

    fun at(t: Float, still: Boolean): Mix {
        if (still) return Mix(Shown.IN_TUNE, Shown.IN_TUNE, 0f)
        val phases = floor(t / PHASE_S).toInt()
        val within = t - phases * PHASE_S
        val from = cycle[phases.mod(cycle.size)]
        val to = cycle[(phases + 1).mod(cycle.size)]
        val fraction = ((within - (PHASE_S - CROSSFADE_S)) / CROSSFADE_S).coerceIn(0f, 1f)
        return Mix(from, to, fraction)
    }

    fun weight(kind: Shown, t: Float, still: Boolean): Float {
        val mix = at(t, still)
        return (if (mix.from == kind) 1f - mix.fraction else 0f) + (if (mix.to == kind) mix.fraction else 0f)
    }
}
