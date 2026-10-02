package com.violinjourney.app.core.recording.audio

import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.ImportFailure
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.of
import com.violinjourney.app.core.recording.video.AnalysisSpeed
import com.violinjourney.app.core.recording.video.FakeFileTakeAnalyzer
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.time.FixedWallClock
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
import org.junit.Test

/** «Звук из файла» (spec 3.35, 5.28; plan D15–D18): a picked sound file becomes a recording of its event, or says why it does not. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioTakeImporterTest {
    private val sounds = FakePickedSounds()
    private val analyzer = FakeFileTakeAnalyzer()
    private val sessions = FakeSessionRepository()
    private val now = Instant.parse("2026-10-24T18:00:00Z")
    private val clock = FixedWallClock(now, TimeZone.of("Europe/Moscow"))
    private val analytics = FakeAnalytics()
    private val event = TakeOwner.Event(12)

    private fun TestScope.importer(): Pair<AudioTakeImporter, MutableList<AudioTakeImporter.Saved>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val importer = AudioTakeImporter(
            sounds, analyzer, sessions, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), RepertoireConfig(), IntonationConfig(),
            clock, { testScheduler.currentTime }, AnalysisSpeed(), io = dispatcher, dispatcher = dispatcher, analytics = analytics,
        )
        val saved = mutableListOf<AudioTakeImporter.Saved>()
        backgroundScope.launch { importer.saved.collect { saved += it } }
        return importer to saved
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private val AudioTakeImporter.working get() = state.value as MediaImport.Working

    @Test
    fun `a picked sound becomes a recording of the event, copied whole and dated by what it says of itself`() = runTest {
        sounds.probe = sounds.probe!!.copy(createdAtEpochMs = 1_700_000_000_000)
        val (importer, saved) = importer()
        importer.picked(event, "content://audio/1")
        runCurrent()
        assertTrue("copying shows at once", importer.working.copying && importer.working.visible)
        advance(FakePickedSounds.COPY_MS)
        assertFalse(importer.working.copying)
        assertTrue("the analysis goes on in the sheet of the copy", importer.working.visible)
        advance(1_000)
        assertEquals(50, importer.working.percent)
        assertTrue(importer.working.bars.pieces.isNotEmpty())

        advance(5_000)
        assertEquals(MediaImport.Idle, importer.state.value)
        val session = sessions.sessions.value.single()
        assertEquals(12L, session.eventId)
        assertNull("a recording of an event is no take", session.pieceId)
        assertNull(session.videoPath)
        assertEquals("the file as it came, its extension kept", "sound-1.sound.mp3", session.audioPath)
        assertEquals(1_700_000_000_000, session.startedAtEpochMs)
        assertEquals(listOf(AudioTakeImporter.Saved(event, session.id)), saved)
        assertTrue("a pick that came in is the recording's now", sounds.released.isEmpty())
        assertTrue(sounds.discarded.isEmpty())
    }

    @Test
    fun `without a date of its own a sound is dated by its last change, and without that by now, less its length`() = runTest {
        sounds.probe = sounds.probe!!.copy(modifiedAtEpochMs = 1_700_000_060_000)
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        advance(6_000)
        assertEquals(1_700_000_000_000, sessions.sessions.value.single().startedAtEpochMs)

        sounds.probe = sounds.probe!!.copy(modifiedAtEpochMs = null)
        importer.picked(event, "content://audio/2")
        advance(6_000)
        assertEquals(now.toEpochMilliseconds() - 60_000, sessions.sessions.value.maxBy { it.id }.startedAtEpochMs)
    }

    @Test
    fun `a file without sound, longer than an hour or that does not open is refused before it is copied`() = runTest {
        val cases = listOf<Pair<() -> Unit, ImportFailure>>(
            { sounds.probe = sounds.probe!!.copy(hasSound = false) } to ImportFailure.NO_SOUND,
            { sounds.probe = sounds.probe!!.copy(hasSound = true, durationMs = 3_600_001) } to ImportFailure.TOO_LONG,
            { sounds.probe = null } to ImportFailure.CANNOT_OPEN,
        )
        val (importer, saved) = importer()
        cases.forEachIndexed { index, (arrange, reason) ->
            arrange()
            val uri = "content://audio/$index"
            importer.picked(event, uri)
            advance(1_000)
            val failed = importer.state.value as MediaImport.Failed
            assertEquals(reason, failed.reason)
            assertEquals(event, failed.owner)
            assertNull("nothing to send on: the original is where it was", failed.rescuePath)
            assertEquals("what the platform holds of the pick goes", uri, sounds.released.last())
            importer.dismiss()
            assertEquals(MediaImport.Idle, importer.state.value)
        }
        assertEquals(0, sounds.imported)
        assertEquals(0, analyzer.calls)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a file the phone has no room for says how much is missing, and is not copied`() = runTest {
        val megabyte = 1024L * 1024
        sounds.sizes["content://audio/1"] = 10 * megabyte
        sounds.free = 0
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        advance(1_000)
        val failed = importer.state.value as MediaImport.Failed
        assertEquals(ImportFailure.NO_SPACE, failed.reason)
        val missing = 10 * megabyte + RepertoireConfig().videoFreeSpaceMarginBytes
        assertEquals(((missing + megabyte - 1) / megabyte).toInt(), failed.missingMb)
        assertEquals(0, sounds.imported)
        assertEquals(listOf("content://audio/1"), sounds.released)
    }

    @Test
    fun `a rate the detector is not tuned for is a file that does not open, and its copy goes`() = runTest {
        // 16 kHz of a dictaphone (plan D16): the rates of a video from the gallery, 44.1 and 48 kHz
        analyzer.outcome = FileAnalysisResult.UnsupportedRate
        val (importer, saved) = importer()
        importer.picked(event, "content://audio/1")
        advance(6_000)
        assertEquals(ImportFailure.CANNOT_OPEN, (importer.state.value as MediaImport.Failed).reason)
        assertEquals(listOf("sound-1.sound.mp3"), sounds.discarded)
        assertTrue(sessions.sessions.value.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a file without notes is not added, and its copy goes`() = runTest {
        analyzer.outcome = FileAnalysisResult.NoNotes
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        advance(6_000)
        val failed = importer.state.value as MediaImport.Failed
        assertEquals(ImportFailure.NO_NOTES, failed.reason)
        assertEquals(listOf("sound-1.sound.mp3"), sounds.discarded)
        assertTrue(sessions.sessions.value.isEmpty())
        importer.dismiss()
        assertEquals(MediaImport.Idle, importer.state.value)
    }

    @Test
    fun `one file at a time - a second pick is let go at once`() = runTest {
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        importer.picked(TakeOwner.Event(13), "content://audio/2")
        assertEquals(listOf("content://audio/2"), sounds.released)
        advance(6_000)
        assertEquals(listOf(12L), sessions.sessions.value.map { it.eventId })
    }

    @Test
    fun `«Отмена» stops the analysis and deletes the copy`() = runTest {
        val (importer, saved) = importer()
        importer.picked(event, "content://audio/1")
        advance(1_000)
        importer.cancelClicked()
        runCurrent()
        assertEquals(MediaImport.Idle, importer.state.value)
        assertEquals(listOf("sound-1.sound.mp3"), sounds.discarded)
        advance(6_000)
        assertTrue(sessions.sessions.value.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `whatever the platform throws is a file that did not open, told to the statistics`() = runTest {
        sounds.probeThrows = IllegalStateException("no decoder for it")
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        advance(1_000)
        assertEquals(ImportFailure.CANNOT_OPEN, (importer.state.value as MediaImport.Failed).reason)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })

        importer.dismiss()
        sounds.probeThrows = null
        analyzer.failWith = IllegalStateException("the codec gave up")
        importer.picked(event, "content://audio/2")
        advance(6_000)
        assertEquals(ImportFailure.CANNOT_OPEN, (importer.state.value as MediaImport.Failed).reason)
        assertEquals("the copy that did not become a recording goes", listOf("sound-1.sound.mp3"), sounds.discarded)
    }

    @Test
    fun `only the room is measured unseen - the sheet is up while the file is looked into`() = runTest {
        // review of stage 98a: looking into a file in a cloud is its download — the screen must not be left meanwhile, the sheet holds it
        val (importer, _) = importer()
        var whileMeasured: MediaImport? = null
        var whileLooked: MediaImport? = null
        sounds.sizes["content://audio/1"] = 1_000
        sounds.onFree = { whileMeasured = importer.state.value }
        sounds.onProbe = { whileLooked = importer.state.value }
        importer.picked(event, "content://audio/1")
        runCurrent()
        val measured = whileMeasured as MediaImport.Working
        assertFalse("the room is measured unseen, as a video's", measured.visible)
        val looked = whileLooked as MediaImport.Working
        assertTrue("«Добавляем файл…» is up while the file is looked into", looked.visible && looked.copying)
        assertEquals(event, looked.owner)
        assertEquals(0, sounds.imported)
    }

    @Test
    fun `a file looked into and refused says why, and nothing of it is copied`() = runTest {
        sounds.probe = sounds.probe!!.copy(durationMs = 3_600_001)
        val (importer, _) = importer()
        var whileLooked: MediaImport? = null
        sounds.onProbe = { whileLooked = importer.state.value }
        importer.picked(event, "content://audio/1")
        runCurrent()
        assertTrue((whileLooked as MediaImport.Working).visible)
        assertEquals(ImportFailure.TOO_LONG, (importer.state.value as MediaImport.Failed).reason)
        assertEquals(0, sounds.imported)
        assertEquals(listOf("content://audio/1"), sounds.released)
    }

    @Test
    fun `a file that cannot be copied in is said so, and nothing is heard`() = runTest {
        sounds.importFails = true
        val (importer, saved) = importer()
        importer.picked(event, "content://audio/1")
        advance(1_000)
        val failed = importer.state.value as MediaImport.Failed
        assertEquals(ImportFailure.CANNOT_OPEN, failed.reason)
        assertEquals(event, failed.owner)
        assertEquals("what the platform holds of the pick goes", listOf("content://audio/1"), sounds.released)
        assertEquals(0, analyzer.calls)
        assertTrue(saved.isEmpty())
        assertTrue("nothing to tell: the platform answered", analytics.errors.isEmpty())

        importer.dismiss()
        sounds.importFails = false
        sounds.importThrows = java.io.IOException("the provider went away")
        importer.picked(event, "content://audio/2")
        advance(1_000)
        assertEquals(ImportFailure.CANNOT_OPEN, (importer.state.value as MediaImport.Failed).reason)
        assertEquals(listOf("content://audio/1", "content://audio/2"), sounds.released)
        assertEquals("what it threw is told", listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
        assertEquals(0, analyzer.calls)
    }

    @Test
    fun `a file whose provider will not tell its size comes in all the same`() = runTest {
        sounds.sizeThrows = SecurityException("no size for you")
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        advance(6_000)
        assertEquals(12L, sessions.sessions.value.single().eventId)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
    }

    @Test
    fun `a failure of a screen that is gone holds no next file back`() = runTest {
        // review of stage 98a: a failure is shown only by the screen of its owner; left for good, it must not hold every event asleep
        sounds.probe = null
        val (importer, saved) = importer()
        importer.picked(event, "content://audio/1")
        advance(1_000)
        assertEquals(event, (importer.state.value as MediaImport.Failed).owner)

        sounds.probe = SoundProbe(durationMs = 60_000, hasSound = true, createdAtEpochMs = null, modifiedAtEpochMs = null)
        val other = TakeOwner.Event(13)
        importer.picked(other, "content://audio/2")
        runCurrent()
        assertEquals("the next file takes the place of the failure", other, (importer.state.value as MediaImport.Working).owner)
        advance(6_000)
        assertEquals(listOf(13L), sessions.sessions.value.map { it.eventId })
        assertEquals(listOf(AudioTakeImporter.Saved(other, sessions.sessions.value.single().id)), saved)
        assertFalse("taken in, not let go", "content://audio/2" in sounds.released)
    }

    @Test
    fun `an owner that is gone takes what is on its way in for it`() = runTest {
        val (importer, saved) = importer()
        // a failure no screen will show any more
        sounds.probe = null
        importer.picked(event, "content://audio/1")
        advance(1_000)
        importer.forget(TakeOwner.Event(13))
        assertTrue("another owner's is left alone", importer.state.value is MediaImport.Failed)
        importer.forget(event)
        assertEquals(MediaImport.Idle, importer.state.value)

        // a file being heard: it stops, its copy goes, nothing is saved
        sounds.probe = SoundProbe(durationMs = 60_000, hasSound = true, createdAtEpochMs = null, modifiedAtEpochMs = null)
        importer.picked(event, "content://audio/2")
        advance(1_000)
        assertTrue(importer.state.value is MediaImport.Working)
        importer.forget(event)
        assertEquals(MediaImport.Idle, importer.state.value)
        assertEquals(listOf("sound-1.sound.mp3"), sounds.discarded)
        advance(6_000)
        assertTrue(sessions.sessions.value.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `a screen sees only the file on its way in for itself`() = runTest {
        val (importer, _) = importer()
        importer.picked(event, "content://audio/1")
        runCurrent()
        assertTrue(importer.state.value.of(event) is MediaImport.Working)
        assertEquals(MediaImport.Idle, importer.state.value.of(TakeOwner.Event(13)))
        assertEquals(MediaImport.Idle, importer.state.value.of(TakeOwner.Piece(12)))
    }
}
