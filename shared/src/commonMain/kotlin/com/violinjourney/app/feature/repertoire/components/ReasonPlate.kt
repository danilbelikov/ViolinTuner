package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.theme.AppShapes

// The plate of the bottom zone (spec 5.29 R4, «Нижняя зона записи»).
private val PlateIcon = 18.dp
private val PlateSide = 12.dp
private val PlateVertical = 10.dp
private val PlateGap = 8.dp
private val PlateLineHeight = 18.sp

/**
 * What stands over the key of a bottom zone and why (5.29 R4): a plate of surfaceContainer at a corner of 12, the [icon] of 18 in the
 * second level (not the amber — that is the zone «рядом») and the words of 13 sp in the colour of text, as many lines as they take;
 * said aloud when it appears. Why «Записать дубль» sleeps («Подключите наушники…»), and the scale that is there already over
 * «Открыть её» of its form. The caller gives the width.
 */
@Composable
internal fun ReasonPlate(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(colors.surfaceContainer, AppShapes.S)
            .padding(horizontal = PlateSide, vertical = PlateVertical)
            // said aloud when it appears: the key under it has just changed
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(PlateGap),
    ) {
        AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant, size = PlateIcon)
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = PlateLineHeight),
        )
    }
}
