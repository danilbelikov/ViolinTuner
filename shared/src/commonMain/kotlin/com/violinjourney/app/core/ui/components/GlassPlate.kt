package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The glass over pictures (spec 5.29; components.html, «Стекло»).
private val PlateGap = 8.dp
private val TagHeight = 32.dp
private val TagIcon = 15.dp
private val Capsule = RoundedCornerShape(percent = 50)

/**
 * A plate of smoked glass over a picture (spec 3.36.1, 5.29): the ground of the screen at 0.72 ([ViolinTheme.glass]), or at 0.82
 * where the picture under it is busy ([strong]: the city tag of the shop, R7; the line of a missing permission over the preview of
 * the own camera, R4). No blur, no layer. The words on it are onSurface — the grey of the second level is never used on glass
 * (2.9 : 1 over white); a second line of a card on glass is GlassCaption (R6). A row, its children centred and 8 dp apart.
 *
 * A capsule by default; the card of 60 at a corner of 18 is `GlassPlate(Modifier.height(60.dp), shape = AppShapes.M,
 * contentPadding = PaddingValues(horizontal = 14.dp))`.
 */
@Composable
fun GlassPlate(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    shape: Shape = Capsule,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = modifier
                .background(if (strong) ViolinTheme.glassStrong else ViolinTheme.glass, shape)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlateGap),
            content = content,
        )
    }
}

/** A tag of glass in one line (components.html, `.gp.plate`): 32 high, a capsule, an [icon] of 15 and the [text] of 13 sp / 700 — «Дома». */
@Composable
fun GlassTag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, strong: Boolean = false) {
    GlassPlate(modifier.heightIn(min = TagHeight), strong = strong) {
        if (icon != null) AppIcon(icon, contentDescription = null, size = TagIcon)
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        )
    }
}
