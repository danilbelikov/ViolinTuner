package com.example.violintuner.core.domain.sound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How a recording sounds right now, and whose settings those are (spec 3.17). */
data class EffectiveSound(
    val settings: SoundSettings,
    /** True — the recording has settings of its own; false — it sounds «как у всех». */
    val own: Boolean,
)

/**
 * Settings of sound processing: one default for every recording («как у всех»), settings of
 * their own for the recordings that were given some, and the presets the user saved. Whatever
 * comes out of here has been through [SoundRules.clean].
 */
interface SoundRepository {
    /** The processing of every recording without settings of its own. Everything off until the user sets it. */
    val default: Flow<SoundSettings>

    /** Own settings by session id: only the recordings that have them. Tens of rows, not thousands. */
    val own: Flow<Map<Long, SoundSettings>>

    val presets: Flow<List<UserPreset>>

    suspend fun setDefault(settings: SoundSettings)

    suspend fun setOwn(sessionId: Long, settings: SoundSettings)

    /** Back to «как у всех»: the recording's own settings are forgotten. */
    suspend fun clearOwn(sessionId: Long)

    /** Null when the name is empty once cleaned: such a preset is not saved. */
    suspend fun savePreset(name: String, settings: SoundSettings): Long?

    suspend fun deletePreset(id: Long)

    /** Follows both: change the default, and a recording that sounds «как у всех» changes with it. */
    fun effective(sessionId: Long): Flow<EffectiveSound> = combine(default, own) { default, own ->
        own[sessionId]?.let { EffectiveSound(it, own = true) } ?: EffectiveSound(default, own = false)
    }
}
