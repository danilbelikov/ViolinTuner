package com.violinjourney.app.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.RecordingRate
import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOffset
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.recording.video.VideoShift
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.monotonicNanos
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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
open class CaptureViewModel(
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
    private val muxer: VideoMux,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "piece id is required" }

    /** One camera for the screen's life: the route binds it to what is on screen. */
    val camera: ShotCamera = cameraFactory.create()

    private val mutableState = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects: Flow<CaptureEffect> = effectChannel.receiveAsFlow()

    private val listening = MutableStateFlow(false)

    // Written on the main thread and on the chain's: the hook is called from the chain.
    @Volatile private var picture: PlatformFile? = null
    @Volatile private var soundStartNanos: Long? = null

    /**
     * The camera's start, on its way to the main thread. A take that ends before it has run waits for it: the camera it
     * starts is stopped by that take all the same, instead of shooting on until the screen goes — and CameraX refusing
     * the next take's recording.
     */
    @Volatile private var cameraStart: Job? = null

    /** «Закрыть» was tapped during the take: the screen goes as soon as the take is done with, whatever it came to. */
    private var closeWhenDone = false

    /**
     * The request under way was asked by «Разрешить доступ», not by the screen itself on entering: an answer «denied for good» to it
     * opens the settings (spec 3.36.4); one to the first request on entering does not — the line says it, and a tap opens them.
     */
    private var askedByButton = false
    private var closed = false

    /** Once: the take saved, «закрыть» and the end of a take closed by it may all come to it, and a second pop would leave the piece too. */
    private fun close() {
        if (closed) return
        closed = true
        effectChannel.trySend(CaptureEffect.Close)
    }

    private val hook = object : TakePipeline.VideoHook {
        override fun onRecordingStarted(recordStartNanos: Long?) {
            // the chain's thread: the camera is the main thread's
            soundStartNanos = recordStartNanos ?: monotonicNanos()
            cameraStart = viewModelScope.launch(Dispatchers.Main.immediate) {
                val file = videos.newCameraFile()
                picture = file
                camera.startRecording(file)
            }
        }

        override suspend fun onRecordingFinished(audio: PlatformFile, recordStartNanos: Long?): String? {
            // a camera still on its way is started first, then stopped with the take; one cancelled with the screen never started
            cameraStart?.join()
            val shot = picture ?: return null
            picture = null
            mutableState.update { it.copy(saving = true) }
            val kept = withContext(Dispatchers.Main.immediate) { camera.stopRecording() }
            val made = if (kept) {
                val pictureStart = camera.startNanos ?: soundStartNanos ?: 0L
                val shift = VideoShift.shiftUs(pictureStart, recordStartNanos ?: soundStartNanos ?: pictureStart)
                withContext(io) {
                    val muxed = videos.newCameraFile()
                    val whole = muxer.mux(shot, audio, muxed, shift)
                    shot.deleteFile()
                    if (!whole) return@withContext null
                    videos.adopt(muxed)?.also { videos.makeThumb(it) }?.fileName
                }
            } else {
                shot.deleteFile()
                null
            }
            if (made == null) effectChannel.send(CaptureEffect.ShowVideoFailed)
            mutableState.update { it.copy(saving = false) }
            return made
        }

        override suspend fun onRecordingDiscarded() {
            cameraStart?.join()
            val shot = picture ?: return
            picture = null
            withContext(Dispatchers.Main.immediate) { camera.stopRecording() }
            shot.deleteFile()
        }
    }

    // The backing's sound by (backing, rate), main thread only: being made, made once — a change of headphones does not
    // look again — and not to be made. A failure is not tried again while the screen lives (spec 5.25). Above `init`: its
    // collector reads them, and a flow that gives its first value at once would run it before a later initializer.
    private var currentBackingId: Long? = null
    private val unpacking = mutableSetOf<Pair<Long, Int>>()
    private val unpacked = mutableSetOf<Pair<Long, Int>>()
    private val unpackFailed = mutableSetOf<Pair<Long, Int>>()

    init {
        takes.videoHook = hook
        viewModelScope.launch { takes.watchPractice() }
        viewModelScope.launch {
            val title = repertoire.piece(pieceId)?.title.orEmpty()
            // off the main thread: on iOS the room is counted with what the system would free, and that takes a while
            // counted in Long: a disk of terabytes — or a fake one — is more minutes than an Int holds, and turned negative it read «мало места»
            val minutes = withContext(io) { videos.freeBytes() } / BYTES_PER_MINUTE
            mutableState.update { it.copy(title = title, spaceMinutes = minutes.takeIf { m -> m < LOW_SPACE_MINUTES }?.toInt()) }
        }
        viewModelScope.launch {
            combine(backings.pieceBackings, backings.backings, routes.changes) { rows, all, route ->
                val row = rows.firstOrNull { it.pieceId == pieceId }
                val backing = row?.takeIf { it.enabled }?.let { r -> all.firstOrNull { it.id == r.backingId } }
                Triple(backing, route.output.isHeadphones, route.deviceName)
            }.collect { (backing, headphones, name) ->
                currentBackingId = backing?.id
                backing?.let { prepare(it, recordingRate.likelyHz()) }
                mutableState.update {
                    it.copy(
                        backingTitle = backing?.title, backingDurationMs = backing?.durationMs ?: 0, underBacking = backing != null, noHeadphones = !headphones,
                        headphonesName = name?.takeIf { headphones },
                    )
                }
                showPreparation()
            }
        }
        viewModelScope.launch { takes.backingPosition.collect { played -> mutableState.update { it.copy(backingPlayedMs = played) } } }
        viewModelScope.launch {
            takes.events.collect { event ->
                when (event) {
                    // saved: back to the piece, where the take tops the list (spec 3.19)
                    is TakePipeline.Event.Saved -> close()
                    TakePipeline.Event.NoNotes -> effectChannel.send(CaptureEffect.ShowNoNotes)
                    // kept without the player's stop — the microphone was lost: the screen stays, «Микрофон недоступен» over the
                    // shutter says why the shot ended (spec 3.36.4); the piece shows the take when it is back
                    is TakePipeline.Event.Kept -> Unit
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
            takes.run(config = intonation, owner = TakeOwner.Piece(pieceId), targetMode = { TargetMode.Chromatic }, unavailable = true) { _, _ -> false }.map { output ->
                if (!takes.recordingRequested.value && output.recording == null) {
                    // the take is over — kept, dropped or ended by the microphone: what the chain does with it is done
                    listening.value = false
                    if (closeWhenDone) close()
                }
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
            is CaptureIntent.PermissionsChanged -> permissionsChanged(intent)
            // Refused for good: the system would answer at once with nothing — its settings instead (spec 3.4, 3.36.4).
            CaptureIntent.GrantClicked -> if (state.value.permissionBlocked) {
                effectChannel.trySend(CaptureEffect.OpenSettings)
            } else {
                askedByButton = true
                effectChannel.trySend(CaptureEffect.RequestPermissions)
            }
            CaptureIntent.RecordClicked -> when {
                takes.recordingRequested.value -> takes.recordingRequested.value = false
                // stopped, and still being finished: nothing starts over it, a second tap on «стоп» included
                state.value.recording -> Unit
                // without the permissions the shutter sleeps under its line (spec 3.36.4): «Разрешить доступ» asks, not the shutter
                state.value.canRecord -> viewModelScope.launch { start() }
            }
            CaptureIntent.SwitchCameraClicked -> if (!state.value.recording) mutableState.update { it.copy(front = !it.front, cameraFailed = false) }
            is CaptureIntent.FocusAt -> camera.focus(intent.x, intent.y)
            CaptureIntent.CloseClicked -> when {
                // «Собираем видео…»: the button sleeps; a take stopped by the player closes the screen by itself once saved
                state.value.saving -> Unit
                // like «назад» (spec 3.32): the shot stops and is kept, and the screen goes when the take is done
                state.value.recording -> {
                    closeWhenDone = true
                    takes.recordingRequested.value = false
                }
                else -> {
                    takes.recordingRequested.value = false
                    close()
                }
            }
            CaptureIntent.CameraBindFailed -> mutableState.update { it.copy(cameraFailed = true) }
            // Leaving the app is a quiet save (spec 3.32): the chain ends, its end keeps the take without a word, the backing
            // stops with it, and the screen waits for the next one. Shooting on blind in the background would only leave
            // a hole in the picture. The picture ends here, where the camera stops taking frames: the take would stop it
            // only once its sound is closed, and a start counted back from that later moment would come out late by as
            // much — baked into the video. A take already being finished keeps its sound as it is.
            CaptureIntent.ScreenLeft -> {
                camera.stopNow()
                if (takes.recordingRequested.value) listening.value = false
            }
        }
    }

    private fun permissionsChanged(intent: CaptureIntent.PermissionsChanged) {
        mutableState.update {
            it.copy(
                cameraPermission = intent.camera == CaptureAccess.GRANTED,
                micPermission = intent.mic == CaptureAccess.GRANTED,
                cameraBlocked = CapturePermissionRules.blocked(it.cameraBlocked, intent.camera, intent.answered),
                micBlocked = CapturePermissionRules.blocked(it.micBlocked, intent.mic, intent.answered),
            )
        }
        if (!intent.answered) return
        val blocked = state.value.permissionBlocked
        val byButton = askedByButton
        askedByButton = false
        // the button asked, and the system answered without a dialog: nothing but the settings can help (spec 3.4)
        if (byButton && blocked) effectChannel.trySend(CaptureEffect.OpenSettings)
    }

    /**
     * The backing made ready for the mix at [rate] — the one the take will be recorded at — before the button is pressed.
     * [again]: the take found it gone at that rate after all (the system cleared the cache), so it is made anew.
     */
    private fun prepare(backing: Backing, rate: Int, again: Boolean = false) {
        val key = backing.id to rate
        if (key in unpacking || key in unpackFailed || (key in unpacked && !again)) return
        unpacking += key
        showPreparation()
        viewModelScope.launch {
            val made = try {
                withContext(io) { backingPcm.cached(backing, rate) ?: backingPcm.prepare(backing, rate) }
            } finally {
                unpacking -= key
            }
            if (made == null) unpackFailed += key else unpacked += key
            showPreparation()
        }
    }

    private fun showPreparation() {
        val id = currentBackingId
        mutableState.update { it.copy(preparing = unpacking.any { key -> key.first == id }, backingUnprepared = unpackFailed.any { key -> key.first == id }) }
    }

    /** The take under the backing if the chip is on — as on the piece screen (spec 3.32) — or a plain video. */
    private suspend fun start() {
        val row = backings.pieceBackings.first().firstOrNull { it.pieceId == pieceId }
        val backing = row?.takeIf { it.enabled }?.let { backings.backing(it.backingId) }
        val route = routes.current()
        takes.backingPlan = backing?.let {
            TakePipeline.BackingPlan(
                backing = it,
                ready = { rate -> backingPcm.cached(it, rate) },
                route = route,
                latencyMs = BackingOffset.latencyMs(route, backingConfig),
                // the microphone opened at a rate the sound was not made for: no take, no picture — it is made, and the next press records
                notReady = { rate -> viewModelScope.launch { prepare(it, rate, again = true) } },
            )
        }
        takes.recordingRequested.value = true
        listening.value = true
    }

    // The hook stays: a take the chain is still finishing when the screen goes calls it after this, and it must still
    // make the video, or throw the picture away — never leave it behind (spec 3.32). The camera stops here, and a
    // stop the take asks for after it waits for the same end of the picture.
    override fun onCleared() {
        camera.release()
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        private const val MS_PER_SECOND = 1_000L

        /** 1080p at the bit rate CameraX picks is about 16 Mbit/s: two megabytes a second (spec 5.25). */
        private const val BYTES_PER_MINUTE = 120L * 1024 * 1024
        private const val LOW_SPACE_MINUTES = 10L
    }
}
