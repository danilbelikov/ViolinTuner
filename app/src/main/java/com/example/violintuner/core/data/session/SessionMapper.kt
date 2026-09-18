package com.example.violintuner.core.data.session

import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionSummary

/** Entity ↔ domain. Zones are stored as letters, not ordinals, so reordering the enum is safe. */
internal object SessionMapper {
    private const val IN_TUNE = 'I'
    private const val NEAR = 'N'
    private const val OFF = 'O'

    fun toEntity(session: NewSession): SessionEntity = SessionEntity(
        title = null,
        startedAtEpochMs = session.startedAtEpochMs,
        durationMs = session.durationMs,
        a4Hz = session.config.a4Hz,
        toleranceCents = session.config.toleranceCents,
        nearCents = session.config.nearCents,
        scorePercent = session.metrics.scorePercent,
        nearPercent = session.metrics.nearPercent,
        offPercent = session.metrics.offPercent,
        maeCents = session.metrics.maeCents,
        biasCents = session.metrics.biasCents,
        previewZones = encodeZones(session.previewZones),
        audioPath = session.audioPath,
        pieceId = session.pieceId,
    )

    fun toSummary(entity: SessionEntity): SessionSummary = SessionSummary(
        id = entity.id,
        title = entity.title,
        startedAtEpochMs = entity.startedAtEpochMs,
        durationMs = entity.durationMs,
        a4Hz = entity.a4Hz,
        toleranceCents = entity.toleranceCents,
        nearCents = entity.nearCents,
        scorePercent = entity.scorePercent,
        nearPercent = entity.nearPercent,
        offPercent = entity.offPercent,
        maeCents = entity.maeCents,
        biasCents = entity.biasCents,
        previewZones = decodeZones(entity.previewZones),
        audioPath = entity.audioPath,
        pieceId = entity.pieceId,
    )

    fun encodeZones(zones: List<Zone>): String = zones.joinToString(separator = "") {
        when (it) {
            Zone.IN_TUNE -> IN_TUNE
            Zone.NEAR -> NEAR
            Zone.OFF -> OFF
        }.toString()
    }

    /** Unknown letters (a file from a newer version) are skipped rather than failing the list. */
    fun decodeZones(letters: String): List<Zone> = letters.mapNotNull {
        when (it) {
            IN_TUNE -> Zone.IN_TUNE
            NEAR -> Zone.NEAR
            OFF -> Zone.OFF
            else -> null
        }
    }
}
