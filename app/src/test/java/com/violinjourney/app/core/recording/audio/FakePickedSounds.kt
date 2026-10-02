package com.violinjourney.app.core.recording.audio

import java.io.File
import kotlinx.coroutines.delay

/** Sound files that are only names: nothing touches a disk. A copy is `<n>.sound.<extension>`, as the platforms name theirs. */
class FakePickedSounds : PickedSounds {
    var probe: SoundProbe? = SoundProbe(durationMs = 60_000, hasSound = true, createdAtEpochMs = null, modifiedAtEpochMs = null)
    var free = Long.MAX_VALUE
    val sizes = mutableMapOf<String, Long>()
    var importFails = false

    /** What the platform throws instead of answering: a provider, a file system, a decoder. */
    var importThrows: Exception? = null
    var sizeThrows: Exception? = null
    var probeThrows: Exception? = null

    /** The extension of the picked file, kept by its copy. */
    var extension = "mp3"

    /** The picks let go without coming in, in order. */
    val released = mutableListOf<String>()

    /** The copies removed, by name. */
    val discarded = mutableListOf<String>()

    /** How many files were copied in. */
    var imported = 0
    private var next = 1

    /** Called as the room is asked for, and as the file is looked into: what the importer shows meanwhile. */
    var onFree: (() -> Unit)? = null
    var onProbe: (() -> Unit)? = null

    override fun sizeOf(uri: String): Long? = sizeThrows?.let { throw it } ?: sizes[uri]

    override fun freeBytes(): Long {
        onFree?.invoke()
        return free
    }

    override fun probe(uri: String): SoundProbe? {
        onProbe?.invoke()
        probeThrows?.let { throw it }
        return probe
    }

    override suspend fun import(uri: String): File? {
        delay(COPY_MS)
        importThrows?.let { throw it }
        if (importFails) return null
        imported++
        return File("/files/sessions/sound-${next++}${PickedSounds.MARK}$extension")
    }

    override fun release(uri: String) {
        released += uri
    }

    override fun discard(file: File) {
        discarded += file.name
    }

    companion object {
        const val COPY_MS = 300L
    }
}
