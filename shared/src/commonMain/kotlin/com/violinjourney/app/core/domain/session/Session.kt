package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import kotlinx.coroutines.flow.Flow

/** What the history list needs; stored computed so that the list never re-analyses sessions. */
data class SessionSummary(
    val id: Long,
    /** Null = the default «Запись · date» name, built by the UI in the language of the interface. */
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
    /** Zones of the first notes (spec 5.5), stored with the summary; no card draws them since spec 3.21. */
    val previewZones: List<Zone>,
    /**
     * Null while the session has no audio (recorded before stage 11 or from a silent source), and when its file is gone —
     * a copy restored without «Звук записей», a file lost (spec 3.17, 3.20): such a recording is one without sound everywhere.
     */
    val audioPath: String?,
    /** The piece this session is a take of (spec 3.15); null for a session recorded on Live. */
    val pieceId: Long? = null,
    /** Set for a video take (spec 3.19): the name of the video file — its sound track is what [audioPath] plays. */
    val videoPath: String? = null,
    /**
     * The event this recording belongs to (spec 3.35); null for every other one. Its name, date and kind are not here:
     * `EventRepository.recordEvents` gives them (plan D11) — an edit of the events re-reads that short query, not the list.
     */
    val eventId: Long? = null,
    /**
     * Where the thumbnail of the video lies — the frame its tile shows in the lists (spec 3.38): found with the sound, as [audioPath]
     * is, and only for a video whose file is there and that has one. Not stored: the store looks for the file.
     */
    val thumbPath: String? = null,
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
    /** Set for a recording made on the screen of an event (spec 3.35). */
    val eventId: Long? = null,
)

interface SessionRepository {
    /** Newest first. */
    val sessions: Flow<List<SessionSummary>>

    /** Null when there is no such session (deleted meanwhile). */
    suspend fun details(id: Long): SessionDetails?

    /** The row alone, without unpacking and analysing the samples: its name, piece and sound. Null when it is gone. */
    suspend fun summary(id: Long): SessionSummary? = details(id)?.summary

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
