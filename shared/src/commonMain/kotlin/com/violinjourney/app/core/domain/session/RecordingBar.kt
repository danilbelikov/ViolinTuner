package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.Zone

/**
 * One piece of the mini ribbon of the recording strip (spec 3.9): [buckets] closed buckets of one note in one zone —
 * or, on a long take, of neighbours too narrow to see apart, joined ([SessionRecorder]).
 */
data class RecordingBar(val buckets: Int, val zone: Zone)

/**
 * The mini ribbon of a take: its [pieces] in order, each [share] of the full width, which is [span] buckets. Pieces are
 * counted, not measured, so a longer take that stretches the ribbon changes only [span], and the pieces already closed
 * are handed out again as they are.
 */
class RecordingRibbon(val pieces: List<RecordingBar>, val span: Float) {
    fun share(piece: RecordingBar): Float = piece.buckets / span

    /** The same pieces measured against another whole: a file whose length is known in advance fills it like a progress bar. */
    fun against(span: Float) = RecordingRibbon(pieces, span)

    companion object {
        val EMPTY = RecordingRibbon(emptyList(), 1f)
    }
}
