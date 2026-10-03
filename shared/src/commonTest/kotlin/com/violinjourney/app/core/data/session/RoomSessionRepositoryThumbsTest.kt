package com.violinjourney.app.core.data.session

import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionMetrics
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.platformFile
import com.violinjourney.app.core.recording.video.FakeVideoThumbRuleStore
import com.violinjourney.app.core.recording.video.VideoThumbs
import com.violinjourney.app.core.time.MutableWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The frames of the videos come from the store of the recordings only once the thumbnails are all of the current rule (spec 5.31):
 * a frame of the old rule read before the pass made it anew would stay on the screen, kept by its path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSessionRepositoryThumbsTest {
    /** One video take, its file and its thumbnail on the disk. */
    private val video = SessionMapper.toEntity(
        NewSession(
            startedAtEpochMs = 1_789_000_000_000,
            durationMs = 60_000,
            config = IntonationConfig(),
            samples = emptyList(),
            metrics = SessionMetrics(84, 11, 5, 7.3, -6.0, ViolinString.entries.associateWith { null }, emptyList()),
            previewZones = listOf(Zone.IN_TUNE),
            audioPath = "shot.mov",
            pieceId = 3,
            videoPath = "shot.mov",
        ),
    ).copy(id = 1)

    private class Rows(entity: SessionEntity) : SessionDao() {
        private val rows = MutableStateFlow(listOf(entity))
        override fun observeAll() = rows
        override suspend fun session(id: Long) = rows.value.firstOrNull { it.id == id }
        override suspend fun samples(id: Long): SamplesEntity? = null
        override suspend fun audioPaths() = rows.value.mapNotNull { it.audioPath }
        override suspend fun rename(id: Long, title: String?) = Unit
        override suspend fun filesOf(ids: List<Long>) = emptyList<SessionFiles>()
        override suspend fun deleteSessions(ids: List<Long>) = Unit
        override suspend fun deleteOwnSound(ids: List<Long>) = Unit
        override suspend fun insertSession(session: SessionEntity) = session.id
        override suspend fun insertSamples(samples: SamplesEntity) = Unit
    }

    private object EverythingThere : SessionAudioFiles {
        override fun newFile(): PlatformFile = platformFile("/sessions/new.m4a")
        override fun existing(name: String): PlatformFile = platformFile("/sessions/$name")
        override fun delete(name: String) = Unit
        override fun thumbOf(name: String): PlatformFile = platformFile("/sessions/${VideoThumbs.nameOf(name)}")
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    @Test
    fun `a video keeps its camera until the thumbnails are of the current rule`() = runTest {
        val rules = FakeVideoThumbRuleStore(VideoThumbs.FIRST_RULE)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = RoomSessionRepository(
            Rows(video), IntonationConfig(), EverythingThere, MutableWallClock(0), FakeAnalytics(), rules, io = dispatcher, compute = dispatcher,
        )

        assertNull(repository.sessions.first().single().thumbPath, "made by the old rule: no frame yet")
        assertNull(repository.summary(1)?.thumbPath)

        rules.markRule(VideoThumbs.RULE)

        assertEquals("/sessions/shot-thumb.jpg", repository.sessions.first().single().thumbPath)
        assertEquals("/sessions/shot-thumb.jpg", repository.summary(1)?.thumbPath)
    }
}
