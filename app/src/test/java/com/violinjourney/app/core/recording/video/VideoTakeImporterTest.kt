package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import java.io.File
import java.io.IOException
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VideoTakeImporterTest {
    @get:Rule val folder = TemporaryFolder()
    private val files = FakeVideoFiles()
    private val analyzer = FakeFileTakeAnalyzer()
    private val sessions = FakeSessionRepository()
    private val practice = FakeRunningPracticeStore()
    private val now = Instant.parse("2026-09-20T10:00:00Z")
    private val clock = FixedWallClock(now, TimeZone.of("Europe/Moscow"))
    private val shot = File("/cache/camera/shot.mp4")
    private val analytics = FakeAnalytics()

    private fun TestScope.importer(
        speed: AnalysisSpeed = AnalysisSpeed(),
        sessions: SessionRepository = this@VideoTakeImporterTest.sessions,
        practice: RunningPracticeStore = this@VideoTakeImporterTest.practice,
    ): Pair<VideoTakeImporter, MutableList<VideoTakeImporter.Saved>> {
        val importer = VideoTakeImporter(
            files, analyzer, sessions, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), practice, PracticeConfig(), RepertoireConfig(), IntonationConfig(),
            clock, { testScheduler.currentTime }, speed, StandardTestDispatcher(testScheduler), analytics,
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
        assertTrue(importer.working.bars.pieces.isNotEmpty())
        assertTrue(importer.working.thumbPath!!.endsWith("-thumb.jpg"))

        advance(5_000)
        assertEquals(VideoImport.Idle, importer.state.value)
        val session = sessions.sessions.value.single()
        assertEquals(7L, session.pieceId)
        assertEquals(session.audioPath, session.videoPath)
        assertTrue(session.videoPath!!.endsWith(".mp4"))
        // the minute of video ended when the camera came back
        assertEquals(now.toEpochMilliseconds() - 60_000, session.startedAtEpochMs)
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
        assertEquals(now.toEpochMilliseconds(), sessions.sessions.value.single().startedAtEpochMs)
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
        // the room is measured on the importer's thread, not on the main one that called — and nothing is shown meanwhile
        assertEquals(0, files.freeAsked)
        assertFalse(importer.working.visible)
        runCurrent()
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.NO_SPACE, failed.reason)
        // 300 to copy and 50 to stay free, 100 there
        assertEquals(250, failed.missingMb)
        assertEquals(1, files.freeAsked)
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
        practice.start(now.toEpochMilliseconds() - 600_000)
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(6_000)
        assertNull(practice.running.value!!.lastSoundEpochMs)

        importer.shot(7, shot)
        advance(6_000)
        assertEquals(now.toEpochMilliseconds(), practice.running.value!!.lastSoundEpochMs)
    }

    @Test
    fun `a shot past the twelve hours of a practice leaves its last sound where it was`() = runTest {
        // the practice has ended by itself at its last sound (spec 3.12): a later shot would stretch it to twelve hours
        val start = now.toEpochMilliseconds() - PracticeConfig().maxPracticeMs - 60_000
        practice.start(start)
        practice.markSound(start + 40 * 60_000)
        val (importer, _) = importer()
        importer.shot(7, shot)
        advance(6_000)
        assertEquals(start + 40 * 60_000, practice.running.value!!.lastSoundEpochMs)
    }

    @Test
    fun `a decoder that gives up halfway fails the video in words - a shot is kept, a picked copy goes`() = runTest {
        // MediaCodec.CodecException is an IllegalStateException
        analyzer.failWith = IllegalStateException("codec gave up")
        val (importer, saved) = importer()
        importer.shot(7, shot)
        advance(6_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, failed.reason)
        assertTrue("the shot exists nowhere else", failed.rescuePath != null && files.discarded.isEmpty())
        importer.dismiss()

        importer.picked(7, "content://video/1")
        advance(6_000)
        val picked = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, picked.reason)
        assertNull("its original is in the gallery", picked.rescuePath)
        assertEquals(2, files.discarded.size)
        assertEquals(listOf(ErrorGroup.MEDIA, ErrorGroup.MEDIA), analytics.errors.map { it.first })
        importer.dismiss()

        analyzer.failWith = null
        importer.picked(7, "content://video/1")
        advance(6_000)
        assertEquals("the next video goes as if nothing had happened", 1, saved.size)
    }

    @Test
    fun `a video the platform cannot read is said so - and the app stays`() = runTest {
        // AVFoundation gives a duration of NaN for what it cannot read, and rounding NaN throws IllegalArgumentException
        files.onInfo = { throw IllegalArgumentException("Cannot round NaN value.") }
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(1_000)
        assertEquals(VideoImportFailure.CANNOT_OPEN, (importer.state.value as VideoImport.Failed).reason)
        assertEquals(1, files.discarded.size)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
        assertEquals(0, analyzer.calls)
    }

    @Test
    fun `a take the database cannot keep is said and not a fall - the shot stays to be sent`() = runTest {
        val full = object : SessionRepository by FakeSessionRepository() {
            override suspend fun save(session: NewSession): Long = throw IllegalStateException("database or disk is full (code 13 SQLITE_FULL)")
        }
        val (importer, saved) = importer(sessions = full)
        importer.shot(7, shot)
        advance(6_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, failed.reason)
        assertTrue(failed.rescuePath != null && files.discarded.isEmpty())
        assertTrue(saved.isEmpty())
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
    }

    @Test
    fun `a practice that cannot be marked does not undo a saved take`() = runTest {
        val stuck = object : RunningPracticeStore by practice {
            override suspend fun markSound(epochMs: Long) {
                throw IOException("the settings file cannot be written")
            }
        }
        practice.start(now.toEpochMilliseconds() - 600_000)
        val (importer, saved) = importer(practice = stuck)
        importer.shot(7, shot)
        advance(6_000)
        assertEquals(VideoImport.Idle, importer.state.value)
        assertEquals(1, saved.size)
        assertEquals(7L, sessions.sessions.value.single().pieceId)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
    }

    @Test
    fun `a shot that cannot be moved in stays where the camera left it`() = runTest {
        val cameraFile = folder.newFile("shot.mp4").apply { writeBytes(ByteArray(1_000) { 1 }) }
        files.adoptThrows = SecurityException("the camera folder is not ours")
        val (importer, _) = importer()
        importer.shot(7, cameraFile)
        advance(1_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, failed.reason)
        assertEquals(cameraFile.path, failed.rescuePath)
        assertTrue("nothing is deleted before the player says so", cameraFile.isFile && files.discarded.isEmpty())
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
        importer.dismiss()
        assertEquals(listOf(cameraFile.name), files.discarded)
    }

    @Test
    fun `a shot the platform will not move in stays too - an empty one goes`() = runTest {
        // a copy across volumes that ran out of room: the platform answers null, the shot is still whole in the camera folder
        val cameraFile = folder.newFile("shot.mp4").apply { writeBytes(ByteArray(1_000) { 1 }) }
        files.adoptFails = true
        val (importer, _) = importer()
        importer.shot(7, cameraFile)
        advance(1_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, failed.reason)
        assertEquals(cameraFile.path, failed.rescuePath)
        assertTrue("nothing is deleted before the player says so", cameraFile.isFile)
        importer.dismiss()

        // the camera came back with nothing written: there is nothing to send
        val empty = folder.newFile("empty.mp4")
        importer.shot(7, empty)
        advance(1_000)
        assertNull((importer.state.value as VideoImport.Failed).rescuePath)
        assertFalse(empty.exists())
        assertTrue("a platform that answers is no error to tell about", analytics.errors.isEmpty())
    }

    @Test
    fun `a picked video that cannot be copied in is said so`() = runTest {
        files.importThrows = IllegalStateException("the provider has died")
        val (importer, _) = importer()
        importer.picked(7, "content://video/1")
        advance(1_000)
        val failed = importer.state.value as VideoImport.Failed
        assertEquals(VideoImportFailure.CANNOT_OPEN, failed.reason)
        assertNull(failed.rescuePath)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
    }

    @Test
    fun `a picked video whose provider will not tell its size is copied all the same`() = runTest {
        files.sizeThrows = IllegalArgumentException("Invalid column _size")
        val (importer, saved) = importer()
        importer.picked(7, "content://video/1")
        advance(6_000)
        assertEquals(1, saved.size)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
    }

    @Test
    fun `a video stopped while the platform throws stays stopped, and the next one is left alone`() = runTest {
        val (importer, saved) = importer()
        files.onInfo = {
            files.onInfo = null
            // «Отмена» on a picked video, and the next one picked at once — while the platform is still busy with the first
            importer.cancelClicked()
            importer.picked(8, "content://video/2")
            throw IllegalArgumentException("Cannot round NaN value.")
        }
        importer.picked(7, "content://video/1")
        advance(1_000)
        assertTrue("the next video goes on", importer.state.value is VideoImport.Working)
        advance(6_000)
        assertEquals(listOf(8L), sessions.sessions.value.map { it.pieceId })
        assertEquals(1, saved.size)
        assertEquals("only the stopped one is gone", 1, files.discarded.size)
        assertTrue("a stop is no failure to tell about", analytics.errors.isEmpty())
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
