package com.example.violintuner.feature.share

import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.audio.share.SoundRenderer
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.FakeSoundRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.domain.sound.SoundSettings
import com.example.violintuner.feature.sound.SoundCaption
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    /** Takes [tookMs] of virtual time, telling its progress on the way; fails when told to. */
    private inner class Renderer(var tookMs: Long, var fails: Boolean = false) : SoundRenderer {
        var renders = 0
        override suspend fun render(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean {
            renders++
            try {
                repeat(STEPS) { step ->
                    delay(tookMs / STEPS)
                    onProgress((step + 1f) / STEPS)
                }
                if (fails) return false
                target.parentFile?.mkdirs()
                target.writeText("sound")
                return true
            } catch (e: kotlinx.coroutines.CancellationException) {
                target.delete()
                throw e
            }
        }

        var videoRenders = 0
        override suspend fun renderVideo(source: File, settings: SoundSettings, target: File, onProgress: (Float) -> Unit): Boolean {
            videoRenders++
            return render(source, settings, target, onProgress).also { renders-- }
        }
    }

    private inner class Files : ShareFiles {
        var originalFails = false
        override fun processed(audioName: String, settings: SoundSettings, fileName: String) = File(folder.root, "share/${settings.hashCode()}/$fileName")
        override suspend fun original(audio: File, fileName: String): File? =
            if (originalFails) null else File(folder.root, "share/original/$fileName").also { it.parentFile?.mkdirs(); audio.copyTo(it, overwrite = true) }
        override suspend fun deleteOlderThan(nowEpochMs: Long, maxAgeMs: Long) = Unit
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
    private val videoFiles = com.example.violintuner.core.recording.video.FakeVideoFiles()
    private val speed = RenderSpeed()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        audio = folder.newFile("take.m4a").apply { writeText("original sound") }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun recording(audioName: String? = "take.m4a", durationMs: Long = 10_000, pieceId: Long? = null): Long {
        val intonation = IntonationConfig()
        val samples = List(40) { SessionSample(69, 1.0) }
        val analysis = SessionAnalyzer.analyze(samples, intonation)
        return sessions.save(
            NewSession(
                startedAtEpochMs = 1_000, durationMs = durationMs, config = intonation, samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, intonation), audioPath = audioName, pieceId = pieceId,
            ),
        )
    }

    private fun TestScope.share(renderer: SoundRenderer): Pair<ShareViewModel, MutableList<ShareEffect>> {
        val viewModel = ShareViewModel(sessions, repertoire, sound, audioFiles, files, renderer, texts, speed, { testScheduler.currentTime }, config, videoFiles)
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

        sound.setOwn(id, SoundPresets.settingsOf(BuiltInPreset.WARM, config))
        viewModel.start(id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, renderer.renders)
    }

    @Test
    fun `renders measure the device, a silent recording is not shared, a closed sheet forgets its work`() = runTest {
        sound.setDefault(hall)
        val (viewModel, effects) = share(Renderer(tookMs = 5_900))
        viewModel.start(recording(audioName = null))
        runCurrent()
        assertNull(viewModel.sheet.value)
        assertTrue(effects.isEmpty())

        viewModel.start(recording(durationMs = 10_000))
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(1_000)
        viewModel.onIntent(ShareIntent.Dismissed)
        runCurrent()
        assertNull(viewModel.sheet.value)
        advanceTimeBy(10_000)
        assertTrue(effects.isEmpty())

        viewModel.start(sessions.sessions.value.first().id)
        runCurrent()
        viewModel.onIntent(ShareIntent.ContinueClicked)
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals("5.9 s for 11.8 s of sound", 0.5, speed.factor, 0.01)
    }

    private companion object {
        const val STEPS = 10
    }

    private suspend fun videoTake(): Long {
        val id = recording(pieceId = repertoire.add(com.example.violintuner.core.domain.repertoire.PieceDraft(title = "Менуэт"), nowEpochMs = 1))
        sessions.sessions.value = sessions.sessions.value.map { if (it.id == id) it.copy(videoPath = it.audioPath) else it }
        return id
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
}
