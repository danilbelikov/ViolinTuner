package com.violinjourney.app.core.data.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionMetrics
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SessionMapperTest {
    private val newSession = NewSession(
        startedAtEpochMs = 1_789_000_000_000,
        durationMs = 760_000,
        config = IntonationConfig(a4Hz = 442.0, toleranceCents = 12.0),
        samples = emptyList(),
        metrics = SessionMetrics(84, 11, 5, 7.3, -6.0, ViolinString.entries.associateWith { null }, emptyList()),
        previewZones = listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF, Zone.IN_TUNE),
        audioPath = null,
    )

    @Test
    fun `a new session becomes a row and comes back as the same summary`() {
        val entity = SessionMapper.toEntity(newSession).copy(id = 7)
        val summary = SessionMapper.toSummary(entity)
        assertEquals(7, summary.id)
        assertNull(summary.title)
        assertEquals(442.0, summary.a4Hz, 0.0)
        assertEquals(12.0, summary.toleranceCents, 0.0)
        assertEquals(20.0, summary.nearCents, 0.0)
        assertEquals(listOf(84, 11, 5), listOf(summary.scorePercent, summary.nearPercent, summary.offPercent))
        assertEquals(7.3, summary.maeCents, 0.0)
        assertEquals(-6.0, summary.biasCents, 0.0)
        assertEquals(newSession.previewZones, summary.previewZones)
        assertEquals(760_000, summary.durationMs)
    }

    @Test
    fun `a summary whose sound file is gone is a recording without sound — the row keeps its name`() {
        val entity = SessionMapper.toEntity(newSession.copy(audioPath = "take.m4a", videoPath = "shot.mp4")).copy(id = 3)
        assertEquals("take.m4a", SessionMapper.toSummary(entity) { true }.audioPath)
        val gone = SessionMapper.toSummary(entity) { name -> name != "take.m4a" }
        assertNull(gone.audioPath)
        assertEquals("shot.mp4", gone.videoPath, "a video take still says it was one: its screen tells the file is gone")
        assertEquals("take.m4a", entity.audioPath)
    }

    @Test
    fun `the thumbnail of a video is looked for only while its file is there`() {
        val video = SessionMapper.toEntity(newSession.copy(audioPath = "shot.mp4", videoPath = "shot.mp4")).copy(id = 8)
        val asked = mutableListOf<String>()
        val found = { name: String -> asked += name; "/files/sessions/${name.substringBefore('.')}-thumb.jpg" }

        assertEquals("/files/sessions/shot-thumb.jpg", SessionMapper.toSummary(video, found) { true }.thumbPath)
        assertNull(SessionMapper.toSummary(video, found) { false }.thumbPath, "the frame of a video that is gone is not shown")
        assertNull(SessionMapper.toSummary(video, { null }) { true }.thumbPath, "a video without a thumbnail keeps its camera")
        val sound = SessionMapper.toEntity(newSession.copy(audioPath = "take.m4a")).copy(id = 9)
        assertNull(SessionMapper.toSummary(sound, found) { true }.thumbPath)
        assertEquals(listOf("shot.mp4"), asked, "asked of the video whose file is there, and of nothing else")
    }

    @Test
    fun `a recording of an event keeps its event and every other one has none`() {
        val ofEvent = SessionMapper.toEntity(newSession.copy(eventId = 12)).copy(id = 4)
        assertEquals(12L, ofEvent.eventId)
        assertEquals(12L, SessionMapper.toSummary(ofEvent).eventId)
        assertNull(SessionMapper.toSummary(SessionMapper.toEntity(newSession).copy(id = 5)).eventId)
        assertNull(SessionMapper.toEntity(newSession.copy(pieceId = 3)).eventId, "a take of a piece belongs to no event")
    }

    @Test
    fun `zones are stored as letters and unknown letters are skipped`() {
        assertEquals("INO", SessionMapper.encodeZones(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF)))
        assertEquals(listOf(Zone.OFF, Zone.IN_TUNE), SessionMapper.decodeZones("O?I"))
        assertEquals(emptyList<Zone>(), SessionMapper.decodeZones(""))
    }
}
