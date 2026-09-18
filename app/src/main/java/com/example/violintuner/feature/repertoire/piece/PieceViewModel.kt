package com.example.violintuner.feature.repertoire.piece

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PieceViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val sheetFiles: SheetFiles,
    private val config: RepertoireConfig,
    private val clock: Clock,
) : ViewModel() {
    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "piece id is required" }

    /** What only the screen decides: photos on their way in and the open menu. */
    private data class Ui(val importing: Int = 0, val statusMenuOpen: Boolean = false)

    private val ui = MutableStateFlow(Ui())
    private var closed = false
    private val effectChannel = Channel<PieceEffect>(Channel.BUFFERED)
    val effects: Flow<PieceEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<PieceState> = combine(repertoire.pieces, repertoire.pages, ui) { pieces, pages, ui ->
        val piece = pieces.firstOrNull { it.id == pieceId }
        if (piece == null) {
            // Deleted from its form, or an id from nowhere: there is nothing to show. Said once:
            // a second "close" would take the screen underneath with it.
            if (!closed) effectChannel.trySend(PieceEffect.Close)
            closed = true
            PieceReducer.loading(config)
        } else {
            PieceReducer.stateOf(piece, pages, ui.importing, ui.statusMenuOpen, config) { sheetFiles.existing(it)?.path }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PieceReducer.loading(config))

    // Photos go in one at a time and in the order they were picked: that is the order of the pages.
    private val importLock = Mutex()

    fun onIntent(intent: PieceIntent) {
        when (intent) {
            PieceIntent.BackClicked -> effectChannel.trySend(PieceEffect.Close)
            PieceIntent.EditClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = false))
            PieceIntent.AddNotesClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = true))
            PieceIntent.StatusChipClicked -> ui.update { it.copy(statusMenuOpen = true) }
            PieceIntent.StatusMenuDismissed -> ui.update { it.copy(statusMenuOpen = false) }
            is PieceIntent.StatusSelected -> {
                ui.update { it.copy(statusMenuOpen = false) }
                viewModelScope.launch { repertoire.setStatus(pieceId, intent.status, clock.millis()) }
            }
            is PieceIntent.PageClicked -> effectChannel.trySend(PieceEffect.OpenStand(pieceId, intent.index))
            is PieceIntent.PhotosPicked -> import(intent.uris, temporary = emptyList())
            PieceIntent.CameraClicked -> {
                val file = sheetFiles.newCameraFile()
                // The camera app may push this process out of memory: the path has to outlive it.
                savedState[KEY_CAMERA_FILE] = file.path
                effectChannel.trySend(PieceEffect.LaunchCamera(file.path))
            }
            is PieceIntent.CameraFinished -> {
                val file = savedState.remove<String>(KEY_CAMERA_FILE)?.let(::File) ?: return
                if (intent.saved) import(listOf(file.toURI().toString()), temporary = listOf(file)) else file.delete()
            }
        }
    }

    private fun import(uris: List<String>, temporary: List<File>) {
        if (uris.isEmpty()) return
        ui.update { it.copy(importing = it.importing + uris.size) }
        viewModelScope.launch {
            var failed = false
            importLock.withLock {
                uris.forEach { uri ->
                    val stored = sheetFiles.import(uri)
                    if (stored == null) failed = true else repertoire.addPage(pieceId, stored.fileName, stored.thumbFileName, clock.millis())
                    ui.update { it.copy(importing = it.importing - 1) }
                }
            }
            temporary.forEach { it.delete() }
            // One word for the whole batch: the pages that did open are already in the strip.
            if (failed) effectChannel.send(PieceEffect.ShowPhotoFailed)
        }
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        private const val KEY_CAMERA_FILE = "cameraFile"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
