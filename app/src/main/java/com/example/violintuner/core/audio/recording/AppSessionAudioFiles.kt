package com.example.violintuner.core.audio.recording

import android.content.Context
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Session audio in the app's private storage: `files/sessions/<uuid>.m4a`. The videos of takes live
 * here too (`<uuid>.mp4` with a thumbnail): for such a take the video is its sound.
 */
class AppSessionAudioFiles @Inject constructor(
    @ApplicationContext context: Context,
    private val repertoireConfig: RepertoireConfig,
) : SessionAudioFiles {
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
        // a video take keeps a thumbnail beside it (spec 3.19)
        File(directory, thumbNameOf(name)).takeIf { it.parentFile == directory }?.delete()
    }

    override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) {
        val videoMinAgeMs = maxOf(minAgeMs, repertoireConfig.orphanVideoMinAgeMs)
        val thumbs = referenced.mapTo(HashSet(), ::thumbNameOf)
        directory.listFiles().orEmpty()
            .filter { it.name !in referenced && it.name !in thumbs }
            // A video may be the only copy of a shot, and its import takes longer than any take: it is given a day.
            .filter { nowEpochMs - it.lastModified() > if (it.name.endsWith(EXTENSION)) minAgeMs else videoMinAgeMs }
            .forEach { it.delete() }
    }

    private fun thumbNameOf(name: String) = name.substringBeforeLast('.') + THUMB_SUFFIX

    private companion object {
        const val DIRECTORY = "sessions"
        const val EXTENSION = ".m4a"
        const val THUMB_SUFFIX = "-thumb.jpg"
    }
}
