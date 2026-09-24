package com.violinjourney.app.core.data.sound

import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.domain.sound.UserPreset
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomSoundRepository(
    private val dao: SoundDao,
    private val config: SoundConfig,
    private val clock: WallClock,
) : SoundRepository {

    override val default: Flow<SoundSettings> = dao.observeSettings()
        .map { rows ->
            rows.firstOrNull { it.ownerId == SoundSettingsEntity.DEFAULT_OWNER }
                ?.let { SoundMapper.settingsOf(it.sound, config) }
                ?: SoundRules.off(config)
        }
        .distinctUntilChanged()

    override val own: Flow<Map<Long, SoundSettings>> = dao.observeSettings()
        .map { rows ->
            rows.filter { it.ownerId != SoundSettingsEntity.DEFAULT_OWNER }
                .associate { it.ownerId to SoundMapper.settingsOf(it.sound, config) }
        }
        .distinctUntilChanged()

    override val presets: Flow<List<UserPreset>> = dao.observePresets()
        .map { rows -> rows.map { UserPreset(it.id, it.name, SoundMapper.settingsOf(it.sound, config)) } }

    override suspend fun setDefault(settings: SoundSettings) = put(SoundSettingsEntity.DEFAULT_OWNER, settings)

    override suspend fun setOwn(sessionId: Long, settings: SoundSettings) {
        require(sessionId != SoundSettingsEntity.DEFAULT_OWNER) { "0 is the default, not a session" }
        put(sessionId, settings)
    }

    override suspend fun clearOwn(sessionId: Long) = dao.deleteOwn(sessionId)

    override suspend fun savePreset(name: String, settings: SoundSettings): Long? {
        val clean = SoundRules.cleanPresetName(name, config) ?: return null
        return dao.insertPreset(SoundPresetEntity(name = clean, createdAtEpochMs = clock.millis(), sound = columnsOf(settings)))
    }

    override suspend fun deletePreset(id: Long) = dao.deletePreset(id)

    private suspend fun put(ownerId: Long, settings: SoundSettings) = dao.put(SoundSettingsEntity(ownerId, columnsOf(settings)))

    private fun columnsOf(settings: SoundSettings) = SoundMapper.columnsOf(SoundRules.clean(settings, config))
}
