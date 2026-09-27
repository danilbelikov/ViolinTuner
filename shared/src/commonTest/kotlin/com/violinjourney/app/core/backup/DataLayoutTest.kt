package com.violinjourney.app.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The names of the data are what installed apps and existing copies already hold (spec 5.14): a name changed here
 * would leave a folder of the person's data behind, so every one is pinned to its letters.
 */
class DataLayoutTest {
    @Test
    fun `the folders and the settings file keep the names the data already has`() {
        assertEquals("profile", DataLayout.PROFILE)
        assertEquals("repertoire", DataLayout.SHEETS)
        assertEquals("sessions", DataLayout.SESSIONS)
        assertEquals("backings", DataLayout.BACKINGS)
        assertEquals("waveforms", DataLayout.WAVEFORMS)
        assertEquals("camera", DataLayout.CAMERA)
        assertEquals("user_settings.preferences_pb", DataLayout.SETTINGS_FILE)
    }

    @Test
    fun `a copy carries the four folders of media and nothing reckoned or on its way`() {
        assertEquals(listOf("profile", "repertoire", "sessions", "backings"), DataLayout.MEDIA_DIRS)
        assertFalse(DataLayout.WAVEFORMS in DataLayout.MEDIA_DIRS, "waveforms are reckoned again from the sound")
        assertFalse(DataLayout.CAMERA in DataLayout.MEDIA_DIRS, "a shot on its way is no data yet")
    }

    @Test
    fun `the folders of a copy are the folders of the data`() {
        assertEquals(
            DataLayout.MEDIA_DIRS,
            listOf(BackupPaths.PROFILE, BackupPaths.SHEETS, BackupPaths.SESSIONS, BackupPaths.BACKINGS),
        )
    }
}
