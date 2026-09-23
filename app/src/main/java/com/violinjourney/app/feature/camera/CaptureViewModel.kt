package com.violinjourney.app.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.RecordingRate
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOffset
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoMuxer
import com.violinjourney.app.core.settings.IntonationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Снять под минусовку» (spec 3.32): the app's camera takes the picture, the take's chain the sound and the backing —
 * started together, the camera the moment the sound begins; when the take is saved, picture and sound become one
 * `.mp4`, the picture moved by how much later it began. The take is then an ordinary video take (spec 3.19), analysed
 * already: the chain heard every note while it recorded.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val takes: TakePipeline,
    private val configSource: IntonationConfigSource,
    private val repertoire: RepertoireRepository,
    private val backings: BackingRepository,
    private val backingPcm: BackingPcm,
    private val routes: AudioRoutes,
    private val videos: VideoFiles,
    private val backingConfig: BackingConfig,
    private val cameraFactory: ShotCameraFactory,
    private val recordingRate: RecordingRate,
    @IoDispatcher private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "piece id is required" }

    /** One camera for the screen's life: the route binds it to what is on screen. */
    val camera: ShotCamera = cameraFactory.create()

    private val mutableState = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects: Flow<CaptureEffect> = effectChannel.receiveAsFlow()

    private val listening = MutableStateFlow(false)
    private var picture: File? = null
    private var soundStartNanos: Long? = null

    private val hook = object : TakePipeline.VideoHook {
        override fun onRecordingStarted(recordStartNanos: Long?) {
            // the chain's thread: the camera is the main thread's
            soundStartNanos = recordStartNanos ?: System.nanoTime()
            viewModelScope.launch(Dispatchers.Main.immediate) {
                val file = videos.newCameraFile()
                picture = file
                camera.startRecording(file)
            }
        }

        override suspend fun onRecordingFinished(audio: File, recordStartNanos: Long?): String? {
            val shot = picture ?: return null
            picture = null
            mutableState.update { it.copy(saving = true) }
            val kept = withContext(Dispatchers.Main.immediate) { camera.stopRecording() }
            val made = if (kept) {
                val pictureStart = camera.startNanos ?: soundStartNanos ?: 0L
                val shift = VideoMuxer.shiftUs(pictureStart, recordStartNanos ?: soundStartNanos ?: pictureStart)
                withContext(io) {
                    val muxed = videos.newCameraFile()
                    val whole = VideoMuxer.mux(shot, audio, muxed, shift)
                    shot.delete()
                    if (!whole) return@withContext null
                    videos.adopt(muxed)?.also { videos.makeThumb(it) }?.name
                }
            } else {
                shot.delete()
                null
            }
            if (made == null) effectChannel.send(CaptureEffect.ShowVideoFailed)
            mutableState.update { it.copy(saving = false) }
            return made
        }

        override suspend fun onRecordingDiscarded() {
            val shot = picture ?: return
            picture = null
            withContext(Dispatchers.Main.immediate) { camera.stopRecording() }
            shot.delete()
        }
    }

    init {
        takes.videoHook = hook
        viewModelScope.launch { takes.watchPractice() }
        viewModelScope.launch {
            val title = repertoire.piece(pieceId)?.title.orEmpty()
            val minutes = (videos.freeBytes() / BYTES_PER_MINUTE).toInt()
            mutableState.update { it.copy(title = title, spaceMinutes = minutes.takeIf { m -> m < LOW_SPACE_MINUTES }) }
        }
        viewModelScope.launch {
            combine(backings.pieceBackings, backings.backings, routes.changes) { rows, all, route ->
                val row = rows.firstOrNull { it.pieceId == pieceId }
                val backing = row?.takeIf { it.enabled }?.let { r -> all.firstOrNull { it.id == r.backingId } }
                backing to route.output.isHeadphones
            }.collect { (backing, headphones) ->
                backing?.let(::prepare)
                mutableState.update {
                    it.copy(backingTitle = backing?.title, backingDurationMs = backing?.durationMs ?: 0, underBacking = backing != null, noHeadphones = !headphones)
                }
            }
        }
        viewModelScope.launch { takes.backingPosition.collect { played -> mutableState.update { it.copy(backingPlayedMs = played) } } }
        viewModelScope.launch {
            takes.events.collect { event ->
                when (event) {
                    // saved: back to the piece, where the take tops the list (spec 3.19)
                    is TakePipeline.Event.Saved -> effectChannel.send(CaptureEffect.Close)
                    TakePipeline.Event.NoNotes -> effectChannel.send(CaptureEffect.ShowNoNotes)
                }
            }
        }
        viewModelScope.launch { chain().collect {} }
    }

    /** The chain runs only while a take does: the screen has no business holding the microphone otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun chain(): Flow<Unit> = combine(listening, configSource.config, ::Pair).flatMapLatest { (wanted, intonation) ->
        if (!wanted) {
            emptyFlow()
        } else {
            // what the chain shows here is only whether the microphone can be had: blind, like any take (spec 3.15)
            takes.run(config = intonation, pieceId = pieceId, targetMode = { TargetMode.Chromatic }, unavailable = true) { _, _ -> false }.map { output ->
                if (!takes.recordingRequested.value && output.recording == null) listening.value = false
                mutableState.update {
                    it.copy(
                        recording = output.recording != null || takes.recordingRequested.value,
                        elapsedSeconds = (output.recording?.elapsedMs ?: 0) / MS_PER_SECOND,
                        micUnavailable = output.shown,
                    )
                }
            }.onCompletion {
                listening.value = false
                mutableState.update { it.copy(recording = false) }
            }
        }
    }

    fun onIntent(intent: CaptureIntent) {
        when (intent) {
            is CaptureIntent.PermissionsChanged -> mutableState.update { it.copy(cameraPermission = intent.camera, micPermission = intent.mic) }
            CaptureIntent.RecordClicked -> when {
                takes.recordingRequested.value -> takes.recordingRequested.value = false
                state.value.cameraPermission != true || state.value.micPermission != true -> effectChannel.trySend(CaptureEffect.RequestPermissions)
                state.value.canRecord -> viewModelScope.launch { start() }
            }
            CaptureIntent.SwitchCameraClicked -> if (!state.value.recording) mutableState.update { it.copy(front = !it.front, cameraFailed = false) }
            is CaptureIntent.FocusAt -> camera.focus(intent.x, intent.y)
            CaptureIntent.CloseClicked -> {
                takes.recordingRequested.value = false
                effectChannel.trySend(CaptureEffect.Close)
            }
            CaptureIntent.CameraBindFailed -> mutableState.update { it.copy(cameraFailed = true) }
        }
    }

    /** The backing made ready for the mix at the rate the take will be recorded at, before the button is pressed. */
    private fun prepare(backing: Backing) {
        if (prepared == backing.id) return
        prepared = backing.id
        viewModelScope.launch {
            mutableState.update { it.copy(preparing = true) }
            withContext(io) { backingPcm.cached(backing, recordingRate.likelyHz()) ?: backingPcm.prepare(backing, recordingRate.likelyHz()) }
            mutableState.update { it.copy(preparing = false) }
        }
    }

    private var prepared: Long? = null

    /** The take under the backing if the chip is on — as on the piece screen (spec 3.32) — or a plain video. */
    private suspend fun start() {
        val row = backings.pieceBackings.first().firstOrNull { it.pieceId == pieceId }
        val backing = row?.takeIf { it.enabled }?.let { backings.backing(it.backingId) }
        val route = routes.current()
        takes.backingPlan = backing?.let {
            TakePipeline.BackingPlan(
                backing = it,
                pcm = { rate -> backingPcm.cached(it, rate) ?: backingPcm.prepare(it, rate) },
                route = route,
                latencyMs = BackingOffset.latencyMs(route, backingConfig),
            )
        }
        takes.recordingRequested.value = true
        listening.value = true
    }

    override fun onCleared() {
        takes.videoHook = null
        camera.release()
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        private const val MS_PER_SECOND = 1_000L

        /** 1080p at the bit rate CameraX picks is about 16 Mbit/s: two megabytes a second (spec 5.25). */
        private const val BYTES_PER_MINUTE = 120L * 1024 * 1024
        private const val LOW_SPACE_MINUTES = 10
    }
}
