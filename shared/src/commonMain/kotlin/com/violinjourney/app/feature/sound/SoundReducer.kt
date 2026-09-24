package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundParams
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.domain.sound.UserPreset

/** The pure part of the «Звук» screen. */
object SoundReducer {
    /** Which preset these settings are, if any: a built-in one first, then the user's own, oldest first. */
    fun presetOf(settings: SoundSettings, userPresets: List<UserPreset>, config: SoundConfig): PresetRef? =
        SoundPresets.matching(settings, config)?.let(PresetRef::BuiltIn)
            ?: userPresets.firstOrNull { it.settings == settings }?.let { PresetRef.User(it.id) }

    fun captionOf(settings: SoundSettings, userPresets: List<UserPreset>, config: SoundConfig): SoundCaption =
        when (val ref = presetOf(settings, userPresets, config)) {
            is PresetRef.BuiltIn -> SoundCaption.BuiltIn(ref.preset)
            is PresetRef.User -> SoundCaption.User(userPresets.first { it.id == ref.id }.name)
            null -> SoundCaption.Custom
        }

    /** Built-in presets in their order, then the user's in the order they were saved. */
    fun chipsOf(settings: SoundSettings, userPresets: List<UserPreset>, config: SoundConfig): List<PresetChip> {
        val selected = presetOf(settings, userPresets, config)
        return BuiltInPreset.entries.map { PresetRef.BuiltIn(it) }.map { PresetChip(it, userName = null, selected = it == selected) } +
            userPresets.map { PresetRef.User(it.id) to it.name }.map { (ref, name) -> PresetChip(ref, name, selected = ref == selected) }
    }

    fun settingsOf(ref: PresetRef, userPresets: List<UserPreset>, config: SoundConfig): SoundSettings? = when (ref) {
        is PresetRef.BuiltIn -> SoundPresets.settingsOf(ref.preset, config)
        is PresetRef.User -> userPresets.firstOrNull { it.id == ref.id }?.settings
    }

    fun withBlock(settings: SoundSettings, block: SoundBlock, on: Boolean): SoundSettings = when (block) {
        SoundBlock.EQ -> settings.copy(eq = settings.eq.copy(enabled = on))
        SoundBlock.COMPRESSOR -> settings.copy(compressor = settings.compressor.copy(enabled = on))
        SoundBlock.REVERB -> settings.copy(reverb = settings.reverb.copy(enabled = on))
        SoundBlock.OUTPUT -> settings.copy(output = settings.output.copy(enabled = on))
    }

    fun isOn(settings: SoundSettings, block: SoundBlock): Boolean = when (block) {
        SoundBlock.EQ -> settings.eq.enabled
        SoundBlock.COMPRESSOR -> settings.compressor.enabled
        SoundBlock.REVERB -> settings.reverb.enabled
        SoundBlock.OUTPUT -> settings.output.enabled
    }

    /**
     * A point dragged on the curve. Whoever drags a band means it: the equalizer comes on with
     * it, and so does the low cut when that is the point — a curve that moves under the finger
     * and changes nothing in the sound would be a lie.
     */
    fun dragged(settings: SoundSettings, band: EqBand, hz: Double, gainDb: Double, config: SoundConfig): SoundSettings {
        val on = settings.copy(eq = settings.eq.copy(enabled = true))
        return when (band) {
            EqBand.LOW_CUT -> SoundParams.set(SoundParam.LOW_CUT_HZ, hz, on.copy(eq = on.eq.copy(lowCut = on.eq.lowCut.copy(enabled = true))), config)
            EqBand.LOW -> SoundParams.set(SoundParam.LOW_GAIN, gainDb, SoundParams.set(SoundParam.LOW_HZ, hz, on, config), config)
            EqBand.BODY -> SoundParams.set(SoundParam.BODY_GAIN, gainDb, SoundParams.set(SoundParam.BODY_HZ, hz, on, config), config)
            EqBand.PRESENCE -> SoundParams.set(SoundParam.PRESENCE_GAIN, gainDb, SoundParams.set(SoundParam.PRESENCE_HZ, hz, on, config), config)
            EqBand.AIR -> SoundParams.set(SoundParam.AIR_GAIN, gainDb, SoundParams.set(SoundParam.AIR_HZ, hz, on, config), config)
        }
    }

    /** Where the point of [band] stands: its frequency and its gain (the low cut sits on the zero line). */
    fun pointOf(settings: SoundSettings, band: EqBand): Pair<Double, Double> = with(settings.eq) {
        when (band) {
            EqBand.LOW_CUT -> lowCut.hz to 0.0
            EqBand.LOW -> low.hz to low.gainDb
            EqBand.BODY -> body.hz to body.gainDb
            EqBand.PRESENCE -> presence.hz to presence.gainDb
            EqBand.AIR -> air.hz to air.gainDb
        }
    }

    /** Recordings with sound, newest first: what the default can be listened on. */
    fun withSound(sessions: List<SessionSummary>): List<SessionSummary> =
        sessions.filter { it.audioPath != null }.sortedByDescending { it.startedAtEpochMs }

    /** «для 23 записей»: those with sound that have no settings of their own. */
    fun affected(sessions: List<SessionSummary>, own: Set<Long>): Int = withSound(sessions).count { it.id !in own }

    fun canReset(mode: SoundMode, own: Boolean, settings: SoundSettings, config: SoundConfig): Boolean = when (mode) {
        SoundMode.RECORDING -> own
        SoundMode.EVERYONE -> settings != SoundRules.off(config)
    }
}
