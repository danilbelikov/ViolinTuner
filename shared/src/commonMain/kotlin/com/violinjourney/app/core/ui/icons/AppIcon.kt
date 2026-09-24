package com.violinjourney.app.core.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of the icon set by place (handoff `sizes`, spec 5.10). */
object IconSizes {
    /** On its own, in a menu item, in a settings row. */
    val Standalone = 24.dp

    /** Beside the text of a text or outlined button. */
    val InButton = 18.dp

    /** Beside the text of a large filled button. */
    val InFilledButton = 20.dp

    /** A small sign inside a line of text: the take chip, the best take, the tempo, the streak. */
    val InText = 14.dp

    val ButtonGap = 8.dp
    val MenuGap = 16.dp
}

/**
 * An icon of the set. [contentDescription] is null beside a caption that already says it —
 * TalkBack then skips the icon — and is a must for an icon that stands alone (spec 3.16).
 */
@Composable
fun AppIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = IconSizes.Standalone,
    tint: Color = LocalContentColor.current,
) {
    Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** The content of a button that used to be bare text: the icon to the left, the text stays. Takes the button's content colour. */
@Composable
fun IconLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = IconSizes.InButton,
    gap: Dp = IconSizes.ButtonGap,
    style: TextStyle = TextStyle.Default,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        AppIcon(icon, contentDescription = null, size = iconSize)
        Text(text = text, style = style)
    }
}
