package com.violinjourney.app.core.audio.backing

/**
 * Which files of the prepared backings a sweep throws away (spec 5.25), on Android and iOS alike. Pure.
 *
 * A prepared file, `<stem of the copy>-<rate>.pcm`, goes with its copy. A [PARTIAL] is written by the one holder of the
 * lock of its (backing, rate), chunk after chunk, so its time moves all the while: one nobody has written to for longer
 * than the idle time was left by a process that died mid-unpack, and goes whatever backing it is of. Its name alone is
 * no reason — an unpack of a backing removed meanwhile may still be under way.
 */
object BackingPcmSweep {
    const val PARTIAL = ".partial"
    const val RATE_SEPARATOR = '-'

    /**
     * The [names] in the folder that go: what belongs to no copy among [keptFiles], and every [PARTIAL] whose
     * [modifiedMs] lies more than [idleMs] before [nowMs].
     */
    fun doomed(names: List<String>, keptFiles: Set<String>, nowMs: Long, idleMs: Long, modifiedMs: (String) -> Long): List<String> {
        val kept = keptFiles.mapTo(HashSet()) { it.substringBeforeLast('.') }
        return names.filter { name ->
            if (name.endsWith(PARTIAL)) nowMs - modifiedMs(name) > idleMs else name.substringBeforeLast(RATE_SEPARATOR) !in kept
        }
    }
}
