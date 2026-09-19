package com.example.violintuner.feature.repertoire.stand

/** One sheet on the stand. */
data class StandPage(
    val pageId: Long,
    /** Null when the file is gone: the stand shows blank paper. */
    val path: String?,
)

/**
 * The music stand (spec 3.15): the pages of one piece, full screen. The recording of a take is
 * not here — it belongs to the piece and is shared with its screen, so that walking between
 * the two does not break it.
 */
data class StandState(
    /** True until the pages have been read once. */
    val loading: Boolean,
    val pages: List<StandPage>,
    /** Where to open; the pager keeps the page from then on. */
    val initialPage: Int,
    val panelVisible: Boolean,
    val deleteDialog: Boolean,
    /** The very first visit: outline the page-turning zones once. */
    val showHint: Boolean,
)

sealed interface StandIntent {
    data object BackClicked : StandIntent

    /** A tap in the middle third: the panel comes, or goes. */
    data object PanelToggled : StandIntent

    /** Anything that shows the player is at the controls: the panel waits its three seconds again. */
    data object Touched : StandIntent

    data class PageSettled(val index: Int) : StandIntent

    data object DeleteClicked : StandIntent

    data object DeleteConfirmed : StandIntent

    data object DeleteDismissed : StandIntent

    /** The hint has played. */
    data object HintShown : StandIntent
}

sealed interface StandEffect {
    data object Close : StandEffect
}
