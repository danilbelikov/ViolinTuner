package com.violinjourney.app.core.domain.sound

/** The presets that come with the app. Their names are UI strings; here they are known by id, which is what is stored. */
enum class BuiltInPreset(val id: String) {
    OFF("off"),
    NATURAL("natural"),
    ROOM("room"),
    CHAMBER_HALL("chamber_hall"),
    GRAND_HALL("grand_hall"),
    WARM("warm"),
}

/** A preset the user saved under a name of their own. */
data class UserPreset(val id: Long, val name: String, val settings: SoundSettings)

/**
 * Starting numbers of the built-in presets (spec 5.11) — to be tuned by ear on real recordings.
 * A preset is a whole: choosing it replaces every parameter.
 */
object SoundPresets {
    fun settingsOf(preset: BuiltInPreset, config: SoundConfig): SoundSettings {
        val off = SoundRules.off(config)
        val lowCut = off.eq.copy(enabled = true, lowCut = LowCut(enabled = true, hz = 80.0))
        val settings = when (preset) {
            BuiltInPreset.OFF -> off
            BuiltInPreset.NATURAL -> off.copy(
                eq = lowCut.copy(air = lowCut.air.copy(gainDb = 1.5)),
                compressor = CompressorAmount.settingsOf(CompressorAmount.A_LITTLE),
            )
            BuiltInPreset.ROOM -> off.copy(
                eq = lowCut.copy(air = lowCut.air.copy(gainDb = 1.5)),
                compressor = CompressorAmount.settingsOf(CompressorAmount.A_LITTLE),
                reverb = hall(off, config, ReverbSpace.ROOM, decaySec = 0.6, mix = 0.15),
            )
            BuiltInPreset.CHAMBER_HALL -> off.copy(
                eq = chamberEq(lowCut),
                compressor = CompressorAmount.settingsOf(CompressorAmount.NOTICEABLY),
                reverb = hall(off, config, ReverbSpace.HALL, decaySec = 1.8, mix = 0.25),
                output = OutputSettings(enabled = true, gainDb = 2.0),
            )
            BuiltInPreset.GRAND_HALL -> off.copy(
                eq = chamberEq(lowCut),
                compressor = CompressorAmount.settingsOf(CompressorAmount.NOTICEABLY),
                reverb = hall(off, config, ReverbSpace.HALL, decaySec = 3.0, mix = 0.35).copy(preDelayMs = 30.0),
                output = OutputSettings(enabled = true, gainDb = 2.0),
            )
            BuiltInPreset.WARM -> off.copy(
                eq = lowCut.copy(low = lowCut.low.copy(gainDb = 3.0), air = lowCut.air.copy(gainDb = -2.0)),
                compressor = CompressorAmount.settingsOf(CompressorAmount.NOTICEABLY),
                reverb = hall(off, config, ReverbSpace.ROOM, decaySec = 0.8, mix = 0.20),
            )
        }
        return SoundRules.clean(settings, config)
    }

    /** The built-in preset these settings are, to the last number; null — they are somebody's own. */
    fun matching(settings: SoundSettings, config: SoundConfig): BuiltInPreset? =
        BuiltInPreset.entries.firstOrNull { settingsOf(it, config) == settings }

    private fun chamberEq(lowCut: EqSettings) = lowCut.copy(
        low = lowCut.low.copy(gainDb = 2.0),
        presence = lowCut.presence.copy(hz = 3_200.0, gainDb = -3.5),
    )

    private fun hall(off: SoundSettings, config: SoundConfig, space: ReverbSpace, decaySec: Double, mix: Double) =
        off.reverb.copy(enabled = true, space = space, decaySec = decaySec, preDelayMs = config.preDelayOf(space), mix = mix)
}
