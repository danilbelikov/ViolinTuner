package com.example.violintuner.core.data.session

import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SampleCodec
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionDetails
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.core.domain.session.forSession
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val defaultConfig: IntonationConfig,
    private val audioFiles: SessionAudioFiles,
    private val clock: Clock,
) : SessionRepository {

    override val sessions: Flow<List<SessionSummary>> =
        dao.observeAll().map { entities -> entities.map(SessionMapper::toSummary) }

    /**
     * Segments and per-note figures are not stored: they are re-derived from the samples with
     * the tolerance and bucket size the session was recorded with, which gives the same result.
     */
    override suspend fun details(id: Long): SessionDetails? {
        val summary = dao.session(id)?.let(SessionMapper::toSummary) ?: return null
        val stored = dao.samples(id) ?: return null
        val samples = SampleCodec.decode(stored.data)
        val config = defaultConfig.forSession(summary).copy(sessionBucketMs = stored.bucketMs)
        return SessionDetails(summary, samples, SessionAnalyzer.analyze(samples, config))
    }

    override suspend fun save(session: NewSession): Long = dao.insert(
        session = SessionMapper.toEntity(session),
        bucketMs = session.config.sessionBucketMs,
        samples = SampleCodec.encode(session.samples),
    )

    override suspend fun rename(id: Long, title: String?) =
        dao.rename(id, title?.trim()?.takeIf { it.isNotEmpty() })

    // Row first: a file without a session is cleaned up later, a session without its file
    // would show a player that cannot play.
    override suspend fun delete(id: Long) {
        val audioPath = dao.session(id)?.audioPath
        dao.delete(id)
        audioPath?.let(audioFiles::delete)
    }

    override suspend fun deleteOrphanAudio() = audioFiles.deleteOrphans(
        referenced = dao.audioPaths().toSet(),
        nowEpochMs = clock.millis(),
        // a file younger than the longest possible take may be the take being recorded
        minAgeMs = defaultConfig.maxSessionMs,
    )
}
