package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import kotlinx.coroutines.flow.Flow

/** What the history list needs; stored computed so that the list never re-analyses sessions. */
data class SessionSummary(
    val id: Long,
    /** Null = the default "Сессия · date" name, built by the UI in its locale. */
    val title: String?,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    /** Reference pitch and zone limits in force when it was recorded (spec 3.9). */
    val a4Hz: Double,
    val toleranceCents: Double,
    val nearCents: Double,
    val scorePercent: Int,
    val nearPercent: Int,
    val offPercent: Int,
    val maeCents: Double,
    val biasCents: Double,
    /** Zones of the first notes, for the mini bars of the history card. */
    val previewZones: List<Zone>,
    /** Null while the session has no audio (recorded before stage 11 or from a silent source). */
    val audioPath: String?,
    /** The piece this session is a take of (spec 3.15); null for a session recorded on Live. */
    val pieceId: Long? = null,
    /** Set for a video take (spec 3.19): the name of the video file — its sound track is what [audioPath] plays. */
    val videoPath: String? = null,
)

data class SessionDetails(
    val summary: SessionSummary,
    val samples: List<SessionSample?>,
    val analysis: SessionAnalysis,
)

/** A finished recording on its way to storage. */
data class NewSession(
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val config: IntonationConfig,
    val samples: List<SessionSample?>,
    val metrics: SessionMetrics,
    val previewZones: List<Zone>,
    val audioPath: String?,
    /** Set for a take recorded from a piece's screen. */
    val pieceId: Long? = null,
    /** Set for a video take (spec 3.19): the name of the video file — its sound track is what [audioPath] plays. */
    val videoPath: String? = null,
)

interface SessionRepository {
    /** Newest first. */
    val sessions: Flow<List<SessionSummary>>

    /** Null when there is no such session (deleted meanwhile). */
    suspend fun details(id: Long): SessionDetails?

    suspend fun save(session: NewSession): Long

    /** Blank or null restores the default name. */
    suspend fun rename(id: Long, title: String?)

    /** Removes the session together with its audio file. */
    suspend fun delete(id: Long) = delete(listOf(id))

    /**
     * Removes several sessions at once (spec 3.18): all of their rows or none, then the audio
     * files. Ids that are not there any more are skipped.
     */
    suspend fun delete(ids: Collection<Long>)

    /** Housekeeping at start: audio files no session points at (a crash in the middle of a take). */
    suspend fun deleteOrphanAudio()
}

/** The config a stored session was analysed with: spec values plus what it was recorded with. */
fun IntonationConfig.forSession(summary: SessionSummary): IntonationConfig = copy(
    a4Hz = summary.a4Hz,
    toleranceCents = summary.toleranceCents,
    nearCents = summary.nearCents,
)
