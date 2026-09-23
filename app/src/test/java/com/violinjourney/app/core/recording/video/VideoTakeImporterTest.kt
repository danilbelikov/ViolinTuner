package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoTakeImporterTest {
    private val files = FakeVideoFiles()
    private val analyzer = FakeFileTakeAnalyzer()
    private val sessions = FakeSessionRepository()
    private val practice = FakeRunningPracticeStore()
    private val now = Instant.parse("2026-09-20T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Moscow"))
    private val shot = File("/cache/camera/shot.mp4")

    private fun TestScope.importer(speed: AnalysisSpeed = AnalysisSpeed()): Pair<VideoTakeImporter, MutableList<VideoTakeImporter.Saved>> {
        val importer = VideoTakeImporter(
            files, analyzer, sessions, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), practice, RepertoireConfig(), IntonationConfig(),
            clock, { testScheduler.currentTime }, speed, StandardTestDispatcher(testScheduler),
        )
        val saved = mutableListOf<VideoTakeImporter.Saved>()
        backgroundScope.launch { importer.saved.collect { saved += it } }
        return importer to saved
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private val VideoTakeImporter.working get() = state.value as VideoImport.Working

    @Test
    fun `a shot becomes a take of the piece, dated by when it was shot`() = runTest {
        val (importer, saved) = importer()
        importer.shot(pieceId = 7, cameraFile = shot)
        advance(1_000)
        assertTrue(importer.working.visible)
        assertEquals(50, importer.working.percent)
        assertTrue(importer.working.bars.isNotEmpty())
        assertTrue(importer.working.thumbPath!!.endsWith("-thumb.jpg"))

        advance(5_000)
        assertEquals(VideoImport.Idle, importer.state.value)
        val session = sessions.sessions.value.single()
        assertEquals(7L, session.pieceId)
        assertEquals(session.audioPath, session.videoPath)
        assertTrue(session.videoPath!!.endsWith(".mp4"))
        // the minute of video ended when the camera came back
        assertEquals(now.toEpochMilli() - 60_000, session.startedAtEpochMs)
        assertEquals(listOf(VideoTakeImporter.Saved(7, session.id)), saved)
        assertTrue(files.discarded.isEmpty())
    }

    @Test
    fun `remaining time shows once the speed is known`() = runTest {
        val (importer, _) = importer()
        importer.shot(7, shot)
        advance(200)
        assertNull(importer.working.remainingSec)
        advance(800)
        assertEquals(1, importer.working.remainingSec)
    }

    @Test
    fun `a picked video is copied first and dated by what it says of itself`() = runTest {
        files.info = files.info!!.copy(createdAtEpochMs = 1_700_000_000_000)
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        runCurrent()
        assertTrue("copying shows at once", importer.working.copying && importer.working.visible)
        advance(FakeVideoFiles.COPY_MS)
        assertFalse(importer.working.copying)
        advance(5_000)
        assertEquals(1_700_000_000_000, sessions.sessions.value.single().startedAtEpochMs)
    }

    @Test
    fun `a picked video without a date of its own is dated by now`() = runTest {
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(6_000)
        assertEquals(now.toEpochMilli(), sessions.sessions.value.single().startedAtEpochMs)
    }

    @Test
    fun `nothing blinks - a short analysis shows no sheet at all`() = runTest {
        files.info = files.info!!.copy(durationMs = 1_500)
        analyzer.tookMs = 400
        val (importer, saved) = importer()
        importer.shot(7, shot)
        runCurrent()
        assertFalse(importer.working.visible)
        advance(399)
        assertFalse(importer.working.visible)
        advance(2)
        assertEquals(VideoImport.Idle, importer.state.value)
        assertEquals(1, saved.size)
    }

    @Test
    fun `nothing blinks - an analysis that drags on gets its sheet, and a sheet that was shown stays to be read`() = runTest {
        files.info = files.info!!.copy(durationMs = 1_500)
        analyzer.tookMs = 900
        val (importer, _) = importer()
        importer.shot(7, shot)
        advance(699)
        assertFalse(importer.working.visible)
        advance(2)
        assertTrue(importer.working.visible)
        advance(300)
        // analysed at 900, shown at 700: it stays until 1900
        assertEquals(100, importer.working.percent)
        advance(800)
        assertTrue(importer.state.value is VideoImport.Working)
        advance(200)
        assertEquals(VideoImport.Idle, importer.state.value)
    }

    @Test
    fun `the speed is measured by the analyses themselves`() = runTest {
        val speed = AnalysisSpeed()
        val (importer, _) = importer(speed)
        importer.shot(7, shot)
        advance(6_000)
        assertEquals(2_000.0 / 60_000, speed.factor, 1e-9)
    }

    @Test
    fun `a video without sound, too long, or unreadable is said so - and a picked one is not kept`() = runTest {
        val cases = listOf<Pair<() -> Unit, VideoImportFailure>>(
            { files.info = files.info!!.copy(hasSound = false) } to VideoImportFailure.NO_SOUND,
            { files.info = files.info!!.copy(durationMs = 3_600_001) } to VideoImportFailure.TOO_LONG,
            { files.info = null } to VideoImportFailure.CANNOT_OPEN,
        )
        val (importer, _) = importer()
        val sound = files.info
        cases.forEach { (arrange, reason) ->
            files.discarded.clear()
            files.info = sound
            arrange()
            importer.picked(7, "content://video/1")
            advance(1_000)
            val failed = importer.state.value as VideoImport.Failed
            assertEquals(reason, failed.reason)
            assertNull("its original is in the gallery", failed.rescuePath)
            assertEquals(1, files.discarded.size)
            importer.dismiss()
            assertEquals(VideoImport.Idle, importer.state.value)
        }
        assertEquals(0, analyzer.calls)
    }

    @Test
    fun `a picked video that does not fit is refused before anything is copied`() = runTest {
        files.sizes["content://video/1"] = 300L * 1024 * 1024
        files.free = 100L * 1024 * 1024
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.NO_SPACE, failed.reason)
        // 300 to copy and 50 to stay free, 100 there
        assertEquals(250, failed.missingMb)
    }

    @Test
    fun `a shot without notes is not a take, but it is not thrown away either`() = runTest {
        analyzer.outcome = FileAnalysisResult.NoNotes
        val (importer, saved) = importer()
        importer.shot(7, shot)
        advance(3_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.NO_NOTES, failed.reason)
        val path = failed.rescuePath!!
        assertTrue(files.discarded.isEmpty())
        // sending it leaves the choice where it was: the file is still there
        assertEquals(path, importer.sendClicked())
        assertTrue(importer.state.value is VideoImport.Failed)

        importer.dismiss()
        assertEquals(listOf(File(path).name), files.discarded)
        assertTrue(saved.isEmpty() && sessions.sessions.value.isEmpty())
    }

    @Test
    fun `cancelling a picked video just stops`() = runTest {
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(1_000)
        importer.cancelClicked()
        assertEquals(VideoImport.Idle, importer.state.value)
        advance(10_000)
        assertTrue(sessions.sessions.value.isEmpty())
        assertEquals(1, files.discarded.size)
    }

    @Test
    fun `cancelling a shot is a question, and the analysis goes on under it`() = runTest {
        val (importer, saved) = importer()
        importer.shot(7, shot)
        advance(1_000)
        importer.cancelClicked()
        assertTrue(importer.working.asking)
        advance(400)
        assertTrue("still analysing", importer.working.percent > 50)
        importer.continueClicked()
        assertFalse(importer.working.asking)
        advance(5_000)
        assertEquals(1, saved.size)
    }

    @Test
    fun `a shot stopped for good is sent or deleted`() = runTest {
        val (importer, saved) = importer()
        importer.shot(7, shot)
        advance(1_000)
        importer.cancelClicked()
        val path = importer.sendClicked()!!
        val stopped = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.STOPPED, stopped.reason)
        assertEquals(path, stopped.rescuePath)
        advance(10_000)
        assertTrue("the analysis has stopped", saved.isEmpty())
        importer.dismiss()
        assertEquals(listOf(File(path).name), files.discarded)
    }

    @Test
    fun `a shot with notes says the violin sounded when the camera came back, a picked video says nothing`() = runTest {
        practice.start(now.toEpochMilli() - 600_000)
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(6_000)
        assertNull(practice.running.value!!.lastSoundEpochMs)

        importer.shot(7, shot)
        advance(6_000)
        assertEquals(now.toEpochMilli(), practice.running.value!!.lastSoundEpochMs)
    }

    @Test
    fun `one video at a time`() = runTest {
        val (importer, _) = importer()
        importer.shot(7, shot)
        importer.picked(8, "content://video/2")
        advance(6_000)
        assertEquals(listOf(7L), sessions.sessions.value.map { it.pieceId })
    }
}
