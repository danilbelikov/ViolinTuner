package com.violinjourney.app.feature.session

/** The word the line under the name of a recording begins with (spec 3.36.5, 3.36.9). */
enum class SubtitleWord {
    /** The word of the kind of its event — «выступление · 19:02 · 3:40», of a video of an event too. */
    EVENT_KIND,

    /** «видео · 18:42 · 2:05». */
    VIDEO,

    /** «дубль · 18:42 · 2:05». */
    TAKE,

    /** A recording of its own: «09:15 · 0:36», no word — where it was made is not kept (spec 3.15). */
    NONE,
}

/** Pure: what the line under the name of a recording says first, from what the recording stores. */
object SessionSubtitle {
    /**
     * A recording of an event says the word of the kind of its event — a sound and a video of it alike: the event is what it is
     * (spec 3.36.9, «Экран записи»); a video says «видео» — a video take too; a take «дубль»; a recording of its own nothing.
     */
    fun wordOf(content: SessionContent): SubtitleWord = when {
        content.event != null -> SubtitleWord.EVENT_KIND
        content.hasVideo -> SubtitleWord.VIDEO
        content.pieceId != null -> SubtitleWord.TAKE
        else -> SubtitleWord.NONE
    }
}
