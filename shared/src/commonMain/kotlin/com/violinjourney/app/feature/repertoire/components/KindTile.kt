package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.icons.AppIcon

private val IconSize = 24.dp

/**
 * The tile of an element without a photo of its music (spec 3.36.4, 5.29 R4): not an empty cover but what the element is — a note
 * for a piece and an element of a section of one's own, the page of an étude, a bow for a stroke; surfaceContainerHigh with the
 * [icon] of 24 in the second level of text. A scale has its clef with the key signature instead
 * ([com.violinjourney.app.feature.repertoire.scale.KeySignatureTile]). Decorative: the card says what it is.
 */
@Composable
fun KindTile(icon: ImageVector, width: Dp, height: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(width, height)
            .background(colors.surfaceContainerHigh, shape),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = null, size = IconSize, tint = colors.onSurfaceVariant) }
}
