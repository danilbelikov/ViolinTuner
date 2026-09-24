package com.violinjourney.app.core.domain.sound

/** «Гул»: a second-order high-pass that takes the rumble of the room away. */
data class LowCut(val enabled: Boolean, val hz: Double)

/** «Низ» and «Воздух»: a shelf lifts or lowers everything beyond its frequency. */
data class Shelf(val hz: Double, val gainDb: Double)

/** «Тело» and «Резкость»: a bell around a frequency; [q] is how narrow it is. */
data class Bell(val hz: Double, val gainDb: Double, val q: Double)

/** The five bands, in the order the signal meets them (spec 3.17). */
enum class EqBand { LOW_CUT, LOW, BODY, PRESENCE, AIR }

data class EqSettings(
    val enabled: Boolean,
    val lowCut: LowCut,
    val low: Shelf,
    val body: Bell,
    val presence: Bell,
    val air: Shelf,
)

/**
 * The sound is always made from the five numbers. [amount] only remembers that they were set by
 * the one knob «Сколько» (0…1) and where it stood; null — they were set by hand, the knob says
 * «своё».
 */
data class CompressorSettings(
    val enabled: Boolean,
    val thresholdDb: Double,
    val ratio: Double,
    val attackMs: Double,
    val releaseMs: Double,
    val makeupDb: Double,
    val amount: Double?,
)

/** The room the reverb is built on; it bounds how long the tail may be (spec 5.11). */
enum class ReverbSpace { ROOM, HALL, CATHEDRAL }

data class ReverbSettings(
    val enabled: Boolean,
    val space: ReverbSpace,
    /** RT60: how long the tail takes to fall silent. */
    val decaySec: Double,
    val preDelayMs: Double,
    /** 0 — dull, 1 — bright: where the tail is cut off on its way round. */
    val brightness: Double,
    /** Share of the processed signal, 0…0.6. */
    val mix: Double,
)

data class OutputSettings(val enabled: Boolean, val gainDb: Double)

/**
 * How a recording is made to sound (spec 3.17): equalizer → compressor → hall → volume, each with
 * a switch of its own. Settings only — the recorded sound itself is never changed.
 */
data class SoundSettings(
    val eq: EqSettings,
    val compressor: CompressorSettings,
    val reverb: ReverbSettings,
    val output: OutputSettings,
)
