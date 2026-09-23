package com.example.violintuner.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.HeadphoneLatencies
import com.example.violintuner.core.domain.backing.HeadphoneLatencyCodec
import com.example.violintuner.core.domain.backing.HeadphoneLatencyStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The latency of each pair of headphones (spec 5.25), one line per name in the settings file. */
class DataStoreHeadphoneLatencyStore @Inject constructor(
    private val store: DataStore<Preferences>,
    private val config: BackingConfig,
) : HeadphoneLatencyStore {
    override val latencies: Flow<HeadphoneLatencies> = store.data.map { HeadphoneLatencyCodec.decode(it[KEY]) }

    override suspend fun set(name: String, latencyMs: Int) {
        store.edit { prefs -> prefs[KEY] = HeadphoneLatencyCodec.encode(HeadphoneLatencyCodec.decode(prefs[KEY]).with(name, latencyMs, config)) }
    }

    private companion object {
        val KEY = stringPreferencesKey("headphone_latency")
    }
}
