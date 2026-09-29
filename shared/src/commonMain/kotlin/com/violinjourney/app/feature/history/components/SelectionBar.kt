package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.Selection
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.selection_all
import com.violinjourney.app.shared.resources.selection_close
import com.violinjourney.app.shared.resources.selection_count
import com.violinjourney.app.shared.resources.selection_delete
import com.violinjourney.app.shared.resources.selection_delete_records_few
import com.violinjourney.app.shared.resources.selection_delete_records_many
import com.violinjourney.app.shared.resources.selection_delete_records_one
import com.violinjourney.app.shared.resources.selection_delete_takes_few
import com.violinjourney.app.shared.resources.selection_delete_takes_many
import com.violinjourney.app.shared.resources.selection_delete_takes_one
import com.violinjourney.app.shared.resources.selection_delete_text
import com.violinjourney.app.shared.resources.selection_none
import com.violinjourney.app.shared.resources.session_delete_text
import com.violinjourney.app.shared.resources.session_delete_title
import com.violinjourney.app.shared.resources.video_delete_text
import org.jetbrains.compose.resources.stringResource

/** Heights of the selection bar (handoff `sizes`): that of a top bar; lower when the phone lies on its side. */
object SelectionBarHeight {
    val Portrait = 56.dp
    val Landscape = 52.dp
}

private val BarButton = 48.dp
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/**
 * Stands where the top of the screen was while recordings are being picked (spec 3.18, 3.36.5): close, «Выбрано: 3» (17 sp, 800, an
 * ellipsis rather than a squeezed button), «Выбрать все» / «Снять все», the bin in the colour of danger. The bin is up here on
 * purpose — what cannot be undone should not lie under the thumb. With nothing picked it is dimmed. The buttons are 48 and are
 * measured first: the count gives way to them.
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
        BarIcon(AppIcons.Close, stringResource(Res.string.selection_close), tint = colors.onSurface) { onIntent(SelectionIntent.Closed) }
        Text(
            text = stringResource(Res.string.selection_count, selection.count),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
        AppButton(
            text = stringResource(if (allSelected) Res.string.selection_none else Res.string.selection_all),
            onClick = { onIntent(SelectionIntent.SelectAllClicked) },
            style = AppButtonStyle.Text,
        )
        val canDelete = selection.count > 0
        BarIcon(
            AppIcons.Trash, stringResource(Res.string.selection_delete), tint = ViolinTheme.dangerSoft, enabled = canDelete,
            modifier = Modifier.alpha(if (canDelete) 1f else DISABLED_ALPHA),
        ) { onIntent(SelectionIntent.DeleteClicked) }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, tint: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(BarButton)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, contentDescription = null, tint = tint)
    }
}

/**
 * The question before the recordings of [selection] go — «Записи» and the takes of a piece alike (spec 3.18, 3.36.5). Several
 * picked: «Удалить 2 записи?» ([takes] — «Удалить 2 дубля?») with the weight of their videos (spec 3.19); one card's «Удалить…»:
 * the question of the recording's own screen ([RecordDeleteDialog]). Nothing while nothing is asked. [cards] — what the list shows.
 */
@Composable
fun SelectionDeleteDialog(selection: Selection, cards: List<HistoryCard>, takes: Boolean, onIntent: (SelectionIntent) -> Unit) {
    if (!selection.confirming) return
    val gone = cards.filter { it.id in selection.ids }
    val onConfirm = { onIntent(SelectionIntent.DeleteConfirmed) }
    val onDismiss = { onIntent(SelectionIntent.DeleteDismissed) }
    if (!selection.active) {
        RecordDeleteDialog(videoBytes = gone.singleOrNull()?.videoBytes?.takeIf { it > 0 }, onConfirm = onConfirm, onDismiss = onDismiss)
        return
    }
    val words = if (takes) {
        Formats.plural(selection.count, Res.string.selection_delete_takes_one, Res.string.selection_delete_takes_few, Res.string.selection_delete_takes_many)
    } else {
        Formats.plural(selection.count, Res.string.selection_delete_records_one, Res.string.selection_delete_records_few, Res.string.selection_delete_records_many)
    }
    val videoBytes = gone.sumOf { it.videoBytes }
    DeleteDialog(
        title = stringResource(words, selection.count),
        // a video is the heaviest thing that goes (spec 3.19)
        text = if (videoBytes > 0) stringResource(Res.string.video_delete_text, Formats.fileSize(videoBytes)) else stringResource(Res.string.selection_delete_text),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * «Удалить запись?» of one recording (spec 3.10, 3.36.5) — on its own screen and from the «⋯» of its card: what goes with it, and a
 * video's weight first ([videoBytes], null without a video or when its file is lost; spec 3.19).
 */
@Composable
fun RecordDeleteDialog(videoBytes: Long?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DeleteDialog(
        title = stringResource(Res.string.session_delete_title),
        // a video is the heaviest thing that goes, and the one that cannot be played again (spec 3.19)
        text = videoBytes?.let { stringResource(Res.string.video_delete_text, Formats.fileSize(it)) } ?: stringResource(Res.string.session_delete_text),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
