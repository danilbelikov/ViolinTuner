package com.example.violintuner.feature.repertoire.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.ui.components.rememberSmallFileImage
import com.example.violintuner.core.ui.theme.ViolinTheme

/** How much of the dimming film lies over a thumbnail (handoff `thumb.dim`). */
const val THUMB_DIM = 0.18f

/** The first page is the face of the piece — in the strip and in the list — and is dimmed less. */
const val THUMB_DIM_FIRST = 0.10f

/**
 * A page of sheet music as a small tile (handoff 13b, 13c): the one bright object on a dark
 * screen, so it lies under a film — five cards must not turn into five lamps. Without a photo
 * it is a quiet outline of a sheet, not a hole. Decorative: whoever places it says what it is.
 */
@Composable
fun SheetThumb(path: String?, width: Dp, height: Dp, corner: Dp, modifier: Modifier = Modifier, dim: Float = THUMB_DIM) {
    val colors = ViolinTheme.repertoireColors
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(if (path != null) colors.paper else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) {
            SheetOutline()
        } else {
            rememberSmallFileImage(path)?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.thumbDim.copy(alpha = dim))
                    .border(1.dp, colors.paperFrame, shape),
            )
        }
    }
}

/** A sheet with a few staves, drawn in lines: "there could be music here". */
@Composable
private fun SheetOutline() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        val inset = size.width * OUTLINE_INSET
        val sheet = Size(size.width - inset * 2, size.height - inset * 2)
        drawRoundRect(color, Offset(inset, inset), sheet, CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        repeat(OUTLINE_STAVES) { index ->
            val y = inset + sheet.height * (index + 1) / (OUTLINE_STAVES + 1)
            drawLine(color, Offset(inset * 1.8f, y), Offset(size.width - inset * 1.8f, y), strokeWidth = 1.dp.toPx())
        }
    }
}

private const val OUTLINE_INSET = 0.2f
private const val OUTLINE_STAVES = 4
