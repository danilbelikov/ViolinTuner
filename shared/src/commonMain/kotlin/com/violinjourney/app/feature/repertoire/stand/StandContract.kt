package com.violinjourney.app.feature.repertoire.stand

import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.piece.TakeState

/** One sheet on the stand. */
data class StandPage(
    val pageId: Long,
    /** Null when the file is gone: the stand shows blank paper. */
    val path: String?,
    /** The first page of a scale (spec 3.22): its notes drawn on the paper by the app. There is no file behind it and nothing to delete. */
    val scale: Scale? = null,
) {
    val drawn: Boolean get() = scale != null

    companion object {
        /** No row stands behind a drawn page. */
        const val DRAWN_ID = -1L
    }
}

/**
 * The music stand (spec 3.15): the pages of one piece, full screen. The recording of a take is
 * not here — it belongs to the piece and is shared with its screen, so that walking between
 * the two does not break it.
 */
data class StandState(
    /** True until the pages and the mark of the first visit have been read once: nothing — not the panel — shows before the hint. */
    val loading: Boolean,
    val pages: List<StandPage>,
    /** Where to open; the pager keeps the page from then on. */
    val initialPage: Int,
    val panelVisible: Boolean,
    val deleteDialog: Boolean,
    /**
     * The very first visit (spec 3.36.4): the page-turning zones and the card «Тап по краю листа…», for
     * [com.violinjourney.app.core.domain.repertoire.RepertoireConfig.standHintMs] or until the first touch; no panel meanwhile.
     */
    val showHint: Boolean,
)

/**
 * What the stand shows of the piece's take (spec 3.15, 3.36.4): whether it runs, its seconds and whether it is too noisy — blind as
 * the piece screen, and without the loudness row and the backing's bar it has no room for. The take changes twenty
 * times a second with its loudness; this, once a second.
 */
data class StandTake(val recording: Boolean, val elapsedSeconds: Long, val problem: TakeProblem?) {
    companion object {
        fun of(take: TakeState) = StandTake(take.recording, take.elapsedSeconds, take.problem)
    }
}

sealed interface StandIntent {
    data object BackClicked : StandIntent

    /** A tap in the middle third: the panel comes, or goes. */
    data object PanelToggled : StandIntent

    /** Anything that shows the player is at the controls: the panel waits its three seconds again; the hint of the first visit goes. */
    data object Touched : StandIntent

    data class PageSettled(val index: Int) : StandIntent

    data object DeleteClicked : StandIntent

    data object DeleteConfirmed : StandIntent

    data object DeleteDismissed : StandIntent

    /** The hint has stayed its time: it goes, and is never shown again. */
    data object HintShown : StandIntent
}

sealed interface StandEffect {
    data object Close : StandEffect
}
