package com.example.violintuner.feature.repertoire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RepertoireViewModel @Inject constructor(
    repertoire: RepertoireRepository,
    sessions: SessionRepository,
    private val sheetFiles: SheetFiles,
    private val config: IntonationConfig,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow<PieceStatus?>(null)

    val state: StateFlow<RepertoireState> =
        combine(repertoire.pieces, repertoire.pages, sessions.sessions, filter) { pieces, pages, sessions, filter ->
            RepertoireReducer.stateOf(
                pieces, pages, sessions, filter, LocalDate.now(clock), clock.zone,
                thumbPathOf = { sheetFiles.existing(it)?.path },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = RepertoireReducer.loading(filter.value),
        )

    private val effectChannel = Channel<RepertoireEffect>(Channel.BUFFERED)
    val effects: Flow<RepertoireEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: RepertoireIntent) {
        when (intent) {
            is RepertoireIntent.FilterSelected -> filter.value = intent.status
            is RepertoireIntent.PieceClicked -> effectChannel.trySend(RepertoireEffect.OpenPiece(intent.id))
            RepertoireIntent.AddClicked -> effectChannel.trySend(RepertoireEffect.OpenNewPiece)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
