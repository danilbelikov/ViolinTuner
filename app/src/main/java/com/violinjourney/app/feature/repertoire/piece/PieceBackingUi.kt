package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons

private val CardCorner = 16.dp
private val PlayButton = 44.dp
private val ChipHeight = 36.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * The block «Минусовка» of the piece screen (spec 3.32): add one, listen to it, replace or remove it, and see which
 * headphones it goes to. A file that did not open says why in the block itself, not in a toast.
 */
@Composable
fun BackingCard(backing: BackingUi, recording: Boolean, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (!backing.present && backing.problem != BackingProblem.Missing) {
        // «Добавить минусовку»: a dashed invitation, like the empty sheets
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardCorner))
                    .border(1.5.dp, colors.outlineVariant, RoundedCornerShape(CardCorner))
                    .clickable(enabled = !backing.importing && !recording, role = Role.Button) { onIntent(PieceIntent.BackingAddClicked) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(PlayButton).clip(CircleShape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    if (backing.importing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        AppIcon(AppIcons.Plus, contentDescription = null, tint = colors.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.backing_add), color = colors.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    Text(stringResource(R.string.backing_add_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
                }
            }
            backing.problem?.let { ProblemLine(it, onIntent) }
        }
        return
    }
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.backing_block_title).uppercase(),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }, enabled = !recording) {
                    AppIcon(AppIcons.More, contentDescription = stringResource(R.string.backing_menu), tint = colors.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.backing_replace)) }, onClick = { menu = false; onIntent(PieceIntent.BackingAddClicked) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.backing_remove)) }, onClick = { menu = false; onIntent(PieceIntent.BackingRemoveClicked) })
                }
            }
        }
        if (backing.problem == BackingProblem.Missing) {
            Text(stringResource(R.string.backing_missing), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), modifier = Modifier.padding(bottom = 8.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(PlayButton)
                        .clip(CircleShape)
                        .background(colors.primaryContainer)
                        .clickable(enabled = !recording, role = Role.Button, onClickLabel = stringResource(if (backing.previewing) R.string.backing_preview_stop else R.string.backing_preview)) {
                            onIntent(PieceIntent.BackingPreviewClicked)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(if (backing.previewing) AppIcons.Pause else AppIcons.Play, contentDescription = null, tint = colors.onPrimaryContainer)
                }
                Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(backing.title.orEmpty(), color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp))
                    Text(Formats.duration(backing.durationMs), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceContainerHigh))
            HeadphonesRow(backing)
        }
        backing.problem?.takeIf { it != BackingProblem.Missing }?.let { ProblemLine(it, onIntent) }
    }
    if (backing.askingRemove) {
        AlertDialog(
            onDismissRequest = { onIntent(PieceIntent.BackingRemoveDismissed) },
            title = { Text(stringResource(R.string.backing_remove_title)) },
            text = { Text(stringResource(R.string.backing_remove_text)) },
            confirmButton = { TextButton(onClick = { onIntent(PieceIntent.BackingRemoveConfirmed) }) { Text(stringResource(R.string.backing_remove)) } },
            dismissButton = { TextButton(onClick = { onIntent(PieceIntent.BackingRemoveDismissed) }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** Which headphones the backing goes to, or that there are none (spec 3.32). What they lag is set per take on «Звук». */
@Composable
private fun HeadphonesRow(backing: BackingUi) {
    val colors = MaterialTheme.colorScheme
    val route = backing.route
    val headphones = route.output.isHeadphones
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppIcon(AppIcons.Headphones, contentDescription = null, tint = if (headphones) colors.primary else colors.onSurfaceVariant, size = 20.dp)
        Text(
            if (headphones) stringResource(R.string.backing_headphones_wired, route.deviceName ?: stringResource(R.string.backing_headphones)) else stringResource(R.string.backing_no_headphones),
            color = if (headphones) colors.onSurface else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProblemLine(problem: BackingProblem, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text = when (problem) {
        BackingProblem.Unreadable -> stringResource(R.string.backing_unreadable)
        BackingProblem.TooLong -> stringResource(R.string.backing_too_long)
        is BackingProblem.NoSpace -> stringResource(R.string.backing_no_space, problem.neededMb)
        BackingProblem.Missing -> stringResource(R.string.backing_missing)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), modifier = Modifier.weight(1f))
        TextButton(onClick = { onIntent(PieceIntent.BackingProblemDismissed) }) { Text(stringResource(R.string.backing_understood)) }
    }
}

/**
 * «С минусовкой» over the record button while the piece has a backing, and — with it on and no headphones — why the
 * button sleeps (spec 3.32).
 */
@Composable
fun BackingChipRow(backing: BackingUi, recording: Boolean, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier) {
    if (!backing.present) return
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val on = backing.enabled
        val stateText = stringResource(if (on) R.string.backing_chip_on else R.string.backing_chip_off)
        Row(
            modifier = Modifier
                .height(ChipHeight)
                .clip(RoundedCornerShape(ChipHeight / 2))
                .background(if (on) colors.primaryContainer else colors.surfaceContainerHigh)
                .clickable(enabled = !recording, role = Role.Switch) { onIntent(PieceIntent.BackingChipToggled) }
                .semantics { stateDescription = stateText }
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIcon(if (on) AppIcons.Check else AppIcons.Backing, contentDescription = null, tint = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant, size = 16.dp)
            Text(stringResource(R.string.backing_chip), color = if (on) colors.onPrimaryContainer else colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
        when {
            backing.wanted && !backing.route.output.isHeadphones -> Text(
                stringResource(R.string.backing_needs_headphones),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
            backing.wanted && backing.preparing -> Text(
                stringResource(R.string.backing_preparing),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
    }
}

/** The thin bar of the backing under the timer of a take made under it: «1:12 / 3:40». */
@Composable
fun BackingProgressLine(playedMs: Long, durationMs: Long) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(AppIcons.Backing, contentDescription = null, tint = colors.onSurfaceVariant, size = 14.dp)
            Text(stringResource(R.string.backing_block_title), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), modifier = Modifier.weight(1f))
            Text(
                "${Formats.duration(playedMs.coerceAtMost(durationMs))} / ${Formats.duration(durationMs)}",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        val fraction = if (durationMs > 0) (playedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(colors.onSurfaceVariant))
        }
    }
}
