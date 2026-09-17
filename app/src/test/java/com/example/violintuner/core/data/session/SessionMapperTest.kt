package com.example.violintuner.core.data.session

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `zones are stored as letters and unknown letters are skipped`() {
        assertEquals("INO", SessionMapper.encodeZones(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF)))
        assertEquals(listOf(Zone.OFF, Zone.IN_TUNE), SessionMapper.decodeZones("O?I"))
        assertEquals(emptyList<Zone>(), SessionMapper.decodeZones(""))
    }
}
