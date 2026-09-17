package com.example.violintuner.core.audio.recording

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Session audio in the app's private storage: `files/sessions/<uuid>.m4a`. */
class AppSessionAudioFiles @Inject constructor(@ApplicationContext context: Context) : SessionAudioFiles {
    private val directory = File(context.filesDir, DIRECTORY)

    override fun newFile(): File {
        directory.mkdirs()
        return File(directory, "${UUID.randomUUID()}$EXTENSION")
    }

    // Names come from the database; a name with a path in it is not one of ours.
    override fun existing(name: String): File? =
        File(directory, name).takeIf { it.parentFile == directory && it.isFile }

    override fun delete(name: String) {
        existing(name)?.delete()
    }

    override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        directory.listFiles().orEmpty()
            .filter { it.name !in referenced && nowEpochMs - it.lastModified() > minAgeMs }
            .forEach { it.delete() }
    }

    private companion object {
        const val DIRECTORY = "sessions"
        const val EXTENSION = ".m4a"
    }
}
