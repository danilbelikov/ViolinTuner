package com.violinjourney.app.feature.share

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.FakeSoundRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.recording.overlay.NotesOverlay
import com.violinjourney.app.core.recording.overlay.NotesVideoConfig
import com.violinjourney.app.core.recording.overlay.NotesVideoRenderer
import com.violinjourney.app.core.recording.overlay.OverlayWords
import com.violinjourney.app.feature.events.EventWords
import com.violinjourney.app.feature.sound.SoundCaption
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val config = SoundConfig()
    private val sessions = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val sound = FakeSoundRepository(config)
    private val hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
    private lateinit var audio: File

    /**
     * Takes [tookMs] of virtual time, telling its progress on the way; fails when told to, or throws [throws] with half a
     * file written — as the system's refusals come on iOS. [stuckMs] first, deaf to a cancel: the unpacking of a backing.
     */
    private inner class Renderer(var tookMs: Long, var fails: Boolean = false, var throws: Exception? = null, var stuckMs: Long = 0) : SoundRenderer {
        var renders = 0
        private var running = 0
        var mostAtOnce = 0
            private set

        override suspend fun render(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean {
            renders++
            mostAtOnce = maxOf(mostAtOnce, ++running)
            try {
                if (stuckMs > 0) withContext(NonCancellable) { delay(stuckMs) }
                repeat(STEPS) { step ->
                    delay(tookMs / STEPS)
                    onProgress((step + 1f) / STEPS)
                }
                if (fails) return false
                throws?.let {
                    target.parentFile?.mkdirs()
                    target.writeText("half a sound")
                    throw it
                }
                target.parentFile?.mkdirs()
                target.writeText("sound")
                return true
            } catch (e: kotlinx.coroutines.CancellationException) {
                target.delete()
                throw e
            } finally {
                running--
            }
        }

        var backingRenders = 0
        var backingOffset: Int? = null
        override suspend fun renderWithBacking(
            source: File,
            settings: SoundSettings,
            backing: com.violinjourney.app.core.audio.share.RenderBacking,
            target: File,
            onProgress: (Float) -> Unit,
        ): Boolean {
            backingRenders++
            backingOffset = backing.offsetMs
            return render(source, settings, target, onProgress).also { renders-- }
        }

        var videoRenders = 0
        override suspend fun renderVideo(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean {
            videoRenders++
            return render(source, settings, target, onProgress).also { renders-- }
        }
    }

    private inner class Files : ShareFiles {
        var originalFails = false
        var originalTakesMs = 0L
        val handedOver = mutableListOf<File>()
        // by the key of the recording and its settings, as the real folders are: a mix, the notes in another language are files of their own
        override fun processed(audioName: String, settings: SoundSettings, fileName: String) = File(folder.root, "share/$audioName-${settings.hashCode()}/$fileName")
        override suspend fun original(audio: File, fileName: String): File? {
            delay(originalTakesMs)
            return if (originalFails) null else File(folder.root, "share/original/$fileName").also { it.parentFile?.mkdirs(); audio.copyTo(it, overwrite = true) }
        }
        override suspend fun handedOver(file: File) {
            handedOver += file
        }
        override suspend fun sweep(nowEpochMs: Long) = Unit
    }

    private val audioFiles = object : SessionAudioFiles {
        override fun newFile(): File = error("not used")
        override fun existing(name: String): File? = audio.takeIf { name == it.name }
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private val texts = object : ShareTexts {
        override fun title(title: String?, pieceTitle: String?, startedAtEpochMs: Long) = title ?: pieceTitle?.let { "$it · 18 сентября" } ?: "Сессия · 18 сентября"
        override fun message(title: String?, pieceTitle: String?, scorePercent: Int, startedAtEpochMs: Long) = "${title ?: pieceTitle ?: "Сессия"} · $scorePercent % · 18 сентября"
    }

    private val files = Files()
    private val videoFiles = com.violinjourney.app.core.recording.video.FakeVideoFiles()
    private val events = FakeEventRepository()

    /** The words of an event as the resources would say them: in a JVM test there are none to read. */
    private val eventWords = object : EventWords {
        override suspend fun nameOf(name: EventName): String = when (name) {
            is EventName.Titled -> name.title
            is EventName.OfKind -> "Урок"
        }

        override suspend fun recordTitleOf(event: SessionEvent): String = "${nameOf(event.name)} · 24 октября"
    }
    private val speed = RenderSpeed()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        audio = folder.newFile("take.m4a").apply { writeText("original sound") }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun recording(audioName: String? = "take.m4a", durationMs: Long = 10_000, pieceId: Long? = null, eventId: Long? = null): Long {
        val intonation = IntonationConfig()
        val samples = List(40) { SessionSample(69, 1.0) }
        val analysis = SessionAnalyzer.analyze(samples, intonation)
        return sessions.save(
            NewSession(
                startedAtEpochMs = 1_000, durationMs = durationMs, config = intonation, samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, intonation), audioPath = audioName, pieceId = pieceId,
                eventId = eventId,
            ),
        )
    }

    private val backings = com.violinjourney.app.core.domain.backing.FakeBackingRepository()
    private val backingPcm = object : com.violinjourney.app.core.audio.backing.BackingPcm {
        override fun cached(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): File? = null
        override fun prepare(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): File? = null
        override fun deleteOrphans(keptFiles: Set<String>) = Unit
    }

    private fun TestScope.share(renderer: SoundRenderer, analytics: Analytics = NoOpAnalytics()): Pair<ShareViewModel, MutableList<ShareEffect>> {
        val viewModel = ShareViewModel(
            sessions, repertoire, sound, audioFiles, files, renderer, texts, speed, { testScheduler.currentTime }, config, videoFiles, backings, backingPcm, analytics,
            events = events, eventWords = eventWords, io = StandardTestDispatcher(testScheduler),
        )
        val effects = mutableListOf<ShareEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        return viewModel to effects
    }

    @Test
    fun `without processing there is nothing to choose - the original goes straight to the system sheet`() = runTest {
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(recording())
        runCurrent()
        assertNull(viewModel.sheet.value)
        val sent = effects.single() as ShareEffect.Send
        assertEquals("Сессия · 18 сентября.m4a", sent.file.name)
        assertEquals("original sound", sent.file.readText())
        assertNull("no text was asked for — there was no sheet to ask on", sent.text)
    }

    @Test
    fun `with processing the sheet offers what is heard in the app first, names the file and what goes along`() = runTest {
        sound.setDefault(hall)
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт: соль мажор"), nowEpochMs = 1)
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(recording(pieceId = pieceId))
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals(ShareVariant.PROCESSED, sheet.variant)
        assertTrue(sheet.withText && !sheet.busy)
        assertEquals("a colon is no part of a file name", "Менуэт соль мажор · 18 сентября.m4a", sheet.info.fileName)
        assertEquals(SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), sheet.info.caption)
        assertEquals("ten seconds and the tail of the hall at 128 kbps", (11.8 * 16_000).toLong(), sheet.info.processedBytes)
        assertEquals(audio.length(), sheet.info.originalBytes)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a recording of an event is named by it - its file and what goes along`() = runTest {
        events.recordEvents.value = mapOf(5L to SessionEvent(5, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null))
        sound.setDefault(hall)
        val (viewModel, _) = share(Renderer(tookMs = 100))
        viewModel.start(recording(eventId = 5))
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals("Осенний концерт · 24 октября.m4a", sheet.info.fileName)
        assertEquals("Осенний концерт · ${sessions.sessions.value.single().scorePercent} % · 18 сентября", sheet.info.message)
    }

    @Test
    fun `a sound brought in from a file goes as it came, under its own extension, and processed as m4a`() = runTest {
        // plan D48: «Как записано» of `<uuid>.sound.mp3` is an mp3, not an m4a of the same bytes
        audio = folder.newFile("a1.sound.mp3").apply { writeText("original sound") }
        events.recordEvents.value = mapOf(5L to SessionEvent(5, "", LocalDate(2026, 9, 21), KindRef.BuiltIn(BuiltInKind.LESSON), null))
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(recording(audioName = "a1.sound.mp3", eventId = 5))
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals("Урок · 24 октября.m4a", sheet.info.fileNameOf(ShareVariant.PROCESSED))
        assertEquals("Урок · 24 октября.mp3", sheet.info.fileNameOf(ShareVariant.ORIGINAL))

        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL))
        viewModel.onIntent(ShareIntent.TextToggled(withText = false))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(1_000)
        runCurrent()
        val sent = effects.single() as ShareEffect.Send
        assertEquals("Урок · 24 октября.mp3", sent.file.name)
        assertEquals("original sound", sent.file.readText())
        assertEquals("the receivers are told it is an mp3", "audio/mpeg", sent.type)
    }

    @Test
    fun `a sound from a file of a kind not known here goes as sound all the same`() = runTest {
        // review of stage 98a: the name sent is made of the title, «Урок · 24 октября.webm»; the type is the recording's — no picture, sound
        audio = folder.newFile("a1.sound.webm").apply { writeText("original sound") }
        events.recordEvents.value = mapOf(5L to SessionEvent(5, "", LocalDate(2026, 9, 21), KindRef.BuiltIn(BuiltInKind.LESSON), null))
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(recording(audioName = "a1.sound.webm", eventId = 5))
        runCurrent()
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(1_000)
        runCurrent()
        val original = effects.single() as ShareEffect.Send
        assertEquals("Урок · 24 октября.webm", original.file.name)
        assertEquals("any sound, never a video", "audio/*", original.type)

        effects.clear()
        viewModel.start(recording(audioName = "a1.sound.webm", eventId = 5))
        runCurrent()
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.PROCESSED))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("what is made here is an m4a", "audio/mp4", (effects.single() as ShareEffect.Send).type)
    }

    @Test
    fun `a short preparation shows no progress screen - the button says it is busy, then the file goes`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 150))
        viewModel.start(recording())
        runCurrent()
        viewModel.onIntent(ShareIntent.TextToggled(withText = false))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        assertTrue((viewModel.sheet.value as ShareSheet.Choose).busy)
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL))
        assertEquals("a busy sheet is not to be changed", ShareVariant.PROCESSED, (viewModel.sheet.value as ShareSheet.Choose).variant)

        advanceTimeBy(200)
        runCurrent()
        assertNull(viewModel.sheet.value)
        val sent = effects.single() as ShareEffect.Send
        assertEquals("sound", sent.file.readText())
        assertNull(sent.text)
    }

    @Test
    fun `a long preparation shows its progress and what is left, and stays long enough to be read`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 900))
        viewModel.start(recording(durationMs = 60 * 60_000L)) // an hour: the estimate alone calls for the progress screen
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        assertEquals(0, (viewModel.sheet.value as ShareSheet.Preparing).percent)

        advanceTimeBy(460)
        val midway = viewModel.sheet.value as ShareSheet.Preparing
        // steps of the render come every 90 ms, the screen takes one in 100 ms: not every step is shown
        assertTrue("shows ${midway.percent} %", midway.percent in 40..50)
        assertEquals(1, midway.remainingSec)

        advanceTimeBy(450) // rendered at 900 ms — but a screen that was shown is not snatched away
        runCurrent()
        assertEquals(100, (viewModel.sheet.value as ShareSheet.Preparing).percent)
        assertTrue(effects.isEmpty())
        advanceTimeBy(400)
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertEquals("Сессия · ${sessions.sessions.value.first().scorePercent} % · 18 сентября", (effects.single() as ShareEffect.Send).text)
    }

    @Test
    fun `a preparation that drags on against the estimate gets its progress screen after all`() = runTest {
        sound.setDefault(hall)
        val (viewModel, _) = share(Renderer(tookMs = 3_000))
        viewModel.start(recording(durationMs = 5_000))
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(600)
        assertTrue(viewModel.sheet.value is ShareSheet.Choose)
        advanceTimeBy(200)
        assertTrue(viewModel.sheet.value is ShareSheet.Preparing)
    }

    @Test
    fun `cancel stops at once, leaves no file and returns to the choice`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 5_000)
        val (viewModel, effects) = share(renderer)
        viewModel.start(recording(durationMs = 60 * 60_000L))
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(1_000)
        viewModel.onIntent(ShareIntent.CancelClicked)
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertFalse(sheet.busy)
        advanceTimeBy(10_000)
        assertTrue(effects.isEmpty())
        assertTrue(File(folder.root, "share").walkTopDown().none { it.isFile })
    }

    @Test
    fun `a failure is a line in the sheet with two ways out - again, or the original`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 100, fails = true)
        val (viewModel, effects) = share(renderer)
        viewModel.start(recording())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)

        viewModel.onIntent(ShareIntent.RetryClicked)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        assertEquals(2, renderer.renders)

        viewModel.onIntent(ShareIntent.SendOriginalClicked)
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertEquals("original sound", (effects.single() as ShareEffect.Send).file.readText())
    }

    @Test
    fun `a render that throws is a failure in the sheet too - the app does not fall, the half file goes, the error is counted`() = runTest {
        sound.setDefault(hall)
        val analytics = FakeAnalytics()
        val renderer = Renderer(tookMs = 100, throws = IllegalStateException("ExtAudioFileCreateWithURL failed: -54"))
        val (viewModel, effects) = share(renderer, analytics)
        viewModel.start(recording())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(200)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        assertTrue("what was half written is gone", File(folder.root, "share").walkTopDown().none { it.isFile })
        assertEquals(listOf(ErrorGroup.MEDIA to "a file to share could not be made"), analytics.errors)

        viewModel.onIntent(ShareIntent.SendOriginalClicked)
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertEquals("original sound", (effects.single() as ShareEffect.Send).file.readText())
    }

    @Test
    fun `the same sound is not rendered twice, other settings are`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        val id = recording()
        repeat(2) {
            viewModel.start(id)
            runCurrent()
            viewModel.onIntent(ShareIntent.ContinueClicked)
            advanceTimeBy(300)
            runCurrent()
        }
        assertEquals(1, renderer.renders)
        assertEquals(2, effects.size)
        val target = (effects.first() as ShareEffect.Send).file
        assertEquals("the file made long ago is handed over again: the sweep must spare it", listOf(target, target), files.handedOver)

        sound.setOwn(id, SoundPresets.settingsOf(BuiltInPreset.WARM, config))
        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, renderer.renders)
    }

    @Test
    fun `renders measure the device, and a silent recording is not shared`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 5_900))
        viewModel.start(recording(audioName = null))
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertTrue(effects.isEmpty())

        viewModel.start(recording(durationMs = 10_000))
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals("5.9 s for 11.8 s of sound", 0.5, speed.factor, 0.01)
        assertEquals(1, effects.size)
    }

    @Test
    fun `a swipe while the file is made does not stop it - the sheet holds and the file goes`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 900)
        val (viewModel, effects) = share(renderer)
        viewModel.start(recording(durationMs = 60 * 60_000L)) // an hour: the progress at once
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        assertTrue(viewModel.sheet.value is ShareSheet.Preparing)
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        assertTrue("the sheet holds: a swipe is no «Отмена»", viewModel.sheet.value is ShareSheet.Preparing)

        advanceTimeBy(2_000)
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertEquals("sound", (effects.single() as ShareEffect.Send).file.readText())
        assertEquals(1, renderer.renders)
    }

    @Test
    fun `a swipe while «Готовим…» is on the button does not stop it either`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 300))
        viewModel.start(recording())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(100)
        assertTrue((viewModel.sheet.value as ShareSheet.Choose).busy)
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        assertTrue("still «Готовим…»", (viewModel.sheet.value as ShareSheet.Choose).busy)

        advanceTimeBy(500)
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertEquals(1, effects.size)
    }

    @Test
    fun `a swipe of the choice and of a failure only hides the sheet and sends nothing`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 100, fails = true)
        val (viewModel, effects) = share(renderer)
        val id = recording()
        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        assertNull(viewModel.sheet.value)
        advanceTimeBy(1_000)
        assertTrue(effects.isEmpty())
        assertEquals(0, renderer.renders)

        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        assertNull(viewModel.sheet.value)
        advanceTimeBy(1_000)
        assertTrue("nothing goes on its own after a failure hidden", effects.isEmpty())
        assertEquals(1, renderer.renders)
    }

    @Test
    fun `the file being made says which variant it is - the mix, and a video with its processed sound`() = runTest {
        val underId = recording(durationMs = 60 * 60_000L)
        val backingId = backings.add(backings.backing())
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(
                underId, backingId, 200, 200, -6f, 10_000, com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, null,
            ),
        )
        val (viewModel, _) = share(Renderer(tookMs = 5_000))
        viewModel.start(underId)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        val mix = viewModel.sheet.value as ShareSheet.Preparing
        assertEquals(ShareVariant.BACKING, mix.variant)
        assertEquals(".m4a", mix.info.extensionOf(mix.variant))
        // two steps of the render on (one each 500 ms): the step moves the percent and keeps the variant chosen
        advanceTimeBy(1_000)
        val stepped = viewModel.sheet.value as ShareSheet.Preparing
        assertTrue("the render has stepped: ${stepped.percent} %", stepped.percent > 0)
        assertEquals(ShareVariant.BACKING, stepped.variant)
        viewModel.onIntent(ShareIntent.CancelClicked)
        runCurrent()
        advanceTimeBy(10_000)

        sound.setDefault(hall)
        val video = videoTake()
        sessions.sessions.value = sessions.sessions.value.map { if (it.id == video) it.copy(durationMs = 60 * 60_000L) else it }
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        viewModel.start(video)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        val picture = viewModel.sheet.value as ShareSheet.Preparing
        assertEquals(ShareVariant.PROCESSED, picture.variant)
        assertEquals(".mp4", picture.info.extensionOf(picture.variant))
    }

    private companion object {
        const val STEPS = 10
    }

    /** A video take as the app keeps one: its sound is the track of the video itself — one file, one name (docs/notes/video.md). */
    private suspend fun videoTake(name: String = "take.mp4"): Long {
        audio = folder.newFile(name).apply { writeText("original video") }
        val id = recording(audioName = name, pieceId = repertoire.add(com.violinjourney.app.core.domain.repertoire.PieceDraft(title = "Менуэт"), nowEpochMs = 1))
        sessions.sessions.value = sessions.sessions.value.map { if (it.id == id) it.copy(videoPath = name) else it }
        return id
    }

    @Test
    fun `an iPhone video is sent as shot in its own container and the rest as mp4`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(videoTake("take.mov"))
        runCurrent()
        val info = (viewModel.sheet.value as ShareSheet.Choose).info
        assertEquals("Менуэт · 18 сентября.mov", info.fileNameOf(ShareVariant.ORIGINAL))
        assertEquals("what is made here is an mp4", "Менуэт · 18 сентября.mp4", info.fileNameOf(ShareVariant.PROCESSED))
        assertEquals("Менуэт · 18 сентября.m4a", info.fileNameOf(ShareVariant.SOUND))

        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        assertEquals("Менуэт · 18 сентября.mov", (effects.single() as ShareEffect.Send).file.name)
        assertEquals("a QuickTime movie, told so", "video/quicktime", (effects.single() as ShareEffect.Send).type)
    }

    @Test
    fun `a video take offers three files - what is heard with the picture, the video as shot, the sound alone`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(videoTake())
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals(ShareVariant.PROCESSED, sheet.variant)
        assertEquals("Менуэт · 18 сентября.mp4", sheet.info.fileNameOf(ShareVariant.PROCESSED))
        assertEquals("Менуэт · 18 сентября.mp4", sheet.info.fileNameOf(ShareVariant.ORIGINAL))
        assertEquals("Менуэт · 18 сентября.m4a", sheet.info.fileNameOf(ShareVariant.SOUND))
        assertEquals(1080, sheet.info.resolution)
        assertEquals("a processed video weighs what its picture does", audio.length(), sheet.info.bytesOf(ShareVariant.PROCESSED))

        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1 to 0, renderer.videoRenders to renderer.renders)
        assertEquals("Менуэт · 18 сентября.mp4", (effects.single() as ShareEffect.Send).file.name)
        assertEquals("video/mp4", (effects.single() as ShareEffect.Send).type)
    }

    @Test
    fun `the sound alone of a video take is an m4a like any recording's`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(videoTake())
        runCurrent()
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.SOUND))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0 to 1, renderer.videoRenders to renderer.renders)
        assertEquals("Менуэт · 18 сентября.m4a", (effects.single() as ShareEffect.Send).file.name)
        assertEquals("the sound of a video take is sound", "audio/mp4", (effects.single() as ShareEffect.Send).type)
    }

    @Test
    fun `a video take without processing still shows its sheet - the video as it is, or its sound`() = runTest {
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(videoTake())
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals(ShareVariant.ORIGINAL, sheet.variant)
        assertTrue(!sheet.info.processed)
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.PROCESSED))
        assertEquals("there is no processing to send", ShareVariant.ORIGINAL, (viewModel.sheet.value as ShareSheet.Choose).variant)

        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        assertEquals(0 to 0, renderer.videoRenders to renderer.renders)
        assertEquals("Менуэт · 18 сентября.mp4", (effects.single() as ShareEffect.Send).file.name)
    }

    @Test
    fun `a sound recording is never offered its sound alone`() = runTest {
        sound.setDefault(hall)
        val (viewModel, _) = share(Renderer(tookMs = 100))
        viewModel.start(recording())
        runCurrent()
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.SOUND))
        assertEquals(ShareVariant.PROCESSED, (viewModel.sheet.value as ShareSheet.Choose).variant)
    }

    @Test
    fun `a video that could not be prepared goes as it was shot`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 100, fails = true))
        viewModel.start(videoTake())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        viewModel.onIntent(ShareIntent.SendOriginalClicked)
        runCurrent()
        assertEquals("Менуэт · 18 сентября.mp4", (effects.single() as ShareEffect.Send).file.name)
    }

    @Test
    fun `a take under a backing offers the mix first even without processing, and renders it with the take's shift`() = runTest {
        val sessionId = recording()
        val backingId = backings.add(backings.backing())
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(
                sessionId, backingId, offsetMs = 215, recordedOffsetMs = 200, gainDb = -6f, playedMs = 10_000,
                output = com.violinjourney.app.core.domain.backing.BackingOutput.BLUETOOTH, deviceName = "Buds",
            ),
        )
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(sessionId)
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals("the sheet is not skipped: there is the mix to choose", ShareVariant.BACKING, sheet.variant)
        assertTrue(sheet.info.backing)
        assertEquals("ten seconds at 192 kbps, no hall", 10L * 24_000, sheet.info.bytesOf(ShareVariant.BACKING))

        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, renderer.backingRenders)
        assertEquals(0, renderer.renders)
        assertEquals(215, renderer.backingOffset)
        assertEquals("Сессия · 18 сентября.m4a", (effects.single() as ShareEffect.Send).file.name)
    }

    @Test
    fun `a take under a backing can still go as recorded`() = runTest {
        val sessionId = recording()
        val backingId = backings.add(backings.backing())
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(
                sessionId, backingId, 200, 200, -6f, 10_000, com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, null,
            ),
        )
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(sessionId)
        runCurrent()
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.ORIGINAL))
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, renderer.backingRenders)
        assertEquals("original sound", (effects.single() as ShareEffect.Send).file.readText())
    }

    @Test
    fun `a cancel while the render cannot stop yet - no late progress, and the next render waits for it`() = runTest {
        sound.setDefault(hall)
        val renderer = Renderer(tookMs = 1_000, stuckMs = 3_000) // stuck till 3000, deaf to a cancel
        val (viewModel, effects) = share(renderer)
        val id = recording(durationMs = 60 * 60_000L)
        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(100)
        assertTrue(viewModel.sheet.value is ShareSheet.Preparing)
        viewModel.onIntent(ShareIntent.CancelClicked)
        runCurrent()
        assertFalse((viewModel.sheet.value as ShareSheet.Choose).busy)

        // again at once (t = 100): it waits under «Готовим…» — the cancelled work's late progress screen (due at 700) is gone
        // with it — and after 700 ms of its own wait shows its progress and «Отмена»
        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        advanceTimeBy(650)
        assertTrue("no progress screen of the cancelled work", (viewModel.sheet.value as ShareSheet.Choose).busy)
        advanceTimeBy(100)
        assertEquals(0, (viewModel.sheet.value as ShareSheet.Preparing).percent)
        viewModel.onIntent(ShareIntent.CancelClicked)
        runCurrent()
        assertFalse((viewModel.sheet.value as ShareSheet.Choose).busy)
        advanceTimeBy(1_000)
        assertFalse("no progress screen comes back by itself", (viewModel.sheet.value as ShareSheet.Choose).busy)

        // closed and opened again while the old render still holds on (t = 1850): the sheet is there at once, the render waits
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        viewModel.start(id)
        runCurrent()
        assertFalse("the sheet opens at once", (viewModel.sheet.value as ShareSheet.Choose).busy)
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals("never two renders into one .part", 1, renderer.mostAtOnce)
        val sent = effects.single() as ShareEffect.Send
        assertEquals("sound", sent.file.readText())
    }

    @Test
    fun `a skipped sheet whose copy failed retries the original - never a render or another recording`() = runTest {
        val first = recording() // with a processing of its own; the default leaves the sound alone
        sound.setOwn(first, hall)
        val renderer = Renderer(tookMs = 100)
        val (viewModel, effects) = share(renderer)
        viewModel.start(first)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, renderer.renders)

        val second = recording(pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1))
        files.originalFails = true
        viewModel.start(second)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        files.originalFails = false
        viewModel.onIntent(ShareIntent.RetryClicked)
        runCurrent()
        assertNull(viewModel.sheet.value)
        val sent = effects.last() as ShareEffect.Send
        assertEquals("Менуэт · 18 сентября.m4a", sent.file.name)
        assertEquals("original sound", sent.file.readText())
        assertNull("no text was asked for — there was no sheet to ask on", sent.text)
        assertEquals(1, renderer.renders)
    }

    @Test
    fun `a double tap on continue sends once`() = runTest {
        files.originalTakesMs = 100
        val (viewModel, effects) = share(Renderer(tookMs = 100))
        viewModel.start(videoTake())
        runCurrent()
        assertEquals(ShareVariant.ORIGINAL, (viewModel.sheet.value as ShareSheet.Choose).variant)
        viewModel.onIntent(ShareIntent.ContinueClicked)
        assertTrue("«Готовим…» for the moment of the copy", (viewModel.sheet.value as ShareSheet.Choose).busy)
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, effects.size)
        assertNull(viewModel.sheet.value)
    }

    // «Видео с нотами» (spec 3.37, 5.30)

    /** Makes the video with the notes in [tookMs] of virtual time, telling its progress; fails when told to. */
    private class NotesRenderer(var tookMs: Long = 400, var fails: Boolean = false) : NotesVideoRenderer {
        var renders = 0
        var soundHeard: String? = null
        var overlay: NotesOverlay? = null
        var words: OverlayWords? = null

        override suspend fun render(picture: File, sound: File, overlay: NotesOverlay, words: OverlayWords, target: File, onProgress: (Float) -> Unit): Boolean {
            renders++
            // read now: the sound made for it is deleted once the video is made
            soundHeard = sound.readText()
            this.overlay = overlay
            this.words = words
            repeat(STEPS) { step ->
                delay(tookMs / STEPS)
                onProgress((step + 1f) / STEPS)
            }
            if (fails) return false
            target.parentFile?.mkdirs()
            target.writeText("video with notes")
            return true
        }
    }

    /** The language the words of the picture are «read» in: a JVM test has no resources. */
    private var language = "ru"

    private fun TestScope.shareWithNotes(renderer: SoundRenderer, notes: NotesRenderer): Pair<ShareViewModel, MutableList<ShareEffect>> {
        val viewModel = ShareViewModel(
            sessions, repertoire, sound, audioFiles, files, renderer, texts, speed, { testScheduler.currentTime }, config, videoFiles, backings, backingPcm,
            NoOpAnalytics(), events = events, eventWords = eventWords, notesRenderer = notes, notesSpeed = RenderSpeed(NotesVideoConfig().renderSpeedStart),
            overlayWords = { overlay ->
                OverlayWords(
                    badge = "в строе ${overlay.scorePercent}% ($language)", toleranceLine = language, bestNote = language, drift = language,
                    driftCents = null, driftNone = language, previousTake = language, previousScore = null,
                )
            },
            io = StandardTestDispatcher(testScheduler),
        )
        val effects = mutableListOf<ShareEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        return viewModel to effects
    }

    @Test
    fun `a video take offers the notes first and chosen - with the sound heard in the app`() = runTest {
        sound.setDefault(hall)
        val notes = NotesRenderer()
        val (viewModel, effects) = shareWithNotes(Renderer(tookMs = 100), notes)
        viewModel.start(videoTake())
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals(ShareVariant.NOTES, sheet.variant)
        val offer = sheet.info.notes!!
        assertEquals(NotesSound.PROCESSED, offer.sound)
        assertFalse(offer.tooLong)
        assertEquals("1920 × 1080 stays 1080p", 1_080, offer.resolution)
        assertEquals(".mp4", sheet.info.extensionOf(ShareVariant.NOTES))

        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, notes.renders)
        assertEquals("the processed sound, made beside the file", "sound", notes.soundHeard)
        assertEquals("the summary is titled as the file", "Менуэт · 18 сентября", notes.overlay!!.title)
        val sent = effects.single() as ShareEffect.Send
        assertEquals("Менуэт · 18 сентября.mp4", sent.file.name)
        assertEquals("video with notes", sent.file.readText())
        assertEquals("video/mp4", sent.type)
        assertTrue("nothing is left beside the file: ${sent.file.parentFile!!.list()!!.toList()}", sent.file.parentFile!!.list()!!.all { it == sent.file.name })
    }

    @Test
    fun `without processing the notes carry the sound of the video itself`() = runTest {
        val renderer = Renderer(tookMs = 100)
        val notes = NotesRenderer()
        val (viewModel, _) = shareWithNotes(renderer, notes)
        viewModel.start(videoTake())
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals(ShareVariant.NOTES, sheet.variant)
        assertEquals(NotesSound.ORIGINAL, sheet.info.notes!!.sound)
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("no sound is made", 0, renderer.renders)
        assertEquals("the track of the video as it is", "original video", notes.soundHeard)
    }

    @Test
    fun `under a backing the notes carry the mix`() = runTest {
        val id = videoTake()
        val backingId = backings.add(backings.backing())
        backings.saveTake(
            com.violinjourney.app.core.domain.backing.TakeBacking(
                id, backingId, 215, 200, -6f, 10_000, com.violinjourney.app.core.domain.backing.BackingOutput.WIRED, null,
            ),
        )
        val renderer = Renderer(tookMs = 100)
        val notes = NotesRenderer()
        val (viewModel, _) = shareWithNotes(renderer, notes)
        viewModel.start(id)
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertEquals("the notes come before the mix", ShareVariant.NOTES, sheet.variant)
        assertEquals(NotesSound.BACKING, sheet.info.notes!!.sound)
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, renderer.backingRenders)
        assertEquals(215, renderer.backingOffset)
        assertEquals(1, notes.renders)
    }

    @Test
    fun `a recording longer than fifteen minutes cannot have the notes`() = runTest {
        sound.setDefault(hall)
        val (viewModel, _) = shareWithNotes(Renderer(tookMs = 100), NotesRenderer())
        val id = videoTake()
        sessions.sessions.value = sessions.sessions.value.map { if (it.id == id) it.copy(durationMs = 15 * 60_000L + 1) else it }
        viewModel.start(id)
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertTrue(sheet.info.notes!!.tooLong)
        assertEquals(15, sheet.info.notes!!.limitMinutes)
        assertEquals("the first variant without the notes is chosen", ShareVariant.PROCESSED, sheet.variant)
        viewModel.onIntent(ShareIntent.VariantSelected(ShareVariant.NOTES))
        assertEquals("the dimmed row cannot be chosen", ShareVariant.PROCESSED, (viewModel.sheet.value as ShareSheet.Choose).variant)
    }

    @Test
    fun `fifteen minutes exactly still has the notes`() = runTest {
        val (viewModel, _) = shareWithNotes(Renderer(tookMs = 100), NotesRenderer())
        val id = videoTake()
        sessions.sessions.value = sessions.sessions.value.map { if (it.id == id) it.copy(durationMs = 15 * 60_000L) else it }
        viewModel.start(id)
        runCurrent()
        assertEquals(ShareVariant.NOTES, (viewModel.sheet.value as ShareSheet.Choose).variant)
    }

    @Test
    fun `the same notes are not made twice - but in another language they are`() = runTest {
        val notes = NotesRenderer()
        val (viewModel, effects) = shareWithNotes(Renderer(tookMs = 100), notes)
        val id = videoTake()
        repeat(2) {
            viewModel.start(id)
            runCurrent()
            viewModel.onIntent(ShareIntent.ContinueClicked)
            advanceTimeBy(5_000)
            runCurrent()
        }
        assertEquals("the second share takes the file made", 1, notes.renders)
        assertEquals(2, effects.size)

        language = "en"
        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("other words on the picture: another file", 2, notes.renders)
    }

    @Test
    fun `notes that could not be made fail the sheet - the video can still go as shot`() = runTest {
        val notes = NotesRenderer(fails = true)
        val (viewModel, effects) = shareWithNotes(Renderer(tookMs = 100), notes)
        viewModel.start(videoTake())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(viewModel.sheet.value is ShareSheet.Failed)
        assertTrue(effects.isEmpty())
        viewModel.onIntent(ShareIntent.SendOriginalClicked)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("original video", (effects.single() as ShareEffect.Send).file.readText())
    }

    @Test
    fun `the notes show their progress by their own measure of speed`() = runTest {
        val notes = NotesRenderer(tookMs = 2_000)
        val (viewModel, _) = shareWithNotes(Renderer(tookMs = 100), notes)
        viewModel.start(videoTake())
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        runCurrent()
        // ten seconds of video at the start factor 0.6: six seconds expected — the progress screen at once
        assertTrue(viewModel.sheet.value is ShareSheet.Preparing)
        advanceTimeBy(1_100)
        val midway = viewModel.sheet.value as ShareSheet.Preparing
        assertEquals(ShareVariant.NOTES, midway.variant)
        assertTrue("past the sound (10 %) and on its way: ${midway.percent} %", midway.percent in 11..99)
    }

    @Test
    fun `a sound recording has no notes to offer`() = runTest {
        sound.setDefault(hall)
        val (viewModel, _) = shareWithNotes(Renderer(tookMs = 100), NotesRenderer())
        viewModel.start(recording())
        runCurrent()
        val sheet = viewModel.sheet.value as ShareSheet.Choose
        assertNull(sheet.info.notes)
        assertEquals(ShareVariant.PROCESSED, sheet.variant)
    }
}
