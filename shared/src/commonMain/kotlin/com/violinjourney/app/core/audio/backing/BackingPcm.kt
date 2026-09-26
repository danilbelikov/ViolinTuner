package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.concurrent.KeyedLock
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.io.PlatformFile

/** The backing's sound as the mix reads it, prepared at a rate (spec 5.25). */
interface BackingPcm {
    fun cached(backing: Backing, sampleRate: Int): PlatformFile?

    /**
     * The PCM of [backing] at [sampleRate], made if need be; null when its copy is gone or cannot be decoded. Blocking.
     * A second caller of the same backing and rate waits for the first and gets its file: the piece, the player, «Звук»,
     * «Поделиться» and the camera may all ask for it at once while the cache is empty.
     */
    fun prepare(backing: Backing, sampleRate: Int): PlatformFile?

    /**
     * Throws away the prepared sound of every backing whose copy is not among [keptFiles] (spec 5.25): a backing is
     * heavy — some 45 MB for four minutes — and kept only while a piece or a take still has it. One being made is left.
     */
    fun deleteOrphans(keptFiles: Set<String>)
}

/**
 * A [BackingPcm] that unpacks one backing at one rate once at a time: a ready file is handed out without waiting;
 * otherwise the caller takes the lock of the pair (copy, rate), looks again and only then [make]s it. Two unpacks of
 * one backing would write one `.partial` at once — the later one cuts the earlier one's file short, and whoever finishes
 * second finds nothing to rename (on iOS `moveItemAtPath` also refuses a target that is there) and plays without it.
 * The locks are one per process, not per instance: iOS builds its graph anew after a restore while a player of the old
 * one may still be unpacking.
 */
abstract class SingleFlightBackingPcm : BackingPcm {
    final override fun prepare(backing: Backing, sampleRate: Int): PlatformFile? =
        cached(backing, sampleRate) ?: making.withLock(backing.fileName to sampleRate) { cached(backing, sampleRate) ?: make(backing, sampleRate) }

    /**
     * Decodes [backing] into the cache at [sampleRate]; null when its copy is gone or cannot be decoded. Called under
     * the lock of the pair and only while its file is missing, so its `.partial` has one writer. Blocking.
     */
    protected abstract fun make(backing: Backing, sampleRate: Int): PlatformFile?

    private companion object {
        val making = KeyedLock<Pair<String, Int>>()
    }
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
