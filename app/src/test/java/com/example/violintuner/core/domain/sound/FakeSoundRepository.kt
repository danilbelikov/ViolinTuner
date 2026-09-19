package com.example.violintuner.core.domain.sound

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSoundRepository(private val config: SoundConfig = SoundConfig()) : SoundRepository {
    override val default = MutableStateFlow(SoundRules.off(config))
    override val own = MutableStateFlow<Map<Long, SoundSettings>>(emptyMap())
    override val presets = MutableStateFlow<List<UserPreset>>(emptyList())
    private var nextPresetId = 1L

    override suspend fun setDefault(settings: SoundSettings) {
        default.value = SoundRules.clean(settings, config)
    }

    override suspend fun setOwn(sessionId: Long, settings: SoundSettings) {
        own.update { it + (sessionId to SoundRules.clean(settings, config)) }
    }

    override suspend fun clearOwn(sessionId: Long) {
        own.update { it - sessionId }
    }

    override suspend fun savePreset(name: String, settings: SoundSettings): Long? {
        val clean = SoundRules.cleanPresetName(name, config) ?: return null
        val id = nextPresetId++
        presets.update { it + UserPreset(id, clean, SoundRules.clean(settings, config)) }
        return id
    }

    override suspend fun deletePreset(id: Long) {
        presets.update { list -> list.filterNot { it.id == id } }
    }
}
