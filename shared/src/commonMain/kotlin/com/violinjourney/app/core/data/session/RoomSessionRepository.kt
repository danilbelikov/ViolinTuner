package com.violinjourney.app.core.data.session

import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SampleCodec
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.session.forSession
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.TakeDeleted
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.recording.video.VideoThumbRuleStore
import com.violinjourney.app.core.recording.video.VideoThumbs
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * A recording whose sound file is gone — a copy restored without «Звук записей», a file lost — is read as a recording
 * without sound (spec 3.17, 3.20): [SessionSummary.audioPath] is null, and every list, menu and screen treats it so. The
 * row itself is not changed: the file may come back with another copy. The thumbnail of a video is looked for with its
 * sound ([SessionSummary.thumbPath], spec 3.38) — once the thumbnails are all of the current rule ([thumbRules], spec 5.31): a
 * frame of the old rule read before it is made anew would stay on the screen, kept by its path, so until the pass that makes them
 * anew is over a video keeps its camera.
 */
class RoomSessionRepository(
    private val dao: SessionDao,
    private val defaultConfig: IntonationConfig,
    private val audioFiles: SessionAudioFiles,
    private val clock: WallClock,
    private val analytics: Analytics,
    /** Which rule the thumbnails of the videos were made by: no frame is shown before they are all of [VideoThumbs.RULE]. */
    private val thumbRules: VideoThumbRuleStore,
    /** Where the files are looked for: one question to the file system per recording with sound, one more for a video. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Where a recording is unpacked and analysed: an hour is 72 000 samples, too much for the main thread. */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : SessionRepository {

    // before [sessions], which is built of it: properties are made in the order they are written
    private val thumbsReady: Flow<Boolean> = thumbRules.rule.map { it >= VideoThumbs.RULE }.distinctUntilChanged()

    override val sessions: Flow<List<SessionSummary>> =
        combine(dao.observeAll(), thumbsReady) { entities, ready -> entities.map { SessionMapper.toSummary(it, thumbsOf(ready), ::soundFound) } }
            .flowOn(io)

    /**
     * Segments and per-note figures are not stored: they are re-derived from the samples with
     * the tolerance and bucket size the session was recorded with, which gives the same result.
     */
    override suspend fun details(id: Long): SessionDetails? {
        val entity = dao.session(id) ?: return null
        val stored = dao.samples(id) ?: return null
        val thumbs = thumbsOf(thumbsReady.first())
        return withContext(compute) {
            val summary = SessionMapper.toSummary(entity, thumbs, ::soundFound)
            val samples = SampleCodec.decode(stored.data)
            val config = defaultConfig.forSession(summary).copy(sessionBucketMs = stored.bucketMs)
            SessionDetails(summary, samples, SessionAnalyzer.analyze(samples, config))
        }
    }

    override suspend fun summary(id: Long): SessionSummary? {
        val entity = dao.session(id) ?: return null
        val thumbs = thumbsOf(thumbsReady.first())
        return withContext(io) { SessionMapper.toSummary(entity, thumbs, ::soundFound) }
    }

    override suspend fun save(session: NewSession): Long = dao.insert(
        session = SessionMapper.toEntity(session),
        bucketMs = session.config.sessionBucketMs,
        samples = SampleCodec.encode(session.samples),
    )

    override suspend fun rename(id: Long, title: String?) =
        dao.rename(id, title?.trim()?.takeIf { it.isNotEmpty() })

    // Row first: a file without a session is cleaned up later, a session without its file
    // would show a player that cannot play. Counted by what really went, the sound and the video apart (spec 3.34):
    // an id that was gone already sends nothing.
    override suspend fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val gone = dao.delete(ids)
        gone.forEach { files -> files.audioPath?.let(audioFiles::delete) }
        gone.groupBy { it.videoPath != null }.forEach { (video, rows) -> analytics.track(TakeDeleted(video, rows.size)) }
    }

    override suspend fun deleteOrphanAudio() = audioFiles.deleteOrphans(
        referenced = dao.audioPaths().toSet(),
        nowEpochMs = clock.millis(),
        // a file younger than the longest possible take may be the take being recorded
        minAgeMs = defaultConfig.maxSessionMs,
    )

    private fun soundFound(name: String): Boolean = audioFiles.existing(name) != null

    private fun thumbsOf(ready: Boolean): (String) -> String? = if (ready) ::thumbFound else { _ -> null }

    private fun thumbFound(videoName: String): String? = audioFiles.thumbOf(videoName)?.filePath
}
