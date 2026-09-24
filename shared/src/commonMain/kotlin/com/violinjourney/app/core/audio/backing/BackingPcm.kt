package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.io.PlatformFile

/** The backing's sound as the mix reads it, prepared at a rate (spec 5.25). */
interface BackingPcm {
    fun cached(backing: Backing, sampleRate: Int): PlatformFile?

    /** The PCM of [backing] at [sampleRate], made if need be; null when its copy is gone or cannot be decoded. Blocking. */
    fun prepare(backing: Backing, sampleRate: Int): PlatformFile?

    /**
     * Throws away the prepared sound of every backing whose copy is not among [keptFiles] (spec 5.25): a backing is
     * heavy — some 45 MB for four minutes — and kept only while a piece or a take still has it. One being made is left.
     */
    fun deleteOrphans(keptFiles: Set<String>)
}

/** Takes a picked file in as a backing. Blocking. */
fun interface BackingFileImporter {
    fun import(uri: String): BackingImport
}

/** What adding a backing came to (spec 3.32): the sheet says why it did not. */
sealed interface BackingImport {
    data class Added(val backing: Backing) : BackingImport

    /** Not a sound this phone can decode, or not a file at all. */
    data object Unreadable : BackingImport

    data object TooLong : BackingImport

    data class NoSpace(val neededBytes: Long) : BackingImport
}
