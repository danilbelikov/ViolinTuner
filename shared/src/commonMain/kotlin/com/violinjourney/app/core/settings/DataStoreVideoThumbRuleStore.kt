package com.violinjourney.app.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.violinjourney.app.core.recording.video.VideoThumbRuleStore
import com.violinjourney.app.core.recording.video.VideoThumbs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One number in the same preferences file as the settings: which rule the thumbnails of videos were made by (spec 5.31). */
class DataStoreVideoThumbRuleStore(
    private val dataStore: DataStore<Preferences>,
) : VideoThumbRuleStore {

    override val rule: Flow<Int> = dataStore.data.map { it[RULE] ?: VideoThumbs.FIRST_RULE }.distinctUntilChanged()

    override suspend fun markRule(rule: Int) {
        dataStore.edit { it[RULE] = rule }
    }

    private companion object {
        val RULE = intPreferencesKey("video_thumbs_rule")
    }
}
