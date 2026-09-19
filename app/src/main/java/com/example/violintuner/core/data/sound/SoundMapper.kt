package com.example.violintuner.core.data.sound

import com.example.violintuner.core.domain.sound.Bell
import com.example.violintuner.core.domain.sound.CompressorSettings
import com.example.violintuner.core.domain.sound.EqSettings
import com.example.violintuner.core.domain.sound.LowCut
import com.example.violintuner.core.domain.sound.OutputSettings
import com.example.violintuner.core.domain.sound.ReverbSettings
import com.example.violintuner.core.domain.sound.ReverbSpace
import com.example.violintuner.core.domain.sound.Shelf
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings

/** Columns ↔ settings. Pure Kotlin, tested on the JVM. */
object SoundMapper {
    fun columnsOf(settings: SoundSettings): SoundColumns = with(settings) {
        SoundColumns(
            eqEnabled = eq.enabled,
            lowCutEnabled = eq.lowCut.enabled, lowCutHz = eq.lowCut.hz,
            lowHz = eq.low.hz, lowGainDb = eq.low.gainDb,
            bodyHz = eq.body.hz, bodyGainDb = eq.body.gainDb, bodyQ = eq.body.q,
            presenceHz = eq.presence.hz, presenceGainDb = eq.presence.gainDb, presenceQ = eq.presence.q,
            airHz = eq.air.hz, airGainDb = eq.air.gainDb,
            compEnabled = compressor.enabled, compThresholdDb = compressor.thresholdDb, compRatio = compressor.ratio,
            compAttackMs = compressor.attackMs, compReleaseMs = compressor.releaseMs, compMakeupDb = compressor.makeupDb,
            compAmount = compressor.amount,
            reverbEnabled = reverb.enabled, reverbSpace = reverb.space.name, reverbDecaySec = reverb.decaySec,
            reverbPreDelayMs = reverb.preDelayMs, reverbBrightness = reverb.brightness, reverbMix = reverb.mix,
            outputEnabled = output.enabled, outputGainDb = output.gainDb,
        )
    }

    /**
     * What is read is not trusted: every number goes through [SoundRules.clean], and a space
     * this version does not know (a backup from a later one) is a hall, not a crash.
     */
    fun settingsOf(columns: SoundColumns, config: SoundConfig): SoundSettings = with(columns) {
        val space = ReverbSpace.entries.firstOrNull { it.name == reverbSpace } ?: ReverbSpace.HALL
        SoundRules.clean(
            SoundSettings(
                eq = EqSettings(
                    enabled = eqEnabled,
                    lowCut = LowCut(lowCutEnabled, lowCutHz),
                    low = Shelf(lowHz, lowGainDb),
                    body = Bell(bodyHz, bodyGainDb, bodyQ),
                    presence = Bell(presenceHz, presenceGainDb, presenceQ),
                    air = Shelf(airHz, airGainDb),
                ),
                compressor = CompressorSettings(compEnabled, compThresholdDb, compRatio, compAttackMs, compReleaseMs, compMakeupDb, compAmount),
                reverb = ReverbSettings(reverbEnabled, space, reverbDecaySec, reverbPreDelayMs, reverbBrightness, reverbMix),
                output = OutputSettings(outputEnabled, outputGainDb),
            ),
            config,
        )
    }
}
