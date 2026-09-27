package com.violinjourney.app.core.backup

/**
 * The names of the person's data on disk (spec 5.14): folders under `files/` on Android and in Application Support on
 * iOS, and the same folders inside a copy, so that a copy made on one platform is laid out on the other. Installed apps
 * and existing copies already use these names: renaming one leaves that folder behind, unseen by the app and by the copy.
 *
 * A new folder of data is named here, and it says what becomes of it: a copy carries it ([MEDIA_DIRS]), it goes with the
 * data without being copied ([WAVEFORMS]), or it is no data at all ([CAMERA]). The rules of the system's own backup
 * (`res/xml/backup_rules.xml`, `data_extraction_rules.xml`) repeat the names; on iOS the folders that are no data for a
 * backup are opened by `IosFolders.deviceOnlyFolder`. `DataLayoutRulesTest` holds them and the folders the storages open
 * to this list.
 */
object DataLayout {
    /** The photo of the profile. */
    const val PROFILE = "profile"

    /** The photographed pages of the repertoire and their thumbnails. */
    const val SHEETS = "repertoire"

    /** The sound and the video of recordings, with the thumbnails of the videos beside them. */
    const val SESSIONS = "sessions"

    /** Accompaniment files (spec 3.32). */
    const val BACKINGS = "backings"

    /** The folders a copy carries and a restore replaces whole: a copy without some of them brings those empty. */
    val MEDIA_DIRS = listOf(PROFILE, SHEETS, SESSIONS, BACKINGS)

    /** Waveforms of the recordings: reckoned from their sound, never copied, gone with the data they were reckoned from. */
    const val WAVEFORMS = "waveforms"

    /** Photos and shots on their way in: in the cache on Android, in Application Support on iOS; never copied. */
    const val CAMERA = "camera"

    /** The settings as DataStore names them… */
    const val SETTINGS_NAME = "user_settings"

    /** …and the file it keeps them in: the same entry in a copy from either platform. */
    const val SETTINGS_FILE = "$SETTINGS_NAME.preferences_pb"
}
