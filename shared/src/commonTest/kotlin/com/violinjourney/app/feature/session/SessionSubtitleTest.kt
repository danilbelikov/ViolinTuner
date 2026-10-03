package com.violinjourney.app.feature.session

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

/**
 * The word the line under the name of a recording begins with (spec 3.36.5, 3.36.9 «Экран записи»; review of stage 99): a recording of an
 * event says the word of the kind of its event — its sound and its video alike, a video of an event is still what the event is; then a
 * video says «видео» — a video take too, before «дубль»; a take «дубль»; a recording of its own nothing. Pure, so it runs on iOS too.
 */
class SessionSubtitleTest {
    private val concert = SessionEvent(3, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null)

    private fun content(pieceId: Long? = null, hasVideo: Boolean = false, event: SessionEvent? = null) = SessionContent(
        title = null, pieceId = pieceId, startedAtEpochMs = 0, durationMs = 220_000, toleranceCents = 8.0, scorePercent = 80,
        nearPercent = 15, offPercent = 5, maeCents = 4.0, biasCents = 1.0, biasZone = null, perString = emptyMap(), problemNotes = emptyList(),
        rollNotes = emptyList(), segments = emptyList(), hasAudio = true, hasVideo = hasVideo, event = event,
    )

    @Test
    fun `a recording of an event says the kind of its event - its sound and its video alike`() {
        assertEquals(SubtitleWord.EVENT_KIND, SessionSubtitle.wordOf(content(event = concert)))
        assertEquals(SubtitleWord.EVENT_KIND, SessionSubtitle.wordOf(content(hasVideo = true, event = concert)), "a video of an event")
    }

    @Test
    fun `a video says video - a video take too - a take says take and a recording of its own nothing`() {
        assertEquals(SubtitleWord.VIDEO, SessionSubtitle.wordOf(content(pieceId = 7, hasVideo = true)), "a video take: «видео», not «дубль»")
        assertEquals(SubtitleWord.VIDEO, SessionSubtitle.wordOf(content(hasVideo = true)))
        assertEquals(SubtitleWord.TAKE, SessionSubtitle.wordOf(content(pieceId = 7)))
        assertEquals(SubtitleWord.NONE, SessionSubtitle.wordOf(content()))
    }
}
