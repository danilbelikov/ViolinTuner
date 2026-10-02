package com.violinjourney.app.core.recording

/**
 * How the sheet of a file on its way to becoming a recording paces itself (spec 3.19, 5.13): the same for a video, shot or picked,
 * and for a sound from a file (spec 5.28). Pure.
 */
internal object ImportPacing {
    /** An analysis expected to take longer than this shows its sheet at once; a shorter one only when it drags on past it. */
    const val SHOW_FROM_MS = 700L

    /** A sheet that was shown stays at least this long: nothing on this app's screens flashes by. */
    const val MIN_SHOWN_MS = 1_200L

    /** The analysing thread tells its progress far more often than ten times a second; the sheet takes this many. */
    const val PROGRESS_EVERY_MS = 100L

    /** The seconds left are said from this share of the file on: before it the estimate is noise. */
    private const val REMAINING_FROM = 0.2f
    const val PERCENT = 100
    private const val MS_PER_SECOND = 1_000
    private const val BYTES_PER_MB = 1024L * 1024

    /** «45 %» of [fraction] of the file. */
    fun percentOf(fraction: Float): Int = (fraction * PERCENT).toInt().coerceIn(0, PERCENT)

    /** About how many seconds are left after [spentMs] for [fraction] of the file; null while it is too early to say. */
    fun remainingSec(spentMs: Long, fraction: Float): Int? =
        if (fraction >= REMAINING_FROM) ((spentMs * (1 - fraction) / fraction) / MS_PER_SECOND).toInt().coerceAtLeast(1) else null

    /** [bytes] of room that are missing, as whole megabytes rounded up: «нужно ещё 12 МБ». */
    fun missingMb(bytes: Long): Int = ((bytes + BYTES_PER_MB - 1) / BYTES_PER_MB).toInt()
}
