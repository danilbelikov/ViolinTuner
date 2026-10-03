package com.violinjourney.app.core.data.session

import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionSummary

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
        videoPath = session.videoPath,
        eventId = session.eventId,
    )

    /**
     * [soundFound] answers whether the file of the sound is there: a recording whose file is gone — a copy restored
     * without «Звук записей», a file lost — is read as one without sound (spec 3.17, 3.20). The row keeps its name.
     * [thumbOf] finds the thumbnail of a video by its name (spec 3.38): it is asked only of a video whose file is there —
     * the frame of a video that is gone is not shown, even if its thumbnail stayed.
     */
    fun toSummary(entity: SessionEntity, thumbOf: (String) -> String? = { null }, soundFound: (String) -> Boolean = { true }): SessionSummary {
        val audio = entity.audioPath?.takeIf(soundFound)
        return summaryOf(entity, audio, thumbPath = entity.videoPath?.takeIf { audio != null }?.let(thumbOf))
    }

    private fun summaryOf(entity: SessionEntity, audioPath: String?, thumbPath: String?) = SessionSummary(
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
        audioPath = audioPath,
        pieceId = entity.pieceId,
        videoPath = entity.videoPath,
        eventId = entity.eventId,
        thumbPath = thumbPath,
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
