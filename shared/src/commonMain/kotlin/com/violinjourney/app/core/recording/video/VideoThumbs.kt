package com.violinjourney.app.core.recording.video

import kotlinx.coroutines.flow.Flow

/**
 * The thumbnail of a video (spec 3.38, 5.31): which frame it is, and the file it lives in — `<name without extension>-thumb.jpg`
 * beside the video, the same on both platforms whatever the container: a `.mov` of an iPhone comes to Android with a copy of the
 * data, and its thumbnail with it.
 */
object VideoThumbs {
    /** What follows the name of the video without its extension. */
    const val SUFFIX = "-thumb.jpg"

    /** The rule the thumbnails are made by now: the middle of the video (spec 0.92). */
    const val RULE = 2

    /** The rule of a thumbnail made before any rule was written down: the first frame. */
    const val FIRST_RULE = 1

    /** The frame taken when the middle and the first frame are dark — a video that fades in (spec 5.13). */
    const val LATER_FRAME_MS = 1_000L

    fun nameOf(videoName: String): String = videoName.substringBeforeLast('.') + SUFFIX

    /**
     * The moments a thumbnail is looked for at, in order (spec 5.31): the middle of the video — a video from a tripod begins with a hand
     * at the phone and ends with a step back to it, and in between one plays — then the first frame and the frame a second in. A video
     * that does not tell its length gets the two of the old rule.
     */
    fun momentsMs(durationMs: Long): List<Long> =
        (if (durationMs > 0) listOf(durationMs / 2) else emptyList()).plus(listOf(0L, LATER_FRAME_MS)).distinct()

    /**
     * The first frame at [moments] that is not dark; when every one is, the first there is — the middle. [frameAt] decodes a frame, null
     * when there is none at that moment; each frame decoded and not taken is handed to [discard] (iOS frees its pictures by hand, a
     * frame of Android may be megabytes).
     */
    inline fun <T : Any> pick(moments: List<Long>, frameAt: (Long) -> T?, isDark: (T) -> Boolean, discard: (T) -> Unit): T? {
        var fallback: T? = null
        for (at in moments) {
            val frame = frameAt(at) ?: continue
            if (!isDark(frame)) {
                fallback?.let(discard)
                return frame
            }
            if (fallback == null) fallback = frame else discard(frame)
        }
        return fallback
    }
}

/**
 * The rule the stored thumbnails were made by (spec 5.31), kept in the file of the settings: a copy of the data carries it together
 * with the thumbnails it speaks of, so the thumbnails of an old copy are made anew after it is restored.
 */
interface VideoThumbRuleStore {
    /** [VideoThumbs.FIRST_RULE] until a rule has been written. */
    val rule: Flow<Int>

    suspend fun markRule(rule: Int)
}
