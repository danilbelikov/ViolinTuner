package com.violinjourney.app.core.settings

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.with
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The intonation config as the player has set it up. */
interface IntonationConfigSource {
    /** Spec values; what [config] is before the stored settings have been read. */
    val default: IntonationConfig

    val config: Flow<IntonationConfig>
}

class SettingsConfigSource(
    override val default: IntonationConfig,
    repository: SettingsRepository,
) : IntonationConfigSource {
    override val config: Flow<IntonationConfig> = repository.settings
        .map { default.with(it) }
        .distinctUntilChanged()
}
