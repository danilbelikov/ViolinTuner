package com.example.violintuner.feature.backup

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.backup.BackupCandidate
import com.example.violintuner.core.backup.BackupFileProblem
import com.example.violintuner.core.backup.BackupJob
import com.example.violintuner.core.backup.BackupManifest
import com.example.violintuner.core.backup.BackupPart
import com.example.violintuner.core.backup.RestorePhase
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MaxContentWidth = 560.dp
private const val SWAP_MS = 250
private const val WARNING_ALPHA = 0.12f
private val TimeOfDay = DateTimeFormatter.ofPattern("HH:mm")

/** «Восстановить из копии» (spec 3.20, handoff 21d, 21e): the passport of the copy, what it replaces, the bringing back, and how it ended. Stateless. */
@Composable
fun RestoreScreen(state: RestoreState, onIntent: (RestoreIntent) -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    val colors = MaterialTheme.colorScheme
    val job = state.job
    val face = when {
        state.opening -> "opening"
        job is BackupJob.Restoring -> "progress"
        job is BackupJob.Restored -> "done"
        job is BackupJob.RestoreFailed -> "failed"
        else -> "passport"
    }
    if (face == "opening") {
        // The start screen of the app with one line under it: the process starts anew beneath, and the next one shows the same — one scene, not two.
        Column(modifier = modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
            Text(stringResource(R.string.restore_opening), modifier = Modifier.padding(top = 12.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
        }
        return
    }
    Column(modifier = modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTopBar(onBack = { onIntent(RestoreIntent.CloseClicked) }.takeIf { face == "passport" })
        Crossfade(targetState = face, animationSpec = tween(SWAP_MS), modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(), label = "restoreFace") { target ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    target == "progress" && job is BackupJob.Restoring -> Progress(job, onIntent)
                    target == "done" && job is BackupJob.Restored -> {
                        DoneMark(size = 48)
                        Text(stringResource(R.string.restore_done_title), color = colors.onSurface, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        Text(countsLine(job.manifest.counts), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
                    }
                    target == "failed" && job is BackupJob.RestoreFailed -> Failed(job, onIntent)
                    target == "passport" -> Passport(state, zone, onIntent)
                }
            }
        }
    }
    val ready = state.stage as? RestoreStage.Ready
    when (state.dialog) {
        // not "are you sure?" but what exactly goes: the numbers the person is about to lose
        RestoreDialog.REPLACE -> if (ready != null) ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            text = stringResource(R.string.restore_confirm_text, countsLine(ready.current.counts), Formats.dayAndMonth(ready.copy.manifest.createdAtEpochMs, zone)),
            safe = stringResource(R.string.backup_cancel),
            destructive = stringResource(R.string.restore_confirm_button),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        RestoreDialog.UNSAFE -> if (ready != null) ConfirmDialog(
            title = stringResource(R.string.restore_unsafe_title),
            text = stringResource(R.string.restore_unsafe_text, countsLine(ready.current.counts)),
            safe = stringResource(R.string.backup_cancel),
            destructive = stringResource(R.string.restore_unsafe_confirm),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        RestoreDialog.STOP -> ConfirmDialog(
            title = stringResource(R.string.backup_stop_title),
            text = stringResource(R.string.restore_failed_text),
            safe = stringResource(R.string.backup_stop_continue),
            destructive = stringResource(R.string.backup_stop_confirm),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        null -> Unit
    }
}

@Composable
private fun Passport(state: RestoreState, zone: ZoneId, onIntent: (RestoreIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    ScreenTitle(stringResource(R.string.restore_title))
    when (val stage = state.stage) {
        RestoreStage.Reading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
            Text(stringResource(R.string.restore_reading), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        is RestoreStage.Unfit -> {
            when (stage.problem) {
                BackupFileProblem.NotOurs -> ProblemBlock(stringResource(R.string.restore_not_ours_title), stringResource(R.string.restore_not_ours_text))
                BackupFileProblem.TooNew -> ProblemBlock(stringResource(R.string.restore_too_new_title), stringResource(R.string.restore_too_new_text))
                BackupFileProblem.Damaged -> ProblemBlock(stringResource(R.string.restore_damaged_title), stringResource(R.string.restore_damaged_text))
            }
            Button(onClick = { onIntent(RestoreIntent.PickAnotherClicked) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.restore_pick_another)) }
        }
        is RestoreStage.Ready -> Ready(stage, state.busy, zone, onIntent)
    }
}

@Composable
private fun Ready(stage: RestoreStage.Ready, busy: Boolean, zone: ZoneId, onIntent: (RestoreIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val copy = stage.copy
    val manifest = copy.manifest
    val overData = !stage.current.counts.isEmpty
    PassportCard(copy, manifest, zone)
    if (overData) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.restore_now_title), color = colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Text(
                text = countsLine(stage.current.counts, withLevel = true, withMedia = true) + stringResource(R.string.dot_separator) + Formats.fileSize(stage.current.totalBytes),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
            )
        }
        val destructive = ViolinTheme.destructive
        Column(modifier = Modifier.fillMaxWidth().background(destructive.copy(alpha = WARNING_ALPHA), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(AppIcons.Trash, contentDescription = null, tint = destructive)
                Text(stringResource(R.string.restore_warning), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
            // the answer to the warning lives inside it
            OutlinedButton(onClick = { onIntent(RestoreIntent.SaveFirstClicked) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                IconLabel(AppIcons.SaveCopy, stringResource(R.string.restore_save_first))
            }
        }
    } else {
        Text(stringResource(R.string.restore_empty_app), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    }
    if (busy) {
        Text(stringResource(R.string.restore_busy), modifier = Modifier.fillMaxWidth(), color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
    }
    if (copy.missingBytes > 0) {
        ProblemBlock(stringResource(R.string.restore_no_room_title, Formats.fileSize(copy.missingBytes)), stringResource(R.string.restore_no_room_text))
        Button(onClick = { onIntent(RestoreIntent.CloseClicked) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.restore_no_room_ok)) }
        // the dangerous way is a word in red, not a button that asks to be pressed
        TextButton(onClick = { onIntent(RestoreIntent.UnsafeClicked) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text(stringResource(R.string.restore_unsafe_button), color = ViolinTheme.destructive, textAlign = TextAlign.Center)
        }
    } else {
        Button(
            onClick = { onIntent(RestoreIntent.RestoreClicked) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = if (overData) ButtonDefaults.buttonColors(containerColor = ViolinTheme.destructive, contentColor = Color.White) else ButtonDefaults.buttonColors(),
        ) { IconLabel(AppIcons.Restore, stringResource(R.string.restore_button), iconSize = 20.dp) }
    }
}

@Composable
private fun PassportCard(copy: BackupCandidate.Copy, manifest: BackupManifest, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    val counts = manifest.counts
    val dot = stringResource(R.string.dot_separator)
    Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Archive, contentDescription = null, tint = colors.onSurfaceVariant)
            Column {
                Text(copy.fileName.orEmpty(), color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                Text(Formats.fileSize(copy.fileBytes ?: manifest.totalBytes), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES))
            }
        }
        HorizontalDivider(color = colors.surfaceContainerHigh)
        val made = Instant.ofEpochMilli(manifest.createdAtEpochMs).atZone(zone)
        IconLine(AppIcons.Calendar, stringResource(R.string.restore_made, Formats.dayAndMonth(manifest.createdAtEpochMs, zone) + " " + made.year, TimeOfDay.format(made)))
        if (manifest.device.isNotBlank()) IconLine(AppIcons.Device, manifest.device)
        IconLine(AppIcons.NoteOne, listOf(sessionsWord(counts.sessions), daysWord(counts.practiceDays), stringResource(R.string.backup_count_level, counts.level)).joinToString(dot))
        // what is not in the copy is said with the sign the app already has for "not there": one notion, one mark
        if (BackupPart.SHEETS in manifest.parts) {
            IconLine(AppIcons.Sheet, listOf(piecesWord(counts.pieces), plural(counts.pages, R.string.backup_count_pages_one, R.string.backup_count_pages_few, R.string.backup_count_pages_many)).joinToString(dot))
        } else {
            IconLine(AppIcons.Sheet, piecesWord(counts.pieces) + dot + stringResource(R.string.restore_without_sheets))
        }
        if (BackupPart.AUDIO in manifest.parts) {
            IconLine(AppIcons.Sound, plural(counts.withSound, R.string.backup_count_sound_one, R.string.backup_count_sound_few, R.string.backup_count_sound_many))
        } else if (counts.withSound > 0) {
            IconLine(AppIcons.VolumeOff, stringResource(R.string.restore_without_audio))
        }
        if (BackupPart.VIDEO in manifest.parts) {
            if (counts.videos > 0) IconLine(AppIcons.Video, stringResource(R.string.backup_count_video, counts.videos))
        } else if (counts.videos > 0) {
            IconLine(AppIcons.VideoOff, stringResource(R.string.restore_without_video))
        }
    }
}

@Composable
private fun Progress(job: BackupJob.Restoring, onIntent: (RestoreIntent) -> Unit) {
    ScreenTitle(stringResource(R.string.restore_progress_title))
    val stoppable = if (job.checked) job.phase == RestorePhase.VERIFYING else job.phase != RestorePhase.FINISHING
    JobProgress(
        phase = when (job.phase) {
            RestorePhase.VERIFYING -> stringResource(R.string.restore_phase_verifying)
            RestorePhase.EXTRACTING -> job.progress?.let { stringResource(R.string.restore_phase_extracting, partShortName(it.part), it.index) } ?: stringResource(R.string.restore_step_extracting)
            RestorePhase.FINISHING -> stringResource(R.string.restore_step_finishing)
        },
        progress = job.progress,
        remainingSec = job.remainingSec,
        leaveTitle = stringResource(R.string.restore_can_leave_title),
        // said in words: while this is only checking and unpacking, the person's data are still there
        leaveText = stringResource(if (stoppable) R.string.restore_can_stop else R.string.restore_cannot_stop),
        cancellable = stoppable,
        onCancel = { onIntent(RestoreIntent.CancelClicked) },
        steps = { Steps(job) },
    )
}

/** The phases above the percent: «Проверяем» is not the restoring itself yet, and the line shows it. */
@Composable
private fun Steps(job: BackupJob.Restoring) {
    val colors = MaterialTheme.colorScheme
    val steps = listOfNotNull(
        (RestorePhase.VERIFYING to R.string.restore_step_verifying).takeIf { job.checked },
        RestorePhase.EXTRACTING to R.string.restore_step_extracting,
        RestorePhase.FINISHING to R.string.restore_step_finishing,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { (phase, label) ->
            val reached = phase.ordinal <= job.phase.ordinal
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(if (reached) colors.primary else colors.surfaceContainerHigh, RoundedCornerShape(2.dp)))
                Text(
                    text = stringResource(label),
                    color = when {
                        phase == job.phase -> colors.onSurface
                        reached -> colors.onSurfaceVariant
                        else -> colors.outline
                    },
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun Failed(job: BackupJob.RestoreFailed, onIntent: (RestoreIntent) -> Unit) {
    ScreenTitle(stringResource(R.string.restore_progress_title))
    if (job.dataIntact) {
        ProblemBlock(stringResource(R.string.restore_failed_title), stringResource(R.string.restore_failed_text))
        TwoButtons(stringResource(R.string.backup_close), { onIntent(RestoreIntent.CloseClicked) }, stringResource(R.string.backup_retry), { onIntent(RestoreIntent.RetryClicked) })
    } else {
        // The worst frame, and an honest one: the data are gone — but the copy was checked whole before they went.
        ProblemBlock(stringResource(R.string.restore_failed_lost_title), stringResource(R.string.restore_failed_lost_text))
        Button(onClick = { onIntent(RestoreIntent.RetryClicked) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.restore_retry_same)) }
        TextButton(onClick = { onIntent(RestoreIntent.StartCleanClicked) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore_start_clean)) }
    }
}
