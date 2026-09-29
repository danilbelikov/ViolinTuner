package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.ui.theme.ViolinTheme

private val DotSize = 8.dp
private val RingWidth = 2.dp
private val DotGap = 6.dp

/**
 * The status of an element in its card (spec 3.36.4, 5.29 R4): a dot of 8 and the word, 13 sp / 700, 6 apart — the three tones of
 * the learnt shares, never green. «Разбираю» — a ring in the tone of «учу» and the word in the second level of text: a dark filled dot
 * would not show on the card, and the form tells the step without the colour; «Учу» — the middle tone; «В репертуаре» / «Выучено» —
 * the accent. Their words are the colour of text. The dot says nothing to TalkBack: the word does.
 */
@Composable
fun StatusMark(status: PieceStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tones = ViolinTheme.exerciseColors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        Canvas(Modifier.size(DotSize)) {
            val radius = size.minDimension / 2
            when (status) {
                PieceStatus.READING -> {
                    val ring = RingWidth.toPx()
                    drawCircle(tones.learning, radius = radius - ring / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(ring))
                }
                PieceStatus.LEARNING -> drawCircle(tones.learning, radius)
                PieceStatus.IN_REPERTOIRE -> drawCircle(tones.learned, radius)
            }
        }
        Text(
            text = statusLabel(status),
            color = if (status == PieceStatus.READING) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        )
    }
}
