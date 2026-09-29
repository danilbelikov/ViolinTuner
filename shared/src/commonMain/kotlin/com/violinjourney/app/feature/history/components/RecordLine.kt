package com.violinjourney.app.feature.history.components

import com.violinjourney.app.feature.history.HistoryCard

/**
 * The list a card of a recording stands in (spec 3.36.5): the tab «Записи»; the takes of a piece, where every card is a take and
 * the word «дубль» would be said six times over; a sheet — «Записи этого дня» of the day of «Занятия» — where the card lies on the
 * ground of the screen, not on the colour of the sheet.
 */
enum class RecordPlace { Records, Takes, Sheet }

/** What a recording is: a video (a video take, bound or not), a take of a piece, or a recording of its own. */
enum class RecordKind { RECORD, TAKE, VIDEO }

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
}

/** Pure: what the line of a card says, in which order, and what TalkBack calls the card, from what the recording stores. */
object RecordLine {
    /**
     * The words after «время · длительность», in the order they stand (spec 3.36.5): the kind first — «видео» in every list, under a
     * backing too; «дубль» of a take, but not in the takes of its piece and not under a backing, whose sign says it (only takes are
     * made under one, and a take of a deleted piece stays one); a recording of its own has no word — «Запись» is its default. Then
     * «без звука»; the sign of the backing and «под минусовку» last.
     */
    fun wordsOf(card: HistoryCard, place: RecordPlace): List<LineWord> = buildList {
        when {
            card.hasVideo -> add(LineWord.VIDEO)
            card.take && !card.underBacking && place != RecordPlace.Takes -> add(LineWord.TAKE)
        }
        if (!card.hasAudio) add(LineWord.NO_SOUND)
        if (card.underBacking) add(LineWord.BACKING)
    }

    /** The first word TalkBack hears of a card, in every list alike — the line may not show it. */
    fun kindOf(card: HistoryCard): RecordKind = when {
        card.hasVideo -> RecordKind.VIDEO
        card.take -> RecordKind.TAKE
        else -> RecordKind.RECORD
    }
}
