package com.violinjourney.app.feature.history.components

import com.violinjourney.app.feature.history.HistoryCard

/**
 * The list a card of a recording stands in (spec 3.36.5): the tab «Записи»; the takes of a piece, where every card is a take and
 * the word «дубль» would be said six times over; a sheet — «Записи этого дня» of the day of «Занятия» — where the card lies on the
 * ground of the screen, not on the colour of the sheet; the records of an event on its screen (spec 3.36.9) — every one of them its,
 * so no word of its kind, and a card there is only opened: no «⋯», no long press, a chevron at its end.
 */
enum class RecordPlace { Records, Takes, Sheet, Event }

/**
 * What a recording is: a video (a video take, bound or not, a video of an event), a take of a piece, the sound of an event (spec 3.36.9:
 * the tile tells it from a video, TalkBack reads it «звук»), or a recording of its own.
 */
enum class RecordKind { RECORD, TAKE, VIDEO, SOUND }

/** A word of the line of a card after «время · длительность» (spec 3.36.5). */
enum class LineWord {
    /** «дубль». */
    TAKE,

    /** «видео». */
    VIDEO,

    /** «без звука». */
    NO_SOUND,

    /** The sign of the backing and «под минусовку». */
    BACKING,

    /** The word of the kind of the event of a recording (spec 3.36.9): «выступление», «урок», the name of a kind of one's own. */
    EVENT_KIND,
}

/** Pure: what the line of a card says, in which order, and what TalkBack calls the card, from what the recording stores. */
object RecordLine {
    /**
     * The words after «время · длительность», in the order they stand (spec 3.36.5): the kind first — «видео» in every list, under a
     * backing too; «дубль» of a take, but not in the takes of its piece and not under a backing, whose sign says it (only takes are
     * made under one, and a take of a deleted piece stays one); a recording of its own has no word — «Запись» is its default. A recording
     * of an event says the word of the kind of its event in the place of «видео» — «19:02 · 3:40 · выступление»: the tile tells a video
     * from a sound (spec 3.36.9) — but not on the screen of its event, where every record is its: «· видео» there, as in the takes of a
     * piece. Then «без звука»; the sign of the backing and «под минусовку» last.
     */
    fun wordsOf(card: HistoryCard, place: RecordPlace): List<LineWord> = buildList {
        when {
            card.event != null && place != RecordPlace.Event -> add(LineWord.EVENT_KIND)
            card.hasVideo -> add(LineWord.VIDEO)
            card.take && !card.underBacking && place != RecordPlace.Takes -> add(LineWord.TAKE)
        }
        if (!card.hasAudio) add(LineWord.NO_SOUND)
        if (card.underBacking) add(LineWord.BACKING)
    }

    /**
     * The first word TalkBack hears of a card, in every list alike — the line may not show it: what its tile is. The sound of an event is
     * «звук» (spec 3.36.9: the tile tells it from a video), a recording of its own «запись».
     */
    fun kindOf(card: HistoryCard): RecordKind = when {
        card.hasVideo -> RecordKind.VIDEO
        card.take -> RecordKind.TAKE
        card.event != null -> RecordKind.SOUND
        else -> RecordKind.RECORD
    }
}
