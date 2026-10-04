package com.violinjourney.app.feature.events

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.MicUnavailableException
import com.violinjourney.app.core.audio.MicUnavailableReason
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.SampleClock
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventPlan
import com.violinjourney.app.core.domain.events.EventStep
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.domain.VideoQuality
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
import com.violinjourney.app.core.recording.audio.FakePickedSounds
import com.violinjourney.app.core.recording.audio.SoundProbe
import com.violinjourney.app.core.recording.testTakePipeline
import com.violinjourney.app.core.recording.video.AnalysisSpeed
import com.violinjourney.app.core.recording.video.FakeFileTakeAnalyzer
import com.violinjourney.app.core.recording.video.FakeVideoFiles
import com.violinjourney.app.core.recording.video.VideoPick
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.settings.videoQuality
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.feature.events.screen.EventDialog
import com.violinjourney.app.feature.events.screen.EventEffect
import com.violinjourney.app.feature.events.screen.EventIntent
import com.violinjourney.app.feature.events.screen.EventSection
import com.violinjourney.app.feature.events.screen.EventSheet
import com.violinjourney.app.feature.events.screen.EventState
import com.violinjourney.app.feature.events.screen.EventViewModel
import com.violinjourney.app.feature.events.screen.RecordWay
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.repertoire.piece.ImportWords
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The screen of an event (spec 3.35, 3.36.9): its parts, its four ways to add a recording, its programme and its deletion. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {
    private val settings = FakeSettingsRepository()
    // Saturday 24 October 2026, 18:00 in Moscow: the day of the concert
    private val now = Instant.parse("2026-10-24T15:00:00Z")
    private val today = LocalDate(2026, 10, 24)
    private val clock = FixedWallClock(now, TimeZone.of("Europe/Moscow"))
    private val events = FakeEventRepository()
    private val sessions = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val practice = FakeRunningPracticeStore()
    private val videoFiles = FakeVideoFiles()
    private val videoAnalyzer = FakeFileTakeAnalyzer()
    private val sounds = FakePickedSounds()

    @get:Rule
    val folder = TemporaryFolder()
    private var videoImporter: VideoTakeImporter? = null
    private var soundImporter: AudioTakeImporter? = null

    /** The words of an event as the resources would say them: in a JVM test there are none to read. */
    private val words = object : EventWords {
        override suspend fun nameOf(name: EventName): String = when (name) {
            is EventName.Titled -> name.title
            is EventName.OfKind -> "Урок"
        }

        override suspend fun recordTitleOf(event: SessionEvent): String = "${nameOf(event.name)} · ${event.date}"
    }

    /** The fake source has no sound to write, so nothing here is ever asked for a file. */
    private object NoAudioFiles : SessionAudioFiles {
        override fun newFile(): File = error("the fake source records no sound")
        override fun existing(name: String): File? = null
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private object NoShareFiles : ShareFiles {
        override fun processed(audioName: String, settings: SoundSettings, fileName: String) = File("/cache/share/$fileName")
        override suspend fun original(audio: File, fileName: String): File = File("/cache/share/$fileName")
        override suspend fun handedOver(file: File) = Unit
        override suspend fun sweep(nowEpochMs: Long) = Unit
    }

    /** Counts how often it is listened to: the screen of an event holds the microphone only while a sound is recorded. */
    private class CountingSource(private val delegate: PitchSource, override val requiresMicPermission: Boolean = false) : PitchSource {
        var collections = 0
        var active = 0
        override val clock: SampleClock? = null
        override val audioTap = null
        override fun frames(config: IntonationConfig): Flow<PitchFrame> =
            delegate.frames(config).onStart { collections++; active++ }.onCompletion { active-- }
    }

    private var source: CountingSource? = null

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // one importer of each kind for all the screens of a test, as it is one for the app
    private fun TestScope.videos() = videoImporter ?: VideoTakeImporter(
        videoFiles, videoAnalyzer, sessions, FakeSessionWaveforms(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), practice, PracticeConfig(),
        RepertoireConfig(), IntonationConfig(), clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    ).also { videoImporter = it }

    private fun TestScope.soundFiles() = soundImporter ?: AudioTakeImporter(
        sounds, FakeFileTakeAnalyzer(), sessions, FakeSessionWaveforms(), SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), RepertoireConfig(), IntonationConfig(),
        clock, { testScheduler.currentTime }, AnalysisSpeed(), StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler),
        analytics = NoOpAnalytics(),
    ).also { soundImporter = it }

    private fun TestScope.screen(eventId: Long, saved: SavedStateHandle = SavedStateHandle(mapOf(EventViewModel.ARG_EVENT_ID to eventId))): Pair<EventViewModel, MutableList<EventEffect>> {
        val pitch = source ?: CountingSource(FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource)).also { source = it }
        val takes = testTakePipeline(pitch, sessions, NoAudioFiles, practice, PracticeConfig(), clock, StandardTestDispatcher(testScheduler))
        val viewModel = EventViewModel(
            saved, events, sessions, repertoire, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()), takes, videos(), soundFiles(),
            videoFiles, NoShareFiles, EventsConfig(), RepertoireConfig(), clock, words, settings.videoQuality,
        )
        val effects = mutableListOf<EventEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.takeState.collect {} }
        backgroundScope.launch { viewModel.mediaImport.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private suspend fun concert(date: LocalDate = today): Long = events.add(
        EventDraft(kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = date, startMinutes = 18 * 60 + 30, title = "Осенний концерт"),
        Repeat.NONE, until = null, today = today,
    )

    private suspend fun lesson(date: LocalDate, repeat: Repeat = Repeat.NONE): Long = events.add(
        EventDraft(kind = KindRef.BuiltIn(BuiltInKind.LESSON), date = date, startMinutes = 17 * 60, durationMinutes = 45, place = "Анна Сергеевна"),
        repeat, until = null, today = today,
    )

    /** One more event of the repeat of [of], on [date], as the storage lays them ahead. */
    private fun layNext(of: Long, date: LocalDate, id: Long): Long {
        val first = events.events.value.single { it.id == of }
        events.events.value = events.events.value + first.copy(id = id, date = date)
        return id
    }

    private val sound = SoundProbe(durationMs = 60_000, hasSound = true, createdAtEpochMs = null, modifiedAtEpochMs = null)

    private val EventViewModel.loaded get() = state.value as EventState.Loaded

    /** «Добавить запись» and a row of its sheet. */
    private fun EventViewModel.add(way: RecordWay) {
        onIntent(EventIntent.AddRecordClicked)
        onIntent(EventIntent.AddRecordWay(way))
    }

    @Test
    fun `the parts stand in the order of the kind and of the time`() = runTest {
        val tomorrow = lesson(LocalDate(2026, 10, 25))
        val (lessonScreen, _) = screen(tomorrow)
        assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS_LATER), lessonScreen.loaded.order)
        assertFalse(lessonScreen.loaded.pinnedAddRecord)
        assertEquals("Анна Сергеевна", lessonScreen.loaded.header.person)
        assertTrue(lessonScreen.loaded.header.teacher)

        val tonight = concert()
        val (concertScreen, _) = screen(tonight)
        assertEquals("from its day on, nothing in it yet: «Можно добавить»", listOf(EventSection.CAN_ADD), concertScreen.loaded.order)
        assertTrue("from its day on «Добавить запись» is pinned", concertScreen.loaded.pinnedAddRecord)
        assertFalse(concertScreen.loaded.canAddRecord)
    }

    @Test
    fun `an event that is not there is «Событие не найдено»`() = runTest {
        val (viewModel, _) = screen(404)
        assertEquals(EventState.NotFound, viewModel.state.value)
    }

    @Test
    fun `a sound recorded here is a recording of the event, on top and highlighted, its screen not opened`() = runTest {
        val id = concert()
        // what the storage says of the events of the recordings once this one is among them
        events.recordEvents.value = mapOf(id to SessionEvent(id, "Осенний концерт", today, KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null))
        val (viewModel, effects) = screen(id)
        assertEquals("the screen does not listen by itself", 0, source!!.collections)

        viewModel.onIntent(EventIntent.AddRecordClicked)
        runCurrent()
        assertEquals(EventSheet.AddRecord, viewModel.loaded.sheet)
        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.MIC))
        runCurrent()
        assertNull("the press closes the sheet", viewModel.loaded.sheet)
        advance(3_500)
        assertTrue(viewModel.takeState.value.recording)
        assertEquals(3L, viewModel.takeState.value.elapsedSeconds)

        viewModel.onIntent(EventIntent.RecordStopClicked)
        advance(500)
        assertFalse(viewModel.takeState.value.recording)
        assertEquals("the microphone goes with the stop", 0, source!!.active)
        val saved = sessions.saved.single()
        assertEquals(id, saved.eventId)
        assertNull("a recording of an event is no take", saved.pieceId)
        val record = viewModel.loaded.records.single()
        assertEquals(record.id, viewModel.loaded.newRecordId)
        assertEquals("named by the event until it is given a name of its own", "Осенний концерт", record.event?.title)
        assertTrue("its screen does not open by itself", effects.isEmpty())
        advance(5_000)
        assertNull("the highlight settles", viewModel.loaded.newRecordId)
    }

    @Test
    fun `a sound recorded while a practice runs marks the sound of the practice, as a take does`() = runTest {
        // plan D35: the chain follows the practice from the first moment of the screen
        practice.running.value = RunningPractice(startedAtEpochMs = now.toEpochMilliseconds() - 10 * 60_000, lastSoundEpochMs = null)
        val (viewModel, _) = screen(concert())
        viewModel.add(RecordWay.MIC)
        advance(3_000)
        viewModel.onIntent(EventIntent.RecordStopClicked)
        advance(500)
        assertEquals(now.toEpochMilliseconds(), practice.running.value!!.lastSoundEpochMs)
    }

    @Test
    fun `without a practice a recording marks nothing`() = runTest {
        val (viewModel, _) = screen(concert())
        viewModel.add(RecordWay.MIC)
        advance(3_000)
        viewModel.onIntent(EventIntent.RecordStopClicked)
        advance(500)
        assertNull(practice.running.value)
        assertEquals(1, sessions.saved.size)
    }

    @Test
    fun `without the microphone «Записать звук» asks for it and records only by its press`() = runTest {
        source = CountingSource(FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource), requiresMicPermission = true)
        val (viewModel, effects) = screen(concert())
        viewModel.onIntent(EventIntent.MicPermissionChanged(granted = false))
        runCurrent()
        assertEquals(false, viewModel.loaded.micPermission)

        viewModel.add(RecordWay.MIC)
        runCurrent()
        assertEquals(listOf<EventEffect>(EventEffect.RequestMicPermission), effects)
        assertEquals("the sheet stays: the row says why it sleeps", EventSheet.AddRecord, viewModel.loaded.sheet)

        viewModel.onIntent(EventIntent.MicPermissionChanged(granted = true))
        advance(1_000)
        assertFalse("allowed, the row wakes; the recording does not begin by itself", viewModel.takeState.value.recording)
        assertEquals(0, source!!.collections)
        assertEquals(EventSheet.AddRecord, viewModel.loaded.sheet)

        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.MIC))
        advance(1_500)
        assertTrue(viewModel.takeState.value.recording)
    }

    /** «Изменить» opens the form of the event; «Добавить заметку» and the row «Заметку» of «Можно добавить» open it on its notes. */
    @Test
    fun `the edit and the notes open the form of the event - the notes in focus`() = runTest {
        val id = lesson(LocalDate(2026, 10, 25))
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(EventIntent.EditClicked)
        viewModel.onIntent(EventIntent.AddNotesClicked)
        runCurrent()
        assertEquals(listOf(EventEffect.OpenForm(id, focusNotes = false), EventEffect.OpenForm(id, focusNotes = true)), effects)
    }

    @Test
    fun `while a sound is recorded the ways that would end it do not answer`() = runTest {
        val id = concert()
        val pieceId = repertoire.add(PieceDraft(title = "Концерт ля минор"), nowEpochMs = 1)
        events.setProgram(id, listOf(pieceId))
        val (viewModel, effects) = screen(id)
        viewModel.add(RecordWay.MIC)
        advance(1_000)

        viewModel.onIntent(EventIntent.EditClicked)
        viewModel.onIntent(EventIntent.AddNotesClicked)
        viewModel.onIntent(EventIntent.DeleteClicked)
        viewModel.onIntent(EventIntent.PieceClicked(pieceId))
        viewModel.onIntent(EventIntent.ProgramAddClicked)
        viewModel.onIntent(EventIntent.ProgramRemoved(pieceId))
        viewModel.onIntent(EventIntent.AddRecordClicked)
        viewModel.onIntent(EventIntent.VideoPicked(VideoPick.Ready("content://video/1")))
        runCurrent()
        assertTrue(effects.isEmpty())
        assertNull(viewModel.loaded.sheet)
        assertNull(viewModel.loaded.dialog)
        assertEquals(listOf(pieceId), events.programs.value[id])
        assertEquals("a pick not wanted now is let go", listOf("content://video/1"), videoFiles.released)
        assertTrue("the recording goes on", viewModel.takeState.value.recording)
    }

    @Test
    fun `a video from the gallery and a sound from a file become recordings of the event, each in its own words`() = runTest {
        val id = concert()
        val (viewModel, effects) = screen(id)
        viewModel.add(RecordWay.GALLERY)
        runCurrent()
        assertEquals(listOf<EventEffect>(EventEffect.PickVideo), effects)
        viewModel.onIntent(EventIntent.VideoPicked(VideoPick.Ready("content://video/1")))
        advance(1_000)
        assertEquals(ImportWords.VIDEO_RECORD, viewModel.mediaImport.value.words)
        assertTrue(viewModel.mediaImport.value.import is MediaImport.Working)
        assertTrue("«Добавить запись» sleeps meanwhile", viewModel.loaded.busyImport)
        // copied in 300 ms, heard in two seconds: in by 2.3 s, and highlighted for a while after it
        advance(1_500)
        val video = sessions.saved.single()
        assertEquals(id, video.eventId)
        assertNotNull(video.videoPath)
        assertEquals(sessions.sessions.value.single().id, viewModel.loaded.newRecordId)

        viewModel.add(RecordWay.FILE)
        runCurrent()
        assertEquals(EventEffect.PickSound, effects.last())
        viewModel.onIntent(EventIntent.SoundPicked("content://audio/1"))
        advance(1_000)
        assertEquals(ImportWords.SOUND_FILE, viewModel.mediaImport.value.words)
        assertTrue("the sheet of the sound is this screen's", viewModel.mediaImport.value.import is MediaImport.Working)
        assertTrue("«Добавить запись» sleeps meanwhile", viewModel.loaded.busyImport)
        advance(6_000)
        assertEquals(listOf(id, id), sessions.saved.map { it.eventId })
        assertFalse(viewModel.loaded.busyImport)
        assertEquals(2, viewModel.loaded.records.size)
    }

    @Test
    fun `«Добавить запись» sleeps while a sound of another event is copied and heard, and wakes when it is in`() = runTest {
        val id = concert()
        val (viewModel, _) = screen(id)
        soundFiles().picked(TakeOwner.Event(99), "content://audio/9")
        advance(1_000)
        assertTrue("one file at a time, whoever's (D37)", viewModel.loaded.busyImport)
        assertEquals("that sheet is not this screen's", MediaImport.Idle, viewModel.mediaImport.value.import)
        viewModel.onIntent(EventIntent.AddRecordClicked)
        runCurrent()
        assertNull(viewModel.loaded.sheet)
        viewModel.onIntent(EventIntent.VideoPicked(VideoPick.Ready("content://video/1")))
        runCurrent()
        assertEquals("a pick not wanted now is let go", listOf("content://video/1"), videoFiles.released)
        advance(6_000)
        assertEquals(listOf(99L), sessions.saved.map { it.eventId })
        assertFalse(viewModel.loaded.busyImport)
    }

    @Test
    fun `a sound that failed for a screen that is gone holds this one back in nothing`() = runTest {
        // review of stage 98a: its failure is shown only by the screen of its event — left, or deleted, it is no work of anyone's
        sounds.probe = null
        soundFiles().picked(TakeOwner.Event(99), "content://audio/9")
        advance(1_000)
        assertTrue(soundFiles().state.value is MediaImport.Failed)

        val id = concert()
        val (viewModel, effects) = screen(id)
        assertFalse("nothing spins", viewModel.loaded.busyImport)
        assertEquals("not this screen's sheet", MediaImport.Idle, viewModel.mediaImport.value.import)
        viewModel.add(RecordWay.FILE)
        runCurrent()
        assertEquals(listOf<EventEffect>(EventEffect.PickSound), effects)
        sounds.probe = sound
        viewModel.onIntent(EventIntent.SoundPicked("content://audio/1"))
        advance(6_000)
        assertEquals("the file of this event comes in", listOf(id), sessions.saved.map { it.eventId })
    }

    @Test
    fun `the event is not deleted from under a file of its own on its way in`() = runTest {
        val id = concert()
        val (viewModel, _) = screen(id)
        // the room is measured unseen: nothing stands over the screen yet, and «⋯» is there to be pressed
        videos().picked(TakeOwner.Event(id), "content://video/1")
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        assertNull("not while its video comes in", viewModel.loaded.dialog)
        advance(6_000)
        assertEquals(id, sessions.saved.single().eventId)

        // nor while its failure waits for an answer
        sounds.probe = null
        soundFiles().picked(TakeOwner.Event(id), "content://audio/1")
        advance(1_000)
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        assertNull(viewModel.loaded.dialog)
        viewModel.onIntent(EventIntent.Import(com.violinjourney.app.feature.repertoire.piece.ImportAction.Dismiss))
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        assertEquals(EventDialog.DeleteOne, viewModel.loaded.dialog)
    }

    @Test
    fun `the events that go take the failures of their files with them`() = runTest {
        // a failure no screen will show any more would hold every other video back for good
        val first = lesson(LocalDate(2026, 10, 19), Repeat.WEEKLY)
        val next = layNext(first, LocalDate(2026, 10, 26), id = 100)
        val later = layNext(first, LocalDate(2026, 11, 2), id = 101)
        videoAnalyzer.outcome = FileAnalysisResult.NoNotes
        videos().picked(TakeOwner.Event(next), "content://video/1")
        sounds.probe = null
        soundFiles().picked(TakeOwner.Event(later), "content://audio/1")
        advance(6_000)
        assertTrue(videos().state.value is MediaImport.Failed)
        assertTrue(soundFiles().state.value is MediaImport.Failed)

        val (viewModel, _) = screen(first)
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        viewModel.onIntent(EventIntent.DeleteConfirmed(EditScope.FOLLOWING))
        runCurrent()
        assertEquals(MediaImport.Idle, videos().state.value)
        assertEquals(MediaImport.Idle, soundFiles().state.value)
    }

    @Test
    fun `a recording the lost microphone ended is kept quietly, stands highlighted, and the screen wakes`() = runTest {
        // spec 3.36.9: «потеря микрофона — тихое сохранение», as a take's (3.15)
        val id = concert()
        val pieceId = repertoire.add(PieceDraft(title = "Концерт ля минор"), nowEpochMs = 1)
        val working = FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource)
        var broken = false
        source = CountingSource(
            object : PitchSource {
                override val requiresMicPermission = false
                override val audioTap = null
                override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                    working.frames(config).collect { frame ->
                        if (!broken && frame.tMs > 4_000) {
                            broken = true
                            throw MicUnavailableException(MicUnavailableReason.READ_FAILED, "unplugged")
                        }
                        emit(frame)
                    }
                }
            },
        )
        val (viewModel, effects) = screen(id)
        viewModel.add(RecordWay.MIC)
        advance(4_500)

        assertFalse("the bar goes", viewModel.takeState.value.recording)
        assertEquals(id, sessions.saved.single().eventId)
        assertEquals("it stands highlighted, as a stopped one does", viewModel.loaded.records.single().id, viewModel.loaded.newRecordId)
        assertTrue("without a word, and its screen stays shut", effects.isEmpty())
        assertEquals("the microphone goes", 0, source!!.active)

        // what slept while it recorded answers again
        viewModel.onIntent(EventIntent.ProgramAddClicked)
        runCurrent()
        assertTrue(viewModel.loaded.sheet is EventSheet.Program)
        viewModel.onIntent(EventIntent.SheetHidden)
        viewModel.onIntent(EventIntent.PieceClicked(pieceId))
        runCurrent()
        assertEquals(listOf<EventEffect>(EventEffect.OpenPiece(pieceId)), effects)
    }

    @Test
    fun `«Добавить запись» sleeps while a file is on its way in for anyone, and that sheet is not this screen's`() = runTest {
        val id = concert()
        val (viewModel, _) = screen(id)
        videos().picked(TakeOwner.Piece(7), "content://video/1")
        advance(1_000)
        assertTrue(viewModel.loaded.busyImport)
        assertEquals("the sheet of a piece's video stays on the piece", MediaImport.Idle, viewModel.mediaImport.value.import)
        viewModel.onIntent(EventIntent.AddRecordClicked)
        runCurrent()
        assertNull(viewModel.loaded.sheet)
        viewModel.onIntent(EventIntent.SoundPicked("content://audio/1"))
        assertEquals("one file at a time, whoever's", listOf("content://audio/1"), sounds.released)
    }

    @Test
    fun `the camera writes where the screen said, and a result nobody asked for is ignored`() = runTest {
        val id = concert()
        val saved = SavedStateHandle(mapOf(EventViewModel.ARG_EVENT_ID to id))
        val (viewModel, effects) = screen(id, saved)
        viewModel.add(RecordWay.CAMERA)
        runCurrent()
        val launch = effects.single() as EventEffect.LaunchVideoCamera
        assertTrue(launch.filePath.endsWith(".mp4"))
        assertEquals("the settings' default", VideoQuality.P720, launch.quality)

        // the camera pushed the app out of memory: a new view model gets the same saved state
        val (after, _) = screen(id, saved)
        after.onIntent(EventIntent.VideoShotFinished(saved = true))
        advance(6_000)
        assertEquals(id, sessions.saved.single().eventId)
        after.onIntent(EventIntent.VideoShotFinished(saved = true))
        advance(6_000)
        assertEquals("a result nobody asked for is ignored", 1, sessions.saved.size)
    }

    @Test
    fun `a shot backed out of leaves nothing - its file goes, and nothing comes in`() = runTest {
        // the camera that did not come up (`SystemWindowGate`) is answered the same way by the route
        videoFiles.cameraFolder = folder.newFolder("camera")
        val id = concert()
        val saved = SavedStateHandle(mapOf(EventViewModel.ARG_EVENT_ID to id))
        val (viewModel, effects) = screen(id, saved)
        viewModel.add(RecordWay.CAMERA)
        runCurrent()
        val shot = File((effects.single() as EventEffect.LaunchVideoCamera).filePath)
        // what the camera wrote before the person backed out
        shot.writeBytes(ByteArray(16))

        viewModel.onIntent(EventIntent.VideoShotFinished(saved = false))
        runCurrent()
        assertFalse("the reserved file is deleted", shot.exists())
        assertEquals(MediaImport.Idle, videos().state.value)
        viewModel.onIntent(EventIntent.VideoShotFinished(saved = true))
        advance(6_000)
        assertTrue("the path was let go with it: a late answer brings nothing in", sessions.saved.isEmpty())
    }

    @Test
    fun `a row of «Добавить запись» answers once - a second tap lands on a sheet that is going`() = runTest {
        // the lesson of stages 120 and 122: a press is bound to what it was made on
        val id = concert()
        val saved = SavedStateHandle(mapOf(EventViewModel.ARG_EVENT_ID to id))
        val (viewModel, effects) = screen(id, saved)
        viewModel.onIntent(EventIntent.AddRecordClicked)
        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.CAMERA))
        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.CAMERA))
        runCurrent()
        val launch = effects.single() as EventEffect.LaunchVideoCamera
        // the camera writes the file the one press reserved: its answer finds it
        viewModel.onIntent(EventIntent.VideoShotFinished(saved = true))
        advance(6_000)
        assertEquals(id, sessions.saved.single().eventId)
        assertTrue(launch.filePath.endsWith("shot-1.mp4"))

        for ((way, effect) in listOf(RecordWay.GALLERY to EventEffect.PickVideo, RecordWay.FILE to EventEffect.PickSound)) {
            effects.clear()
            viewModel.onIntent(EventIntent.AddRecordClicked)
            viewModel.onIntent(EventIntent.AddRecordWay(way))
            viewModel.onIntent(EventIntent.AddRecordWay(way))
            runCurrent()
            assertEquals("$way: one system window", listOf(effect), effects)
        }

        effects.clear()
        viewModel.onIntent(EventIntent.AddRecordClicked)
        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.MIC))
        runCurrent()
        // the second tap of «Записать звук» lands on the bar that took the sheet's place: it does not stop or begin anything
        viewModel.onIntent(EventIntent.AddRecordWay(RecordWay.MIC))
        advance(3_000)
        assertTrue(viewModel.takeState.value.recording)
        viewModel.onIntent(EventIntent.RecordStopClicked)
        advance(500)
        assertEquals(2, sessions.saved.size)
    }

    @Test
    fun `the programme is the marked elements in the order of their marks`() = runTest {
        val id = concert()
        val vivaldi = repertoire.add(PieceDraft(title = "Концерт ля минор", composer = "А. Вивальди"), nowEpochMs = 1)
        val gavotte = repertoire.add(PieceDraft(title = "Гавот", composer = "Ф. Госсек"), nowEpochMs = 2)
        val (viewModel, _) = screen(id)
        viewModel.onIntent(EventIntent.ProgramAddClicked)
        runCurrent()
        val sheet = viewModel.loaded.sheet as EventSheet.Program
        assertEquals(setOf(vivaldi, gavotte), sheet.sections.flatMap { it.pieces }.map { it.id }.toSet())
        assertTrue(sheet.checked.isEmpty())

        viewModel.onIntent(EventIntent.ProgramToggled(gavotte))
        viewModel.onIntent(EventIntent.ProgramToggled(vivaldi))
        viewModel.onIntent(EventIntent.ProgramToggled(gavotte))
        viewModel.onIntent(EventIntent.ProgramToggled(gavotte))
        runCurrent()
        assertEquals("a new mark goes to the end", listOf(vivaldi, gavotte), (viewModel.loaded.sheet as EventSheet.Program).checked)
        viewModel.onIntent(EventIntent.ProgramDone)
        viewModel.onIntent(EventIntent.ProgramDone)
        runCurrent()
        assertNull(viewModel.loaded.sheet)
        assertEquals(listOf(vivaldi, gavotte), events.programs.value[id])
        assertEquals(listOf(1, 2), viewModel.loaded.program.map { it.number })
        assertEquals(listOf("А. Вивальди", "Ф. Госсек"), viewModel.loaded.program.map { it.composer })

        viewModel.onIntent(EventIntent.ProgramRemoved(vivaldi))
        runCurrent()
        assertEquals("the cross takes it away at once", listOf(gavotte), viewModel.loaded.program.map { it.pieceId })
        assertEquals(1, viewModel.loaded.program.single().number)
    }

    @Test
    fun `a single event is deleted from its dialog, once, and its recordings keep the name they wore`() = runTest {
        val id = concert()
        events.recordEvents.value = mapOf(id to SessionEvent(id, "Осенний концерт", today, KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null))
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(EventIntent.DeleteConfirmed(scope = null))
        runCurrent()
        assertTrue("not asked: not heard", events.applied.isEmpty())

        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        assertEquals(EventDialog.DeleteOne, viewModel.loaded.dialog)
        viewModel.onIntent(EventIntent.DeleteConfirmed(scope = null))
        viewModel.onIntent(EventIntent.DeleteConfirmed(scope = null))
        runCurrent()
        val (_, frozen) = events.applied.single()
        assertEquals(mapOf(id to "Осенний концерт · 2026-10-24"), frozen)
        assertEquals(listOf<EventEffect>(EventEffect.Close), effects)
    }

    @Test
    fun `the answers of the sheet of a repeat delete this one alone, or this one and the following`() = runTest {
        // spec 3.36.9: «Только этот урок» — the event alone; «Этот и следующие · с … и дальше» — the repeat from the cut on
        val first = lesson(LocalDate(2026, 10, 19), Repeat.WEEKLY)
        val seriesId = events.events.value.single().seriesId!!
        val next = layNext(first, LocalDate(2026, 10, 26), id = 100)
        layNext(first, LocalDate(2026, 11, 2), id = 101)
        val (viewModel, effects) = screen(first)
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        val sheet = viewModel.loaded.sheet as EventSheet.DeleteScope
        assertEquals("the first date «Этот и следующие» deletes — the past lesson itself is over", LocalDate(2026, 10, 26), sheet.from)
        viewModel.onIntent(EventIntent.DeleteConfirmed(EditScope.FOLLOWING))
        runCurrent()
        assertEquals(
            "the repeat ends the day before today, and the lesson pressed on goes too",
            EventPlan(EventStep.DeleteFollowing(seriesId, cut = today, selectedId = first)),
            events.applied.single().first,
        )
        assertEquals(listOf<EventEffect>(EventEffect.Close), effects)

        val (other, _) = screen(next)
        other.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        other.onIntent(EventIntent.DeleteConfirmed(EditScope.ONLY_THIS))
        runCurrent()
        assertEquals("«Только этот» — the lesson alone", EventPlan(EventStep.DeleteOne(next)), events.applied.last().first)
    }

    @Test
    fun `an event of a repeat asks which ones go, in the word of its kind and with its dates`() = runTest {
        val id = lesson(LocalDate(2026, 10, 19), Repeat.WEEKLY)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        val sheet = viewModel.loaded.sheet as EventSheet.DeleteScope
        assertEquals(SeriesWord.LESSON, sheet.word)
        assertEquals(LocalDate(2026, 10, 19), sheet.date)
        assertEquals(DayOfWeek.MONDAY, sheet.weekday)
        assertNull(viewModel.loaded.dialog)

        viewModel.onIntent(EventIntent.SheetHidden)
        runCurrent()
        assertNull("a swipe only hides it", viewModel.loaded.sheet)
        assertTrue(events.applied.isEmpty())

        viewModel.onIntent(EventIntent.DeleteClicked)
        runCurrent()
        viewModel.onIntent(EventIntent.DeleteConfirmed(EditScope.ONLY_THIS))
        runCurrent()
        assertEquals(1, events.applied.size)
        assertEquals(listOf<EventEffect>(EventEffect.Close), effects)
    }
}
