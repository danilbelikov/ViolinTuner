package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.session_back
import org.jetbrains.compose.resources.stringResource

/** The header of a screen over the tabs (spec 5.29 R8, «Общее»). */
object ScreenHeaderDefaults {
    /** Upright. */
    val Height: Dp = 56.dp

    /** In a window wider than high. */
    val HeightLying: Dp = 48.dp

    /** «назад» and ✕: what is pressed. */
    val Button: Dp = 48.dp

    /** From the edge of the screen to the button, and from the button to the title. */
    val Inset: Dp = 4.dp

    /** Where the title starts without a button, and where it ends: the field of the screens of the redesign. */
    val TitleSide: Dp = 16.dp
}

/**
 * The header of a screen over the tabs (spec 3.36.8, 5.29 R8, «Общее»): «назад» — or ✕ with [close], the same action for a screen
 * that is done («Копия сохранена» of a copy, 3.20) — 48, then the [title]: 20 sp / 800 on one line with an ellipsis, a heading for a
 * reader, which reads it whole («Aus Kopie wiederherstellen»). 56 high, 48 in a window wider than high — by the window
 * ([LocalWindowInfo]), not by the box it stands in. Without a button ([onBack] null) the title starts at 16 from the edge; without a
 * title only the button stands. «Настройки» (stage 121) and the screens of a copy and a restore (stage 122) stand under it.
 */
@Composable
fun ScreenHeader(title: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier, close: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val window = LocalWindowInfo.current.containerSize
    val lying = window.width > window.height
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (lying) ScreenHeaderDefaults.HeightLying else ScreenHeaderDefaults.Height)
            .padding(horizontal = ScreenHeaderDefaults.Inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val label = stringResource(if (close) Res.string.backup_close else Res.string.session_back)
            Box(
                modifier = Modifier
                    .size(ScreenHeaderDefaults.Button)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(if (close) AppIcons.Close else AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        }
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (onBack != null) ScreenHeaderDefaults.Inset else ScreenHeaderDefaults.TitleSide - ScreenHeaderDefaults.Inset,
                        end = ScreenHeaderDefaults.TitleSide - ScreenHeaderDefaults.Inset,
                    )
                    .semantics { heading() },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em),
            )
        }
    }
}
