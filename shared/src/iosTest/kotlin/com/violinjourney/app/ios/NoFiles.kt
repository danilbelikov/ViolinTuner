package com.violinjourney.app.ios

import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.io.PlatformFile

/** Files of takes for the tests of the storage: none are there, none are written. */
internal object NoAudioFiles : SessionAudioFiles {
    override fun newFile() = PlatformFile("/dev/null")
    override fun existing(name: String): PlatformFile? = null
    override fun delete(name: String) = Unit
    override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
}

/** Pages of sheets for the tests of the storage: none are there, none are written. */
internal object NoSheetFiles : SheetFiles {
    override suspend fun import(sourceUri: String): SheetFiles.Stored? = null
    override fun existing(name: String): PlatformFile? = null
    override suspend fun delete(names: Collection<String>) = Unit
    override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    override fun newCameraFile() = PlatformFile("/dev/null")
}
