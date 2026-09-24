package com.violinjourney.app.core.domain.sound

/** A closed range of a parameter with the value it starts at. */
data class ParamRange(val min: Double, val max: Double, val default: Double) {
    fun clamp(value: Double): Double = if (value.isNaN()) default else value.coerceIn(min, max)
}

/** Every number of sound processing, with starting values from docs/spec.md 5.11. None of it is about intonation. */
data class SoundConfig(
    val lowCutHz: ParamRange = ParamRange(40.0, 300.0, 80.0),
    val lowHz: ParamRange = ParamRange(60.0, 500.0, 200.0),
    val bodyHz: ParamRange = ParamRange(200.0, 2_000.0, 800.0),
    val presenceHz: ParamRange = ParamRange(1_000.0, 8_000.0, 3_200.0),
    val airHz: ParamRange = ParamRange(3_000.0, 16_000.0, 8_000.0),
    val eqGainDb: ParamRange = ParamRange(-12.0, 12.0, 0.0),
    val bellQ: ParamRange = ParamRange(0.4, 4.0, 1.0),
    /** Shelves and the low cut are not given a width to set: Butterworth, no bump at the corner. */
    val fixedQ: Double = 0.7071,

    val thresholdDb: ParamRange = ParamRange(-40.0, 0.0, -18.0),
    val ratio: ParamRange = ParamRange(1.0, 10.0, 3.0),
    val attackMs: ParamRange = ParamRange(1.0, 100.0, 15.0),
    val releaseMs: ParamRange = ParamRange(20.0, 1_000.0, 200.0),
    val makeupDb: ParamRange = ParamRange(0.0, 18.0, 0.0),
    val kneeDb: Double = 6.0,

    val roomDecaySec: ParamRange = ParamRange(0.3, 1.2, 0.6),
    val hallDecaySec: ParamRange = ParamRange(0.8, 3.0, 1.8),
    val cathedralDecaySec: ParamRange = ParamRange(2.0, 6.0, 4.0),
    val preDelayMs: ParamRange = ParamRange(0.0, 100.0, 20.0),
    val roomPreDelayMs: Double = 5.0,
    val hallPreDelayMs: Double = 20.0,
    val cathedralPreDelayMs: Double = 40.0,
    val brightness: ParamRange = ParamRange(0.0, 1.0, 0.5),
    /** What [brightness] 0 and 1 stand for: where the tail loses its top on every turn. */
    val dullTailHz: Double = 2_000.0,
    val brightTailHz: Double = 12_000.0,
    val mix: ParamRange = ParamRange(0.0, 0.6, 0.2),

    val outputGainDb: ParamRange = ParamRange(-12.0, 12.0, 0.0),

    /** The limiter after everything: not a control, never off while anything else is on. */
    val limiterCeilingDb: Double = -1.0,
    val limiterLookAheadMs: Double = 5.0,
    val limiterReleaseMs: Double = 50.0,
    /** Holding back more than this counts as "the limiter is working". */
    val limitingFromDb: Double = 0.5,

    /** A parameter changed while the sound plays glides to its new value over this long: no clicks. */
    val smoothingMs: Double = 30.0,
    /** The file gets the tail of the hall after the last note, but never more than this. */
    val maxTailSec: Double = 6.0,
    val maxPresetNameLength: Int = 24,
) {
    fun decayRange(space: ReverbSpace): ParamRange = when (space) {
        ReverbSpace.ROOM -> roomDecaySec
        ReverbSpace.HALL -> hallDecaySec
        ReverbSpace.CATHEDRAL -> cathedralDecaySec
    }

    fun preDelayOf(space: ReverbSpace): Double = when (space) {
        ReverbSpace.ROOM -> roomPreDelayMs
        ReverbSpace.HALL -> hallPreDelayMs
        ReverbSpace.CATHEDRAL -> cathedralPreDelayMs
    }
}
