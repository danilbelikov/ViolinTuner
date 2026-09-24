package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.history.Selection
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.selection_all
import com.violinjourney.app.shared.resources.selection_close
import com.violinjourney.app.shared.resources.selection_count
import com.violinjourney.app.shared.resources.selection_delete
import com.violinjourney.app.shared.resources.selection_delete_text
import com.violinjourney.app.shared.resources.selection_none
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.video_delete_text
import org.jetbrains.compose.resources.stringResource

/** Heights of the selection bar (handoff `sizes`): that of a top bar; lower when the phone lies on its side. */
object SelectionBarHeight {
    val Portrait = 56.dp
    val Landscape = 52.dp
}

private val BarButton = 48.dp
private val SelectAllHeight = 40.dp
private val SelectTarget = 32.dp
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/**
 * Stands where the top of the screen was while recordings are being picked (spec 3.18, handoff
 * 19e): close, «Выбрано: 3», «Выбрать все» / «Снять все», the bin. The bin is up here on purpose —
 * what cannot be undone should not lie under the thumb. With nothing picked it is dimmed.
 */
@Composable
fun SelectionBar(selection: Selection, allSelected: Boolean, onIntent: (SelectionIntent) -> Unit, modifier: Modifier = Modifier, height: Dp = SelectionBarHeight.Portrait) {
    val colors = MaterialTheme.colorScheme
    val line = colors.surfaceContainerHigh
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.surface)
            .drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(AppIcons.Close, stringResource(Res.string.selection_close)) { onIntent(SelectionIntent.Closed) }
        Text(
            text = stringResource(Res.string.selection_count, selection.count),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = colors.onSurface,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
        Box(
            modifier = Modifier
                .heightIn(min = SelectAllHeight)
                .clip(RoundedCornerShape(SelectAllHeight / 2))
                .clickable(role = Role.Button) { onIntent(SelectionIntent.SelectAllClicked) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (allSelected) Res.string.selection_none else Res.string.selection_all),
                color = colors.primary,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        val canDelete = selection.count > 0
        BarIcon(AppIcons.Trash, stringResource(Res.string.selection_delete), enabled = canDelete, modifier = Modifier.alpha(if (canDelete) 1f else DISABLED_ALPHA)) {
            onIntent(SelectionIntent.DeleteClicked)
        }
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(BarButton)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** «Выбрать» at the right end of the line above a list: the way into the mode that can be found (a long press cannot). */
@Composable
fun SelectAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = SelectTarget)
            .clip(RoundedCornerShape(SelectTarget / 2))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Select, contentDescription = null, tint = colors.primary, size = SelectIcon)
        Text(
            text = stringResource(Res.string.selection_select),
            modifier = Modifier.padding(start = 6.dp),
            color = colors.primary,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

private val SelectIcon = 16.dp

/** «Звук и разбор удалятся» — and, when videos are among what goes, their weight first (spec 3.19). */
@Composable
fun deleteTextOf(videoBytes: Long): String =
    if (videoBytes > 0) stringResource(Res.string.video_delete_text, Formats.fileSize(videoBytes)) else stringResource(Res.string.selection_delete_text)
