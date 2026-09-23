package com.violinjourney.app.feature.repertoire.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.ui.theme.ViolinTheme

@Composable
fun statusLabel(status: PieceStatus): String = stringResource(
    when (status) {
        PieceStatus.READING -> R.string.piece_status_reading
        PieceStatus.LEARNING -> R.string.piece_status_learning
        // one status in the data, two words for it (spec 3.22): a scale is «выучена», a concerto is «в репертуаре»
        PieceStatus.IN_REPERTOIRE -> if (LocalExerciseWords.current) R.string.piece_status_learned else R.string.piece_status_in_repertoire
    },
)

/**
 * True inside «Гаммы», «Этюды» and «Штрихи»: the third step of the status reads «Выучено» there.
 * Provided by the screens that know their section — the list, the element, its form; the words
 * of the status are asked for in a dozen places that have no business knowing about sections.
 */
val LocalExerciseWords = staticCompositionLocalOf { false }

/** Container and content color of a status: the word always says it too, the color only helps. */
@Composable
fun statusColors(status: PieceStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val repertoire = ViolinTheme.repertoireColors
    return when (status) {
        PieceStatus.READING -> scheme.surfaceContainerHigh to scheme.onSurface
        PieceStatus.LEARNING -> scheme.primaryContainer to scheme.onPrimaryContainer
        PieceStatus.IN_REPERTOIRE -> repertoire.statusRepertoireContainer to repertoire.onStatusRepertoireContainer
    }
}

/** The small chip of the list card (22 dp) and, larger, the one of the piece screen. [trailing] is the chevron there. */
@Composable
fun StatusChip(
    status: PieceStatus,
    modifier: Modifier = Modifier,
    height: Dp = 22.dp,
    corner: Dp = 6.dp,
    fontSize: Int = 11,
    trailing: @Composable (tint: Color) -> Unit = {},
) {
    val (container, content) = statusColors(status)
    Row(
        modifier = modifier
            .height(height)
            .background(container, RoundedCornerShape(corner))
            .padding(horizontal = if (height > 24.dp) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = statusLabel(status),
            color = content,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold),
        )
        trailing(content)
    }
}
