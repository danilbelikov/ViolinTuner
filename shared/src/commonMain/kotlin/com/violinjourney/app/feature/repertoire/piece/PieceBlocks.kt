package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.MenuDanger
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_add
import com.violinjourney.app.shared.resources.backing_add_hint
import com.violinjourney.app.shared.resources.backing_menu
import com.violinjourney.app.shared.resources.backing_missing
import com.violinjourney.app.shared.resources.backing_no_space
import com.violinjourney.app.shared.resources.backing_preview
import com.violinjourney.app.shared.resources.backing_preview_stop
import com.violinjourney.app.shared.resources.backing_remove
import com.violinjourney.app.shared.resources.backing_remove_text
import com.violinjourney.app.shared.resources.backing_remove_title
import com.violinjourney.app.shared.resources.backing_replace
import com.violinjourney.app.shared.resources.backing_too_long
import com.violinjourney.app.shared.resources.backing_understood
import com.violinjourney.app.shared.resources.backing_unreadable
import com.violinjourney.app.shared.resources.capture_backing_length
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_notes_add
import com.violinjourney.app.shared.resources.piece_notes_less
import com.violinjourney.app.shared.resources.piece_notes_more
import org.jetbrains.compose.resources.stringResource

// The blocks of an element under its music (spec 3.36.4, 5.29 R4, «Произведение»; repertoire.html 3).
private val TitleTop = 22.dp
private val TitleBottom = 10.dp
private val TitleTopBesideButton = 8.dp
private val TitleRow = 48.dp
private val QuietHeight = 56.dp
private val QuietIcon = 24.dp
private val QuietLine = 1.dp
private val BackingMinHeight = 68.dp
private val PlayIcon = 18.dp
private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp
private val LandscapeLabelBottom = 4.dp
private const val ASLEEP_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"
private const val NOTES_LINE_HEIGHT = 1.5f
private const val NOTES_SIZE = 15

/** Between the music and a group of quiet rows standing without their titles (repertoire.html 3, «Новое»). */
internal val QuietGroupTop = 18.dp

/**
 * The title of a block (5.29 R4): 15 sp / 800, 22 above it, 10 under it, a heading for TalkBack. [beside] stands right after the
 * words («Дубли 6»), [end] at the right edge, 13 sp / 600 in the second level («4 стр.», «G3 – G6»); a button at the end ([action],
 * «Выбрать») keeps the words where they would stand without it.
 */
@Composable
internal fun BlockTitle(
    text: String,
    modifier: Modifier = Modifier,
    beside: String? = null,
    end: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (action != null) Modifier.padding(top = TitleTopBesideButton).heightIn(min = TitleRow) else Modifier.padding(top = TitleTop, bottom = TitleBottom)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text,
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (beside != null) Text(beside, color = colors.onSurfaceVariant, maxLines = 1, style = small)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when {
                action != null -> Box(Modifier.offset(x = 12.dp)) { action() }
                end != null -> Text(end, color = colors.onSurfaceVariant, maxLines = 1, style = small)
            }
        }
    }
}

/**
 * A quiet row of what is not there yet (spec 3.36.4): «Добавить минусовку», «Добавить заметку», «Добавить фото нот» of a scale —
 * 56, a line of 1 above it (and under the last of a group, [bottomLine]), the icon of 24 in the accent, the words of 15 sp / 700 in
 * the second level and a [caption] of 13 sp / 600 in the third. [busy] — a file on its way in: a spinner on the place of the icon,
 * and the row does not answer.
 */
@Composable
internal fun QuietAddRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    bottomLine: Boolean = false,
    busy: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val line = colors.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = QuietHeight)
            .drawBehind {
                val width = QuietLine.toPx()
                drawLine(line, Offset(0f, width / 2), Offset(size.width, width / 2), width)
                if (bottomLine) drawLine(line, Offset(0f, size.height - width / 2), Offset(size.width, size.height - width / 2), width)
            }
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(QuietIcon).padding(2.dp), color = colors.primary, strokeWidth = SpinnerStroke)
        } else {
            AppIcon(icon, contentDescription = null, tint = colors.primary, size = QuietIcon)
        }
        Column(Modifier.weight(1f)) {
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
            if (caption != null) {
                Text(caption, color = ViolinTheme.textTertiary, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/**
 * The backing of an element (spec 3.32, 3.36.4): one card — «слушать» / «пауза» in a circle of 44, the name of the file on up to
 * two lines (lying, whole), its length, and «⋯» with «Заменить» and the coral «Убрать». The headphones are no longer here — they
 * are named by the switch in the bottom zone. Being copied — a spinner on the place of «слушать», «⋯» asleep; the copy lost —
 * «Файл минусовки не найден…» with «⋯» to replace it. What went wrong with a picked file stands under the card as a line with
 * «Понятно» ([BackingProblemLine]). [landscape] — the card beside the notes, without the title of its block: its second line
 * says «минусовка 3:40».
 */
@Composable
internal fun BackingCard(backing: BackingUi, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier, landscape: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BackingMinHeight)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .padding(start = BackingFit.Start, top = 10.dp, end = BackingFit.End, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BackingFit.Gap),
    ) {
        if (backing.problem == BackingProblem.Missing) {
            // a replacement on its way: the copy of the new file is under way
            if (backing.importing) CircularProgressIndicator(Modifier.size(SpinnerSize), strokeWidth = SpinnerStroke)
            Text(
                stringResource(Res.string.backing_missing),
                modifier = Modifier.weight(1f),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
        } else {
            val listen = stringResource(if (backing.previewing) Res.string.backing_preview_stop else Res.string.backing_preview)
            Box(
                modifier = Modifier
                    .size(BackingFit.PlayCircle)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .clickable(enabled = !backing.importing, role = Role.Button, onClickLabel = listen) { onIntent(PieceIntent.BackingPreviewClicked) },
                contentAlignment = Alignment.Center,
            ) {
                // «Заменить»: the new file is being copied in
                if (backing.importing) {
                    CircularProgressIndicator(Modifier.size(SpinnerSize), color = colors.onSurface, strokeWidth = SpinnerStroke)
                } else {
                    AppIcon(if (backing.previewing) AppIcons.Pause else AppIcons.Play, contentDescription = null, tint = colors.onSurface, size = PlayIcon)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    backing.title.orEmpty(),
                    color = colors.onSurface,
                    maxLines = if (landscape) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = backingNameStyle(),
                )
                Text(
                    backingLengthLine(backing, landscape),
                    color = colors.onSurfaceVariant,
                    style = backingLengthStyle(),
                )
            }
        }
        Box {
            // one file at a time: while a replacement is copied, «Заменить» and «Убрать» wait for it (spec 3.32)
            val more = stringResource(Res.string.backing_menu)
            Box(
                modifier = Modifier
                    .size(BackingFit.MoreButton)
                    .alpha(if (backing.importing) ASLEEP_ALPHA else 1f)
                    .clip(CircleShape)
                    .clickable(enabled = !backing.importing, role = Role.Button) { menu = true }
                    .semantics { contentDescription = more },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.More, contentDescription = null, tint = colors.onSurfaceVariant) }
            AppMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                danger = MenuDanger(stringResource(Res.string.backing_remove), onClick = { menu = false; onIntent(PieceIntent.BackingRemoveClicked) }),
            ) {
                AppMenuItem(stringResource(Res.string.backing_replace), icon = AppIcons.FileAudio, onClick = { menu = false; onIntent(PieceIntent.BackingAddClicked) })
            }
        }
    }
    if (backing.askingRemove) {
        DeleteDialog(
            title = stringResource(Res.string.backing_remove_title),
            text = stringResource(Res.string.backing_remove_text),
            confirm = stringResource(Res.string.backing_remove),
            onConfirm = { onIntent(PieceIntent.BackingRemoveConfirmed) },
            onDismiss = { onIntent(PieceIntent.BackingRemoveDismissed) },
        )
    }
}

/** The name of the backing in its card: 15 sp / 700 (5.29 R4) — drawn, and measured by the landscape of the element. */
@Composable
internal fun backingNameStyle(): TextStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)

/** Its length, 13 sp in tabular figures. */
@Composable
internal fun backingLengthStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)

/** «3:40» under the name; lying, without the title of its block — «минусовка 3:40». */
@Composable
internal fun backingLengthLine(backing: BackingUi, landscape: Boolean): String {
    val length = Formats.duration(backing.durationMs)
    return if (landscape) stringResource(Res.string.capture_backing_length, length) else length
}

/** «Добавить минусовку» — «аккомпанемент для записи под него — mp3, m4a, wav…» (spec 3.36.4); a spinner while a file is copied in. */
@Composable
internal fun BackingAddRow(backing: BackingUi, onIntent: (PieceIntent) -> Unit, bottomLine: Boolean, modifier: Modifier = Modifier) {
    QuietAddRow(
        icon = AppIcons.Backing,
        text = stringResource(Res.string.backing_add),
        caption = stringResource(Res.string.backing_add_hint),
        onClick = { onIntent(PieceIntent.BackingAddClicked) },
        modifier = modifier,
        bottomLine = bottomLine,
        busy = backing.importing,
    )
}

/** Why a picked file did not become the backing: a line under the card or the quiet row with «Понятно», not a toast (spec 3.32). */
@Composable
internal fun BackingProblemLine(problem: BackingProblem, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier) {
    val text = when (problem) {
        BackingProblem.Unreadable -> stringResource(Res.string.backing_unreadable)
        BackingProblem.TooLong -> stringResource(Res.string.backing_too_long)
        is BackingProblem.NoSpace -> stringResource(Res.string.backing_no_space, problem.neededMb)
        // said by the card itself
        BackingProblem.Missing -> return
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        )
        AppButton(stringResource(Res.string.backing_understood), onClick = { onIntent(PieceIntent.BackingProblemDismissed) }, style = AppButtonStyle.Text)
    }
}

/**
 * A teacher's pencil marks (spec 3.15, 3.36.4): meant to be read, so they are text on a card, 15 sp at a line of 1.5, folded after
 * [collapsedLines] with «ещё» in the accent. «ещё» works during a take. [label] — lying, beside the backing without the title of
 * its block: «Заметки» small in the card.
 */
@Composable
internal fun NotesCard(notes: String, collapsedLines: Int, modifier: Modifier = Modifier, label: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (overflows || expanded) 2.dp else 14.dp),
    ) {
        if (label) {
            Text(
                stringResource(Res.string.piece_field_notes),
                modifier = Modifier.padding(bottom = LandscapeLabelBottom),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
        Text(
            text = notes,
            color = colors.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = NOTES_SIZE.sp, lineHeight = (NOTES_SIZE * NOTES_LINE_HEIGHT).sp),
        )
        if (overflows || expanded) {
            Box(Modifier.offset(x = (-12).dp)) {
                AppButton(
                    text = stringResource(if (expanded) Res.string.piece_notes_less else Res.string.piece_notes_more),
                    onClick = { expanded = !expanded },
                    style = AppButtonStyle.Text,
                )
            }
        }
    }
}

/** «Добавить заметку» — the form, on its notes (spec 3.15). */
@Composable
internal fun NotesAddRow(onIntent: (PieceIntent) -> Unit, bottomLine: Boolean, modifier: Modifier = Modifier) {
    QuietAddRow(
        icon = AppIcons.Pencil,
        text = stringResource(Res.string.piece_notes_add),
        onClick = { onIntent(PieceIntent.AddNotesClicked) },
        modifier = modifier,
        bottomLine = bottomLine,
    )
}

/** The side a column of an element keeps (5.29 R4, «Общее»). */
internal val ElementSide: Dp = 16.dp
