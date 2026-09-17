package com.example.violintuner.feature.session

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionDetails
import com.example.violintuner.core.domain.session.SessionSample
import com.example.violintuner.core.domain.session.SessionSummary
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.session.components.NoteSheetContent
import java.time.ZoneId
import kotlin.math.sin

// Handoff frame 4a and the cases it does not show.

/** A D major scale up and down, some notes flat, each with a little vibrato. */
private fun scaleSamples(repeats: Int): List<SessionSample?> {
    val notes = listOf(62 to -3.0, 64 to 2.0, 66 to -14.0, 67 to 1.0, 69 to 4.0, 71 to -2.0, 73 to -11.0, 74 to 0.0, 78 to -19.0)
    val samples = ArrayList<SessionSample?>()
    repeat(repeats) { round ->
        (if (round % 2 == 0) notes else notes.reversed()).forEach { (midi, bias) ->
            repeat(14) { samples += SessionSample(midi, bias + 4 * sin(it * 0.9)) }
            samples += null
        }
    }
    return samples
}

private fun contentOf(samples: List<SessionSample?>, title: String? = "Гаммы D-dur"): SessionContent {
    val config = IntonationConfig()
    val analysis = SessionAnalyzer.analyze(samples, config)
    val metrics = analysis.metrics
    val summary = SessionSummary(
        id = 1, title = title, startedAtEpochMs = 1_789_000_000_000, durationMs = samples.size * config.sessionBucketMs,
        a4Hz = 440.0, toleranceCents = config.toleranceCents, nearCents = config.nearCents,
        scorePercent = metrics?.scorePercent ?: 0, nearPercent = metrics?.nearPercent ?: 0,
        offPercent = metrics?.offPercent ?: 0, maeCents = metrics?.maeCents ?: 0.0, biasCents = metrics?.biasCents ?: 0.0,
        previewZones = emptyList(), audioPath = null,
    )
    return SessionContentMapper.contentOf(SessionDetails(summary, samples, analysis), config)
}

@Composable
private fun SessionPreview(state: SessionState) {
    ViolinTheme { SessionScreen(state = state, onIntent = {}, zone = ZoneId.of("Europe/Moscow")) }
}

@Preview(name = "Session · scale, 14 s", widthDp = 412, heightDp = 1100)
@Composable
private fun SessionScalePreview() = SessionPreview(SessionState.Loaded(contentOf(scaleSamples(repeats = 2))))

@Preview(name = "Session · long, default title, note selected", widthDp = 412, heightDp = 1100)
@Composable
private fun SessionLongPreview() = SessionPreview(
    SessionState.Loaded(contentOf(scaleSamples(repeats = 40), title = null), selectedSegment = null),
)

@Preview(name = "Session · one clean note", widthDp = 412, heightDp = 900)
@Composable
private fun SessionCleanPreview() = SessionPreview(
    SessionState.Loaded(contentOf(List(80) { SessionSample(69, 1.5 * sin(it * 0.5)) })),
)

@Preview(name = "Session · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun SessionLandscapePreview() = SessionPreview(SessionState.Loaded(contentOf(scaleSamples(repeats = 2))))

@Preview(name = "Session · not found", widthDp = 412, heightDp = 500)
@Composable
private fun SessionNotFoundPreview() = SessionPreview(SessionState.NotFound)

@Preview(name = "Note sheet", widthDp = 412)
@Composable
private fun NoteSheetPreview() {
    ViolinTheme {
        val segment = contentOf(scaleSamples(repeats = 1)).segments.first { it.zone == Zone.OFF || it.zone == Zone.NEAR }
        NoteSheetContent(segment, Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
