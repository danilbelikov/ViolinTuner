package com.violinjourney.app.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.backing.FakeBackingRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoInfo
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.time.FixedWallClock
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * «Снять под минусовку» (spec 3.32): a take shot by the app's camera is kept however the screen goes — «закрыть» works
 * like «назад», and a take still being finished when the screen is cleared gets its video all the same.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val directory = File(System.getProperty("java.io.tmpdir"), "capture-test-${System.nanoTime()}").apply { mkdirs() }
    private val sessions = FakeSessionRepository()
    private val camera = FakeCamera()
    private val muxer = FakeMux()
    private val videos = FakeVideos(directory)

    /** The camera as its contract has it: a stop that comes after [release] waits for the same end of the file. */
    private class FakeCamera : ShotCamera {
        var shot: File? = null
        var finalize = CompletableDeferred<Boolean>()
        var released = false
        var started = 0

        override fun release() {
            released = true
        }

        override fun focus(x: Float, y: Float) = Unit

        override fun startRecording(file: File) {
            file.writeText("picture")
            shot = file
            started++
        }

        override val startNanos: Long? = null

        override suspend fun stopRecording(): Boolean = if (shot == null) false else finalize.await()
    }

    /** Making the video waits at [gate], as a long one does. */
    private class FakeMux : VideoMux {
        val gate = CompletableDeferred<Unit>()

        override suspend fun mux(picture: File, sound: File, target: File, shiftUs: Long): Boolean {
            gate.await()
            target.writeText("video")
            return true
        }
    }

    private class FakeVideos(private val directory: File) : VideoFiles {
        private var count = 0

        override fun newCameraFile(): File = File(directory, "camera-${++count}.mp4")

        override fun adopt(cameraFile: File): File? = File(directory, "kept-${cameraFile.name}").takeIf(cameraFile::renameTo)

        override fun sizeOf(uri: String): Long? = null

        override fun freeBytes(): Long = Long.MAX_VALUE / 2

        override suspend fun import(uri: String): File? = null

        override fun info(file: File): VideoInfo? = null

        override fun makeThumb(file: File): Boolean = true

        override fun thumbOf(name: String): File? = null

        override fun existing(name: String): File? = File(directory, name).takeIf(File::isFile)

        override fun discard(file: File) {
            file.delete()
        }
    }

    private class FakeAudioFiles(private val directory: File) : SessionAudioFiles {
        private var count = 0

        override fun newFile(): File = File(directory, "take-${++count}.m4a").also { it.writeText("audio") }

        override fun existing(name: String): File? = File(directory, name).takeIf(File::isFile)

        override fun delete(name: String) {
            File(directory, name).delete()
        }

        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    /** A tap driven by the frames of its source, like the microphone one. */
    private class FakeAudioTap : AudioTap {
        override var state: AudioTap.State = AudioTap.State.Idle

        override fun start(file: File) {
            if (state == AudioTap.State.Idle) state = AudioTap.State.Starting
        }

        fun onFrame(tMs: Long) {
            if (state == AudioTap.State.Starting) state = AudioTap.State.Running(tMs)
        }

        override suspend fun stop(): Boolean {
            val wasRunning = state is AudioTap.State.Running
            state = AudioTap.State.Idle
            return wasRunning
        }
    }

    private val noBackingPcm = object : BackingPcm {
        override fun cached(backing: Backing, sampleRate: Int): File? = null

        override fun prepare(backing: Backing, sampleRate: Int): File? = null

        override fun deleteOrphans(keptFiles: Set<String>) = Unit
    }

    private val headphones = object : AudioRoutes {
        override fun current() = AudioRoute(BackingOutput.WIRED, "Test")

        override val changes: Flow<AudioRoute> = flowOf(current())
    }

    /** The screen's view model in a store of its own, so that it can be cleared the way the screen going clears it. */
    private fun TestScope.screen(scenario: FakeScenario = FakeScenario.IN_TUNE): Pair<CaptureViewModel, ViewModelStore> {
        val tap = FakeAudioTap()
        val delegate = FakePitchSource(scenario, timeSource = testTimeSource)
        val source = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap = tap

            override fun frames(config: IntonationConfig): Flow<PitchFrame> = delegate.frames(config).onEach { tap.onFrame(it.tMs) }
        }
        val clock = FixedWallClock(Instant.parse("2026-09-26T09:00:00Z"), TimeZone.UTC)
        val takes = TakePipeline(
            source, sessions, FakeAudioFiles(directory), FakeRunningPracticeStore(), PracticeConfig(), clock, StandardTestDispatcher(testScheduler),
        )
        val store = ViewModelStore()
        val factory = viewModelFactory {
            initializer {
                CaptureViewModel(
                    SavedStateHandle(mapOf(CaptureViewModel.ARG_PIECE_ID to PIECE_ID)), takes, SettingsConfigSource(IntonationConfig(), FakeSettingsRepository()),
                    FakeRepertoireRepository(), FakeBackingRepository(), noBackingPcm, headphones, videos, BackingConfig(), { camera },
                    { TakePipeline.DEFAULT_RATE }, muxer, StandardTestDispatcher(testScheduler),
                )
            }
        }
        val viewModel = ViewModelProvider.create(store, factory)[CaptureViewModel::class]
        viewModel.onIntent(CaptureIntent.PermissionsChanged(camera = true, mic = true))
        runCurrent()
        return viewModel to store
    }

    private fun TestScope.effectsOf(viewModel: CaptureViewModel): List<CaptureEffect> =
        mutableListOf<CaptureEffect>().also { effects -> backgroundScope.launch { viewModel.effects.collect { effects += it } } }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `a take still being finished when the screen is cleared is kept with its video`() = runTest {
        val (viewModel, store) = screen()
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(3_000)
        assertNotNull("the camera shoots", camera.shot)

        store.clear()
        runCurrent()
        assertTrue(camera.released)
        assertTrue("the take waits for the end of the picture", sessions.saved.isEmpty())

        camera.finalize.complete(true)
        runCurrent()
        muxer.gate.complete(Unit)
        runCurrent()

        assertEquals("the take is kept", 1, sessions.saved.size)
        val session = sessions.saved.single()
        assertEquals("kept-camera-2.mp4", session.videoPath)
        assertEquals(PIECE_ID, session.pieceId)
        assertFalse("the shot is in the video now", camera.shot!!.exists())
    }

    @Test
    fun `a take too short to keep leaves no picture behind when the screen is cleared`() = runTest {
        val (viewModel, store) = screen()
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(1_000)
        assertNotNull(camera.shot)

        store.clear()
        runCurrent()
        camera.finalize.complete(true)
        runCurrent()

        assertTrue(sessions.saved.isEmpty())
        assertFalse("the picture is thrown away", camera.shot!!.exists())
    }

    @Test
    fun `«закрыть» during the shot stops it, waits for the take and closes once`() = runTest {
        val (viewModel, _) = screen()
        val effects = effectsOf(viewModel)
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(3_000)

        viewModel.onIntent(CaptureIntent.CloseClicked)
        advance(50)
        assertTrue("«Собираем видео…»", viewModel.state.value.saving)
        assertTrue("the screen stays while the take is made", effects.isEmpty())
        // under «Собираем видео…» neither «закрыть» nor the button answers
        viewModel.onIntent(CaptureIntent.CloseClicked)
        viewModel.onIntent(CaptureIntent.RecordClicked)
        runCurrent()
        assertTrue(effects.isEmpty())

        camera.finalize.complete(true)
        runCurrent()
        muxer.gate.complete(Unit)
        advance(1_000)

        assertEquals(1, sessions.saved.size)
        assertNotNull(sessions.saved.single().videoPath)
        assertEquals("closed once: a second pop would leave the piece too", listOf(CaptureEffect.Close), effects)
        assertFalse("no new take began over it", viewModel.state.value.recording)
    }

    @Test
    fun `a second tap on «стоп» before the take has stopped does not keep it recording`() = runTest {
        val (viewModel, _) = screen()
        val effects = effectsOf(viewModel)
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(3_000)

        // «стоп» twice, both before the chain has seen the first: the take is still finishing, nothing starts over it
        viewModel.onIntent(CaptureIntent.RecordClicked)
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(50)
        assertTrue("the take stopped and its video is being made", viewModel.state.value.saving)

        camera.finalize.complete(true)
        runCurrent()
        muxer.gate.complete(Unit)
        advance(1_000)

        assertEquals(1, sessions.saved.size)
        assertNotNull(sessions.saved.single().videoPath)
        assertEquals(listOf(CaptureEffect.Close), effects)
        assertFalse("no new take began over it", viewModel.state.value.recording)
        assertEquals("the camera shot one take", 1, camera.started)
    }

    @Test
    fun `«закрыть» during a take without notes says so and closes`() = runTest {
        val (viewModel, _) = screen(FakeScenario.SILENCE)
        val effects = effectsOf(viewModel)
        viewModel.onIntent(CaptureIntent.RecordClicked)
        advance(3_000)
        camera.finalize.complete(true)

        viewModel.onIntent(CaptureIntent.CloseClicked)
        advance(1_000)

        assertTrue(sessions.saved.isEmpty())
        assertEquals(listOf(CaptureEffect.ShowNoNotes, CaptureEffect.Close), effects)
    }

    @Test
    fun `«закрыть» with no shot under way closes at once, and only once`() = runTest {
        val (viewModel, _) = screen()
        val effects = effectsOf(viewModel)
        viewModel.onIntent(CaptureIntent.CloseClicked)
        viewModel.onIntent(CaptureIntent.CloseClicked)
        runCurrent()
        assertEquals(listOf(CaptureEffect.Close), effects)
    }

    private companion object {
        const val PIECE_ID = 51L
    }
}
