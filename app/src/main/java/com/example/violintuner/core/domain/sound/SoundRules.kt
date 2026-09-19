package com.example.violintuner.core.domain.sound

import kotlin.math.abs

/**
 * The one knob of the compressor (spec 5.11): «Сколько» k = 0…1 leads all five parameters at
 * once. Marks on its track: a little, noticeably, a lot.
 */
object CompressorAmount {
    const val A_LITTLE = 0.25
    const val NOTICEABLY = 0.5
    const val A_LOT = 0.85

    fun settingsOf(amount: Double, enabled: Boolean = true): CompressorSettings {
        val k = amount.coerceIn(0.0, 1.0)
        return CompressorSettings(
            enabled = enabled,
            thresholdDb = -6.0 - 24.0 * k,
            ratio = 1.0 + 5.0 * k,
            attackMs = 20.0 - 12.0 * k,
            releaseMs = 250.0 - 100.0 * k,
            makeupDb = 6.0 * k,
            amount = k,
        )
    }
}

/** The only place where the rules of the settings live: ranges, what follows what, what counts as "does nothing". */
object SoundRules {
    /** Everything at its default and switched off: the sound as recorded. */
    fun off(config: SoundConfig): SoundSettings = SoundSettings(
        eq = EqSettings(
            enabled = false,
            lowCut = LowCut(enabled = false, hz = config.lowCutHz.default),
            low = Shelf(config.lowHz.default, config.eqGainDb.default),
            body = Bell(config.bodyHz.default, config.eqGainDb.default, config.bellQ.default),
            presence = Bell(config.presenceHz.default, config.eqGainDb.default, config.bellQ.default),
            air = Shelf(config.airHz.default, config.eqGainDb.default),
        ),
        compressor = CompressorAmount.settingsOf(CompressorAmount.NOTICEABLY, enabled = false).copy(
            thresholdDb = config.thresholdDb.default,
            ratio = config.ratio.default,
            attackMs = config.attackMs.default,
            releaseMs = config.releaseMs.default,
            makeupDb = config.makeupDb.default,
            amount = null,
        ),
        reverb = ReverbSettings(
            enabled = false,
            space = ReverbSpace.HALL,
            decaySec = config.hallDecaySec.default,
            preDelayMs = config.hallPreDelayMs,
            brightness = config.brightness.default,
            mix = config.mix.default,
        ),
        output = OutputSettings(enabled = false, gainDb = config.outputGainDb.default),
    )

    /** Brings every number into its range; the tail — into the range of its space. What is read from disk goes through here. */
    fun clean(settings: SoundSettings, config: SoundConfig): SoundSettings = with(settings) {
        SoundSettings(
            eq = eq.copy(
                lowCut = eq.lowCut.copy(hz = config.lowCutHz.clamp(eq.lowCut.hz)),
                low = Shelf(config.lowHz.clamp(eq.low.hz), config.eqGainDb.clamp(eq.low.gainDb)),
                body = Bell(config.bodyHz.clamp(eq.body.hz), config.eqGainDb.clamp(eq.body.gainDb), config.bellQ.clamp(eq.body.q)),
                presence = Bell(config.presenceHz.clamp(eq.presence.hz), config.eqGainDb.clamp(eq.presence.gainDb), config.bellQ.clamp(eq.presence.q)),
                air = Shelf(config.airHz.clamp(eq.air.hz), config.eqGainDb.clamp(eq.air.gainDb)),
            ),
            compressor = compressor.copy(
                thresholdDb = config.thresholdDb.clamp(compressor.thresholdDb),
                ratio = config.ratio.clamp(compressor.ratio),
                attackMs = config.attackMs.clamp(compressor.attackMs),
                releaseMs = config.releaseMs.clamp(compressor.releaseMs),
                makeupDb = config.makeupDb.clamp(compressor.makeupDb),
                amount = compressor.amount?.coerceIn(0.0, 1.0),
            ),
            reverb = reverb.copy(
                decaySec = config.decayRange(reverb.space).clamp(reverb.decaySec),
                preDelayMs = config.preDelayMs.clamp(reverb.preDelayMs),
                brightness = config.brightness.clamp(reverb.brightness),
                mix = config.mix.clamp(reverb.mix),
            ),
            output = output.copy(gainDb = config.outputGainDb.clamp(output.gainDb)),
        )
    }

    /** Another space: the tail and the pre-delay become those of the new one — a cathedral's six seconds are no room. */
    fun withSpace(reverb: ReverbSettings, space: ReverbSpace, config: SoundConfig): ReverbSettings =
        if (space == reverb.space) {
            reverb
        } else {
            reverb.copy(space = space, decaySec = config.decayRange(space).default, preDelayMs = config.preDelayOf(space))
        }

    /** The knob «Сколько» was turned: the five follow it. The switch of the block is left as it was. */
    fun withAmount(compressor: CompressorSettings, amount: Double): CompressorSettings =
        CompressorAmount.settingsOf(amount, compressor.enabled)

    /** One of the five was set by hand: the knob says «своё». */
    fun byHand(compressor: CompressorSettings): CompressorSettings = compressor.copy(amount = null)

    /**
     * True when the chain would leave the sound as it is: then it is not run at all, and the
     * recording plays — and is shared — bit for bit (spec 5.11). A block that is on but set to
     * nothing counts as off; a compressor that is on always counts, even at 1:1 — it still has
     * a make-up gain and a say in the meters.
     */
    fun isNeutral(settings: SoundSettings): Boolean = with(settings) {
        val eqIdle = !eq.enabled || (!eq.lowCut.enabled && listOf(eq.low.gainDb, eq.body.gainDb, eq.presence.gainDb, eq.air.gainDb).all { abs(it) < EPSILON })
        val reverbIdle = !reverb.enabled || reverb.mix < EPSILON
        val outputIdle = !output.enabled || abs(output.gainDb) < EPSILON
        eqIdle && !compressor.enabled && reverbIdle && outputIdle
    }

    /** How much longer than the recording its file is: the hall rings on after the last note. */
    fun tailSec(settings: SoundSettings, config: SoundConfig): Double =
        if (settings.reverb.enabled && settings.reverb.mix >= EPSILON) settings.reverb.decaySec.coerceAtMost(config.maxTailSec) else 0.0

    /** Edges trimmed, no longer than the config allows; null when nothing is left — such a preset is not saved. */
    fun cleanPresetName(name: String, config: SoundConfig): String? =
        name.trim().take(config.maxPresetNameLength).trim().takeIf { it.isNotEmpty() }

    private const val EPSILON = 1e-6
}
