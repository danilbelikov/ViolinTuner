package com.violinjourney.app.core.audio.share

/**
 * A folder of `cache/share` as the sweep sees it — one file prepared for a receiver, under the
 * name that receiver is shown. [bytes] is what the folder really costs the phone: a second name
 * given to a take is a hard link, and those bytes are already counted under `files/`.
 */
data class ShareFolder(
    val name: String,
    val bytes: Long,
    /** The newest thing inside: a folder is as young as what was last written into it. */
    val touchedAtEpochMs: Long,
    /** A video or an archive of a copy — as heavy as what it was made from, not a megabyte a minute. */
    val heavy: Boolean,
)

/**
 * What of `cache/share` goes now (spec 5.11). Pure: the rule is told about the folders, it does
 * not look at them itself. Two passes. By age first — nobody reads a prepared file a day later,
 * and a heavy one is not worth keeping even that long. Then by weight: what is left has to fit
 * the budget, and if it does not, the oldest go until it does. The newest folder is never taken
 * by the budget — the system sheet may be handing it over this very moment.
 *
 * Nothing here is data: everything swept out is made again from the recording and its settings.
 */
object ShareSweep {
    /** A prepared sound is light and slow to make again: it waits a day, as it always has. */
    const val MAX_AGE_MS = 24 * 60 * 60_000L

    /** A prepared video and an archive of a copy weigh what they were made from: an hour is enough. */
    const val HEAVY_MAX_AGE_MS = 60 * 60_000L

    /** All of `cache/share` together: three videos sent one after another are not worth a gigabyte of the phone. */
    const val BUDGET_BYTES = 512L * 1024 * 1024

    /** Names of the folders to delete. */
    fun toDelete(folders: List<ShareFolder>, nowEpochMs: Long): List<String> {
        val (old, kept) = folders.partition { nowEpochMs - it.touchedAtEpochMs > maxAgeOf(it) }
        val newest = kept.maxByOrNull { it.touchedAtEpochMs }?.name
        val crowded = mutableListOf<ShareFolder>()
        var over = kept.sumOf { it.bytes } - BUDGET_BYTES
        for (folder in kept.filter { it.name != newest }.sortedBy { it.touchedAtEpochMs }) {
            if (over <= 0) break
            crowded += folder
            over -= folder.bytes
        }
        return (old + crowded).map { it.name }
    }

    private fun maxAgeOf(folder: ShareFolder): Long = if (folder.heavy) HEAVY_MAX_AGE_MS else MAX_AGE_MS
}
