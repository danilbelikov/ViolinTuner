package com.example.violintuner.feature.repertoire.piece

import com.example.violintuner.core.domain.backing.AudioRoute
import com.example.violintuner.core.domain.backing.Backing
import com.example.violintuner.core.domain.backing.BackingOutput
import com.example.violintuner.core.domain.backing.HeadphoneLatencies
import com.example.violintuner.core.domain.backing.PieceBacking
import com.example.violintuner.core.domain.backing.TakeBacking

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
    /** Of the headphones the sound goes to: calibrated or remembered; null — never measured. */
    val latencyMs: Int?,
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

    /** Wireless headphones never measured: the sheet «Настроим наушники» comes before the first take. */
    val needsCalibration: Boolean get() = wanted && route.output.needsCalibration && latencyMs == null
}

/** «Настроим наушники» (spec 3.32). */
sealed interface CalibrationUi {
    data object Intro : CalibrationUi

    data class Listening(val answered: List<Boolean>, val clicksDone: Int) : CalibrationUi

    data class Done(val latencyMs: Int, val hits: Int, val of: Int) : CalibrationUi

    data object Failed : CalibrationUi
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
        latencies: HeadphoneLatencies,
        fileExists: (Backing) -> Boolean,
        importing: Boolean,
        problem: BackingProblem?,
        previewing: Boolean,
        preparing: Boolean,
        askingRemove: Boolean = false,
    ): BackingUi {
        val row = pieceBackings.firstOrNull { it.pieceId == pieceId }
        val backing = row?.let { r -> backings.firstOrNull { it.id == r.backingId } }
        val latency = route.deviceName?.takeIf { route.output != BackingOutput.SPEAKER }?.let { latencies.of(it) }
        return BackingUi(
            title = backing?.title,
            durationMs = backing?.durationMs ?: 0,
            enabled = row?.enabled ?: false,
            route = route,
            latencyMs = latency,
            importing = importing,
            problem = problem ?: if (backing != null && !fileExists(backing)) BackingProblem.Missing else null,
            previewing = previewing,
            preparing = preparing,
            takesUnder = backing?.let { b -> takeBackings.count { it.backingId == b.id && it.sessionId in takeIds } } ?: 0,
            askingRemove = askingRemove,
        )
    }
}
