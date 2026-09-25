package com.violinjourney.app.feature.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_busy_recording
import com.violinjourney.app.shared.resources.backup_can_leave_text
import com.violinjourney.app.shared.resources.backup_can_leave_title
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.backup_count_pages_few
import com.violinjourney.app.shared.resources.backup_count_pages_many
import com.violinjourney.app.shared.resources.backup_count_pages_one
import com.violinjourney.app.shared.resources.backup_count_takes_few
import com.violinjourney.app.shared.resources.backup_count_takes_many
import com.violinjourney.app.shared.resources.backup_count_takes_one
import com.violinjourney.app.shared.resources.backup_count_trophies_few
import com.violinjourney.app.shared.resources.backup_count_trophies_many
import com.violinjourney.app.shared.resources.backup_count_trophies_one
import com.violinjourney.app.shared.resources.backup_done
import com.violinjourney.app.shared.resources.backup_failed_gone_text
import com.violinjourney.app.shared.resources.backup_failed_gone_title
import com.violinjourney.app.shared.resources.backup_failed_space_inside
import com.violinjourney.app.shared.resources.backup_failed_space_text
import com.violinjourney.app.shared.resources.backup_failed_space_title
import com.violinjourney.app.shared.resources.backup_failed_text
import com.violinjourney.app.shared.resources.backup_failed_title
import com.violinjourney.app.shared.resources.backup_nothing_text
import com.violinjourney.app.shared.resources.backup_nothing_title
import com.violinjourney.app.shared.resources.backup_part_always
import com.violinjourney.app.shared.resources.backup_part_audio
import com.violinjourney.app.shared.resources.backup_part_data
import com.violinjourney.app.shared.resources.backup_part_sheets
import com.violinjourney.app.shared.resources.backup_part_video
import com.violinjourney.app.shared.resources.backup_phase_data
import com.violinjourney.app.shared.resources.backup_phase_files
import com.violinjourney.app.shared.resources.backup_phase_verifying_file
import com.violinjourney.app.shared.resources.backup_retry
import com.violinjourney.app.shared.resources.backup_save_to
import com.violinjourney.app.shared.resources.backup_saved_advice
import com.violinjourney.app.shared.resources.backup_saved_place
import com.violinjourney.app.shared.resources.backup_saved_title
import com.violinjourney.app.shared.resources.backup_saving_button
import com.violinjourney.app.shared.resources.backup_saving_title
import com.violinjourney.app.shared.resources.backup_share
import com.violinjourney.app.shared.resources.backup_stop_confirm
import com.violinjourney.app.shared.resources.backup_stop_continue
import com.violinjourney.app.shared.resources.backup_stop_text
import com.violinjourney.app.shared.resources.backup_stop_title
import com.violinjourney.app.shared.resources.backup_text
import com.violinjourney.app.shared.resources.backup_title
import com.violinjourney.app.shared.resources.backup_too_big_to_share
import com.violinjourney.app.shared.resources.backup_total
import com.violinjourney.app.shared.resources.backup_total_value
import com.violinjourney.app.shared.resources.backup_without_audio
import com.violinjourney.app.shared.resources.backup_without_sheets
import com.violinjourney.app.shared.resources.backup_without_video
import com.violinjourney.app.shared.resources.dot_separator
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.theme.ViolinTheme

private val MaxContentWidth = 560.dp
private const val SWAP_MS = 250
private const val MIN_SHARE_WEIGHT = 0.012f

/** «Копия данных» (spec 3.20, handoff 21b, 21c): what goes in, the making of it, and how it ended. Stateless. */
@Composable
fun BackupScreen(state: BackupState, fileName: String, onIntent: (BackupIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val job = state.job
    // a short copy shows no progress screen — the button says «Сохраняем…» instead (nothing blinks)
    val face = when {
        job is BackupJob.Saving && job.visible -> "progress"
        job is BackupJob.Saved -> "saved"
        job is BackupJob.SaveFailed -> "failed"
        else -> "choose"
    }
    Column(modifier = modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTopBar(onBack = { onIntent(BackupIntent.BackClicked) }.takeIf { face != "saved" })
        Crossfade(targetState = face, animationSpec = tween(SWAP_MS), modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(), label = "backupFace") { target ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    target == "progress" && job is BackupJob.Saving -> {
                        ScreenTitle(stringResource(Res.string.backup_saving_title))
                        val progress = job.progress
                        JobProgress(
                            phase = when {
                                job.verifying -> stringResource(Res.string.backup_phase_verifying_file)
                                progress == null || progress.part == BackupPart.DATA -> stringResource(Res.string.backup_phase_data)
                                else -> stringResource(Res.string.backup_phase_files, partShortName(progress.part), progress.index, progress.count)
                            },
                            progress = progress,
                            remainingSec = job.remainingSec,
                            leaveTitle = stringResource(Res.string.backup_can_leave_title),
                            leaveText = stringResource(Res.string.backup_can_leave_text),
                            cancellable = !job.verifying,
                            onCancel = { onIntent(BackupIntent.CancelClicked) },
                        )
                    }
                    target == "saved" && job is BackupJob.Saved -> Saved(job, onIntent)
                    target == "failed" && job is BackupJob.SaveFailed -> Failed(job, onIntent)
                    target == "choose" -> Choose(state, fileName, onIntent)
                }
            }
        }
    }
    if (state.stopDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.backup_stop_title),
            text = stringResource(Res.string.backup_stop_text),
            safe = stringResource(Res.string.backup_stop_continue),
            destructive = stringResource(Res.string.backup_stop_confirm),
            onSafe = { onIntent(BackupIntent.StopDismissed) },
            onDestructive = { onIntent(BackupIntent.StopConfirmed) },
        )
    }
}

@Composable
private fun Choose(state: BackupState, fileName: String, onIntent: (BackupIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val contents = state.contents
    ScreenTitle(stringResource(Res.string.backup_title))
    Text(stringResource(Res.string.backup_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    if (contents == null) return
    if (state.nothingToSave) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(AppIcons.Archive, contentDescription = null, tint = colors.onSurfaceVariant, size = 32.dp)
            Text(stringResource(Res.string.backup_nothing_title), color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(stringResource(Res.string.backup_nothing_text), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        }
        return
    }
    val saving = state.job is BackupJob.Saving
    Column {
        PartRow(BackupPart.DATA, stringResource(Res.string.backup_part_data), dataLine(contents), contents, state, onIntent)
        PartRow(BackupPart.SHEETS, stringResource(Res.string.backup_part_sheets), listOf(piecesWord(contents.counts.pieces), plural(contents.counts.pages, Res.string.backup_count_pages_one, Res.string.backup_count_pages_few, Res.string.backup_count_pages_many)).joinToString(stringResource(Res.string.dot_separator)), contents, state, onIntent)
        PartRow(BackupPart.AUDIO, stringResource(Res.string.backup_part_audio), sessionsWord(contents.counts.withSound), contents, state, onIntent)
        PartRow(BackupPart.VIDEO, stringResource(Res.string.backup_part_video), plural(contents.counts.videos, Res.string.backup_count_takes_one, Res.string.backup_count_takes_few, Res.string.backup_count_takes_many), contents, state, onIntent)
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(stringResource(Res.string.backup_total), modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
        Text(
            text = stringResource(Res.string.backup_total_value, Formats.fileSize(state.totalBytes)),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
    WeightBar(contents, state.parts)
    // what is left behind has a consequence, and it is said once, where the choice is made
    val left = BackupPart.entries.filter { it != BackupPart.DATA && it !in state.parts && (contents.bytes[it] ?: 0) > 0 }
    AnimatedVisibility(visible = left.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            left.forEach { part ->
                Text(
                    text = stringResource(
                        when (part) {
                            BackupPart.VIDEO -> Res.string.backup_without_video
                            BackupPart.AUDIO -> Res.string.backup_without_audio
                            else -> Res.string.backup_without_sheets
                        },
                    ),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                )
            }
        }
    }
    val enabled = !state.busy && !saving
    Button(onClick = { onIntent(BackupIntent.SaveClicked) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        IconLabel(AppIcons.SaveCopy, stringResource(if (saving) Res.string.backup_saving_button else Res.string.backup_save_to), iconSize = 20.dp)
    }
    when {
        // the button stays where it is, and the reason stands under it: the function is there, only not now
        state.busy -> Text(stringResource(Res.string.backup_busy_recording), modifier = Modifier.fillMaxWidth(), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        state.canShare -> TextButton(onClick = { onIntent(BackupIntent.ShareClicked(fileName)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            IconLabel(AppIcons.Share, stringResource(Res.string.backup_share))
        }
        else -> Text(stringResource(Res.string.backup_too_big_to_share), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}

@Composable
private fun dataLine(contents: BackupContents): String = listOf(
    sessionsWord(contents.counts.sessions),
    daysWord(contents.counts.practiceDays),
    plural(contents.counts.trophies, Res.string.backup_count_trophies_one, Res.string.backup_count_trophies_few, Res.string.backup_count_trophies_many),
).joinToString(stringResource(Res.string.dot_separator))

/** A line of the contents in the words a person thinks in — «видео», «звук» — with how many and how heavy. */
@Composable
private fun PartRow(part: BackupPart, title: String, caption: String, contents: BackupContents, state: BackupState, onIntent: (BackupIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val included = part in state.parts
    val fixed = part == BackupPart.DATA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (fixed) Modifier else Modifier.toggleable(value = included, enabled = state.job == BackupJob.Idle, role = Role.Switch) { onIntent(BackupIntent.PartToggled(part)) })
            .alpha(if (included) 1f else 0.7f)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(caption, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES))
        }
        Box(Modifier.size(8.dp).background(if (included) partColor(part) else ViolinTheme.backupColors.off, CircleShape))
        Text(
            text = Formats.fileSize(contents.bytes[part] ?: 0),
            modifier = Modifier.widthIn(min = 58.dp),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
        if (fixed) {
            Text(stringResource(Res.string.backup_part_always), modifier = Modifier.width(52.dp), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
        } else {
            Switch(checked = included, onCheckedChange = null)
        }
    }
    HorizontalDivider(color = colors.surfaceContainerHigh)
}

/** The weight seen as weight: with three gigabytes of video out of three and a half, the bar is nearly one colour — which is the truth. A share left behind does not vanish, it greys. */
@Composable
private fun WeightBar(contents: BackupContents, parts: Set<BackupPart>) {
    val total = contents.totalBytes.coerceAtLeast(1).toFloat()
    Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        BackupPart.entries.forEach { part ->
            val bytes = contents.bytes[part] ?: 0
            if (bytes > 0) {
                // a megabyte beside gigabytes would not be seen at all
                Box(Modifier.weight((bytes / total).coerceAtLeast(MIN_SHARE_WEIGHT)).fillMaxSize().background(if (part in parts) partColor(part) else ViolinTheme.backupColors.off))
            }
        }
    }
}

@Composable
private fun Saved(job: BackupJob.Saved, onIntent: (BackupIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    DoneMark()
    ScreenTitle(stringResource(Res.string.backup_saved_title))
    Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Archive, contentDescription = null, tint = colors.onSurfaceVariant)
            Column {
                Text(job.fileName, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                val size = Formats.fileSize(job.bytes)
                Text(job.place?.let { stringResource(Res.string.backup_saved_place, size, it) } ?: size, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES))
            }
        }
        HorizontalDivider(color = colors.surfaceContainerHigh)
        Text(countsLine(job.manifest.counts, withMedia = true), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    }
    Text(stringResource(Res.string.backup_saved_advice), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    Button(onClick = { onIntent(BackupIntent.DoneClicked) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(Res.string.backup_done)) }
}

@Composable
private fun Failed(job: BackupJob.SaveFailed, onIntent: (BackupIntent) -> Unit) {
    ScreenTitle(stringResource(Res.string.backup_saving_title))
    when (job.reason) {
        SaveFailure.NO_SPACE -> ProblemBlock(
            stringResource(Res.string.backup_failed_space_title),
            // inside the phone — the archive for «Отправить…» did not fit; outside — the place that was picked is full
            if (job.missingBytes > 0) stringResource(Res.string.backup_failed_space_inside, Formats.fileSize(job.missingBytes)) else stringResource(Res.string.backup_failed_space_text),
        )
        SaveFailure.UNAVAILABLE -> ProblemBlock(stringResource(Res.string.backup_failed_gone_title), stringResource(Res.string.backup_failed_gone_text))
        SaveFailure.FAILED -> ProblemBlock(stringResource(Res.string.backup_failed_title), stringResource(Res.string.backup_failed_text))
    }
    TwoButtons(stringResource(Res.string.backup_close), { onIntent(BackupIntent.DoneClicked) }, stringResource(Res.string.backup_retry), { onIntent(BackupIntent.RetryClicked) })
}
