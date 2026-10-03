package com.violinjourney.app.feature.events.performances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.PerformanceRow
import com.violinjourney.app.core.domain.events.Performances
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.ticksAt
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/**
 * «Выступления» (spec 3.35, 3.36.9): every event of the kind «Выступление» — «Впереди» the nearest first, «Прошли» the freshest first,
 * divided by their end (spec 5.28) — each with its programme in one line and, at its end, the thumbnail of its newest video or the
 * number of its recordings. Worked out anew whenever the events, their kinds, the programmes, the recordings or the repertoire change,
 * and by itself at the moments it can change ([Performances.nextChangeAt], plan D42): the end of a concert of today — it goes over to
 * «Прошли» while the screen is open — and midnight, when the terms move on; never in between ([ticksAt], plan D9). Off the main thread
 * ([background]). The thumbnail of a video is the one the store of the recordings found with it (spec 3.38, 5.31): of a video whose file
 * is there, made by the current rule — the frame these rows show is the one of the lists of recordings.
 *
 * A press leaves the screen once ([PerformancesIntent.Shown] — the screen in sight again — lets presses count anew): a second tap of a
 * double tap does not open a second screen over the first, nor go back twice (the lesson of stage 120).
 */
open class PerformancesViewModel(
    private val events: EventRepository,
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val config: EventsConfig,
    private val clock: WallClock,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private data class Stored(
        val events: List<CalendarEvent>,
        val kinds: List<EventKind>,
        val programs: Map<Long, List<Long>>,
        val sessions: List<SessionSummary>,
        val pieces: List<Piece>,
    )

    private val stored: Flow<Stored> = combine(events.events, events.kinds, events.programs, sessions.sessions, repertoire.pieces, ::Stored)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PerformancesState> = stored.flatMapLatest { now ->
        // the zone is read at each wake: a flight changes it under an open screen
        clock.ticksAt { at -> Performances.nextChangeAt(now.events, at, clock.zone, config) }.map { at -> stateOf(now, at) }
    }.flowOn(background).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), loading())

    private val effectChannel = Channel<PerformancesEffect>(Channel.BUFFERED)
    val effects: Flow<PerformancesEffect> = effectChannel.receiveAsFlow()

    /** Pressed already: the screen is on its way to where the press leads, and nothing more is heard until it is in sight again. */
    private var away = false

    fun onIntent(intent: PerformancesIntent) {
        when (intent) {
            is PerformancesIntent.RowClicked -> leave(PerformancesEffect.OpenEvent(intent.eventId))
            PerformancesIntent.AddClicked -> leave(PerformancesEffect.OpenNewPerformance)
            PerformancesIntent.BackClicked -> leave(PerformancesEffect.Close)
            PerformancesIntent.Shown -> away = false
        }
    }

    private fun leave(effect: PerformancesEffect) {
        if (away) return
        away = true
        effectChannel.trySend(effect)
    }

    private fun stateOf(stored: Stored, at: Instant): PerformancesState {
        val rows = Performances.rows(stored.events, stored.programs, stored.pieces, stored.sessions, at, clock.zone, config)
        val thumbs = stored.sessions.mapNotNull { session -> session.thumbPath?.let { path -> session.videoPath?.let { it to path } } }.toMap()
        fun cardOf(row: PerformanceRow) = PerformanceCard(row = row, thumbPath = row.lastVideo?.let(thumbs::get))
        return PerformancesState(
            loading = false,
            ahead = rows.ahead.map(::cardOf),
            past = rows.past.map(::cardOf),
            look = KindRules.lookOf(PERFORMANCE, stored.kinds, config),
        )
    }

    private fun loading() = PerformancesState(loading = true, look = KindRules.lookOf(PERFORMANCE, emptyList(), config))

    companion object {
        private val PERFORMANCE = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
