package com.violinjourney.app.feature.repertoire.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.piece_status_in_repertoire
import com.violinjourney.app.shared.resources.piece_status_learned
import com.violinjourney.app.shared.resources.piece_status_learning
import com.violinjourney.app.shared.resources.piece_status_reading
import org.jetbrains.compose.resources.stringResource

/** The word of a status, as the switch of an element, the list and the form say it (spec 3.15, 3.22, 3.36.4). */
@Composable
fun statusLabel(status: PieceStatus): String = stringResource(
    when (status) {
        PieceStatus.READING -> Res.string.piece_status_reading
        PieceStatus.LEARNING -> Res.string.piece_status_learning
        // one status in the data, two words for it (spec 3.22): a scale is «выучена», a concerto is «в репертуаре»
        PieceStatus.IN_REPERTOIRE -> if (LocalExerciseWords.current) Res.string.piece_status_learned else Res.string.piece_status_in_repertoire
    },
)

/**
 * True inside «Гаммы», «Этюды» and «Штрихи»: the third step of the status reads «Выучено» there.
 * Provided by the screens that know their section — the list, the element, its form; the words
 * of the status are asked for in a dozen places that have no business knowing about sections.
 */
val LocalExerciseWords = staticCompositionLocalOf { false }
