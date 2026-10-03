package com.violinjourney.app.core.data

import com.violinjourney.app.core.audio.backing.NoBackingPcm
import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.share.FakeShareFiles
import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.progress.FakeProfileRepository
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.recording.video.FakeVideoFiles
import com.violinjourney.app.core.recording.video.FakeVideoThumbRuleStore
import com.violinjourney.app.core.recording.video.VideoThumbs
import com.violinjourney.app.core.time.MutableWallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The thumbnails of an older rule, made anew once at a start (spec 3.38, 5.31). */
@OptIn(ExperimentalCoroutinesApi::class)
class HousekeepingThumbsTest {
    private val sessions = FakeSessionRepository()
    private val videos = FakeVideoFiles()

    private fun TestScope.housekeeping(rules: FakeVideoThumbRuleStore) = Housekeeping(
        sessions, FakeSessionWaveforms(), FakeAvatarFiles(), FakeProfileRepository(), FakeShareFiles(), FakeRepertoireRepository(), NoBackings,
        NoBackingPcm, videos, rules, MutableWallClock(0), UnconfinedTestDispatcher(testScheduler),
    )

    private fun recording(id: Long, audio: String?, video: String? = null) = SessionSummary(
        id = id, title = null, startedAtEpochMs = id * 60_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 10, offPercent = 10, maeCents = 5.0, biasCents = 0.0, previewZones = listOf(Zone.IN_TUNE),
        audioPath = audio, videoPath = video,
    )

    // newest first, as the store gives them: a video, a sound, a video whose file is gone, an older video
    private val stored = listOf(
        recording(4, audio = "new.mp4", video = "new.mp4"),
        recording(3, audio = "take.m4a"),
        recording(2, audio = null, video = "gone.mp4"),
        recording(1, audio = "old.mov", video = "old.mov"),
    )

    @Test
    fun `thumbnails of the first rule are made anew once — the newest first — and the rule is marked`() = runTest {
        sessions.sessions.value = stored
        val rules = FakeVideoThumbRuleStore(VideoThumbs.FIRST_RULE)

        housekeeping(rules).atStart()

        assertEquals("only the videos whose file is there", listOf("new.mp4", "old.mov"), videos.thumbsMade)
        assertEquals(VideoThumbs.RULE, rules.rule.value)

        housekeeping(rules).atStart()
        assertEquals("the next start makes nothing", listOf("new.mp4", "old.mov"), videos.thumbsMade)
    }

    @Test
    fun `a video whose frame cannot be had does not stop the pass`() = runTest {
        sessions.sessions.value = stored
        videos.thumbFails = setOf("new.mp4")
        val rules = FakeVideoThumbRuleStore(VideoThumbs.FIRST_RULE)

        housekeeping(rules).atStart()

        assertEquals(listOf("new.mp4", "old.mov"), videos.thumbsMade)
        assertEquals(VideoThumbs.RULE, rules.rule.value)
    }

    @Test
    fun `thumbnails of the current rule are left as they are`() = runTest {
        sessions.sessions.value = stored

        housekeeping(FakeVideoThumbRuleStore()).atStart()

        assertEquals(emptyList<String>(), videos.thumbsMade)
    }
}
