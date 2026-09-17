package com.example.violintuner.core.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory repository for view model tests; ids count up from 1. */
class FakeSessionRepository : SessionRepository {
    val saved = mutableListOf<NewSession>()
    override val sessions = MutableStateFlow<List<SessionSummary>>(emptyList())

    override suspend fun save(session: NewSession): Long {
        saved += session
        val id = saved.size.toLong()
        sessions.update { listOf(summaryOf(id, session)) + it }
        return id
    }

    override suspend fun details(id: Long): SessionDetails? {
        val summary = sessions.value.firstOrNull { it.id == id } ?: return null
        val session = saved[(id - 1).toInt()]
        return SessionDetails(summary, session.samples, SessionAnalyzer.analyze(session.samples, session.config))
    }

    override suspend fun rename(id: Long, title: String?) = sessions.update { list ->
        list.map { if (it.id == id) it.copy(title = title?.trim()?.takeIf(String::isNotEmpty)) else it }
    }

    override suspend fun delete(id: Long) = sessions.update { list -> list.filterNot { it.id == id } }

    private fun summaryOf(id: Long, session: NewSession) = SessionSummary(
        id = id,
        title = null,
        startedAtEpochMs = session.startedAtEpochMs,
        durationMs = session.durationMs,
        a4Hz = session.config.a4Hz,
        toleranceCents = session.config.toleranceCents,
        nearCents = session.config.nearCents,
        scorePercent = session.metrics.scorePercent,
        nearPercent = session.metrics.nearPercent,
        offPercent = session.metrics.offPercent,
        maeCents = session.metrics.maeCents,
        biasCents = session.metrics.biasCents,
        previewZones = session.previewZones,
        audioPath = session.audioPath,
    )
}
