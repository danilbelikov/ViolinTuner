package com.example.violintuner.core.data.sound

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Every number of [com.example.violintuner.core.domain.sound.SoundSettings], one column each —
 * not a JSON blob: a migration and its test read plainly, and a parameter added later is a
 * column with a default. The space of the hall is stored by name, not by ordinal.
 */
data class SoundColumns(
    val eqEnabled: Boolean,
    val lowCutEnabled: Boolean,
    val lowCutHz: Double,
    val lowHz: Double,
    val lowGainDb: Double,
    val bodyHz: Double,
    val bodyGainDb: Double,
    val bodyQ: Double,
    val presenceHz: Double,
    val presenceGainDb: Double,
    val presenceQ: Double,
    val airHz: Double,
    val airGainDb: Double,
    val compEnabled: Boolean,
    val compThresholdDb: Double,
    val compRatio: Double,
    val compAttackMs: Double,
    val compReleaseMs: Double,
    val compMakeupDb: Double,
    /** Where the knob «Сколько» stands; null — the five were set by hand. */
    val compAmount: Double?,
    val reverbEnabled: Boolean,
    val reverbSpace: String,
    val reverbDecaySec: Double,
    val reverbPreDelayMs: Double,
    val reverbBrightness: Double,
    val reverbMix: Double,
    val outputEnabled: Boolean,
    val outputGainDb: Double,
)

/**
 * Settings of one recording — or, under [DEFAULT_OWNER], the default for all of them. No foreign
 * key: the default row belongs to no session. The row of a deleted session is removed by
 * the session DAO, in the transaction that deletes the session.
 */
@Entity(tableName = "sound_settings")
data class SoundSettingsEntity(
    @PrimaryKey val ownerId: Long,
    @Embedded val sound: SoundColumns,
) {
    companion object {
        /** Session ids start at one. */
        const val DEFAULT_OWNER = 0L
    }
}

@Entity(tableName = "sound_presets")
data class SoundPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
    @Embedded val sound: SoundColumns,
)
