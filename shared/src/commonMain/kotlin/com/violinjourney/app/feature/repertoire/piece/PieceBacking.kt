package com.violinjourney.app.feature.repertoire.piece

import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.PieceBacking
import com.violinjourney.app.core.domain.backing.TakeBacking

/** Why a picked file did not become the backing: a line in the block, not a toast (spec 3.32). */
sealed interface BackingProblem {
    data object Unreadable : BackingProblem

    data object TooLong : BackingProblem

    data class NoSpace(val neededMb: Long) : BackingProblem

    /** The piece has a backing whose copy is gone (a copy of the data restored without the sound). */
    data object Missing : BackingProblem
}

/** The block «Минусовка» of the piece screen and what it means for the record button (spec 3.32). */
data class BackingUi(
    /** Null — the piece has no backing yet: «Добавить минусовку». */
    val title: String?,
    val durationMs: Long,
    /** The chip «С минусовкой». */
    val enabled: Boolean,
    val route: AudioRoute,
    val importing: Boolean = false,
    val problem: BackingProblem? = null,
    val previewing: Boolean = false,
    /** Its sound is being prepared for the mix: a take waits for it. */
    val preparing: Boolean = false,
    /** Takes made under it: removing it asks first, they keep their copy. */
    val takesUnder: Int = 0,
    /** «Убрать» asked and waits for an answer. */
    val askingRemove: Boolean = false,
) {
    val present: Boolean get() = title != null && problem != BackingProblem.Missing

    /** The take is to be made under the backing. */
    val wanted: Boolean get() = present && enabled

    /** Under the backing and no headphones: the record button sleeps, a line says why (the speaker is refused, spec 3.32). */
    val blocksRecording: Boolean get() = wanted && (!route.output.isHeadphones || preparing)
}


object PieceBackingReducer {
    /** The block, from what is stored and what only the screen knows (an import on its way, a file that did not open…). */
    fun uiOf(
        pieceId: Long,
        pieceBackings: List<PieceBacking>,
        backings: List<Backing>,
        takeBackings: List<TakeBacking>,
        takeIds: Set<Long>,
        route: AudioRoute,
        fileExists: (Backing) -> Boolean,
        importing: Boolean,
        problem: BackingProblem?,
        previewing: Boolean,
        preparing: Boolean,
        askingRemove: Boolean = false,
    ): BackingUi {
        val row = pieceBackings.firstOrNull { it.pieceId == pieceId }
        val backing = row?.let { r -> backings.firstOrNull { it.id == r.backingId } }
        return BackingUi(
            title = backing?.title,
            durationMs = backing?.durationMs ?: 0,
            enabled = row?.enabled ?: false,
            route = route,
            importing = importing,
            problem = problem ?: if (backing != null && !fileExists(backing)) BackingProblem.Missing else null,
            previewing = previewing,
            preparing = preparing,
            takesUnder = backing?.let { b -> takeBackings.count { it.backingId == b.id && it.sessionId in takeIds } } ?: 0,
            askingRemove = askingRemove,
        )
    }
}
