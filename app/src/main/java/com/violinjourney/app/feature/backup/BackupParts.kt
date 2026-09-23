package com.violinjourney.app.feature.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme

internal const val TABULAR_FIGURES = "tnum"
internal const val DISABLED_ALPHA = 0.38f
private const val SECONDS_PER_MINUTE = 60

@Composable
internal fun partColor(part: BackupPart): Color = with(ViolinTheme.backupColors) {
    when (part) {
        BackupPart.DATA -> data
        BackupPart.SHEETS -> sheets
        BackupPart.AUDIO -> audio
        BackupPart.VIDEO -> video
    }
}

@Composable
internal fun plural(count: Int, one: Int, few: Int, many: Int): String = stringResource(Formats.plural(count, one, few, many), count)

@Composable
internal fun sessionsWord(count: Int) = plural(count, R.string.backup_count_sessions_one, R.string.backup_count_sessions_few, R.string.backup_count_sessions_many)

@Composable
internal fun piecesWord(count: Int) = plural(count, R.string.backup_count_pieces_one, R.string.backup_count_pieces_few, R.string.backup_count_pieces_many)

@Composable
internal fun daysWord(count: Int) = plural(count, R.string.backup_count_days_one, R.string.backup_count_days_few, R.string.backup_count_days_many)

/** «23 записи · 5 произведений · 41 день занятий» — what would be lost, what is in a copy: the same words everywhere. */
@Composable
internal fun countsLine(counts: BackupCounts, withLevel: Boolean = false, withMedia: Boolean = false): String {
    val dot = stringResource(R.string.dot_separator)
    return listOfNotNull(
        sessionsWord(counts.sessions),
        piecesWord(counts.pieces),
        daysWord(counts.practiceDays),
        stringResource(R.string.backup_count_level, counts.level).takeIf { withLevel },
        plural(counts.withSound, R.string.backup_count_sound_one, R.string.backup_count_sound_few, R.string.backup_count_sound_many).takeIf { withMedia && counts.withSound > 0 },
        stringResource(R.string.backup_count_video, counts.videos).takeIf { withMedia && counts.videos > 0 },
    ).joinToString(dot)
}

/** «около 3 мин» past a minute, «несколько секунд» below: an estimate is not a stopwatch. */
@Composable
internal fun remainingWords(seconds: Int): String = stringResource(
    R.string.backup_remaining,
    if (seconds < SECONDS_PER_MINUTE) stringResource(R.string.backup_remaining_seconds) else stringResource(R.string.backup_remaining_minutes, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE),
)

@Composable
internal fun partShortName(part: BackupPart): String = stringResource(
    when (part) {
        BackupPart.DATA -> R.string.backup_part_data_short
        BackupPart.SHEETS -> R.string.backup_part_sheets
        BackupPart.AUDIO -> R.string.backup_part_audio_short
        BackupPart.VIDEO -> R.string.backup_part_video
    },
)

@Composable
internal fun ScreenTopBar(onBack: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        if (onBack != null) {
            val label = stringResource(R.string.session_back)
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
internal fun ScreenTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold))
}

/**
 * The progress of a long job (handoff 21c): a screen, not a sheet — it is there for minutes, left
 * and come back to. The phase in words, the percent large, a bar, how much of how much, and the
 * sentence that lets the person put the phone down.
 */
@Composable
internal fun JobProgress(
    phase: String,
    progress: BackupProgress?,
    remainingSec: Int?,
    leaveTitle: String,
    leaveText: String,
    cancellable: Boolean,
    onCancel: () -> Unit,
    steps: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fraction = progress?.fraction ?: 0f
    steps?.invoke()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(phase, modifier = Modifier.weight(1f).padding(bottom = 6.dp), color = colors.onSurface, maxLines = 2, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        Text(
            text = stringResource(R.string.backup_percent, (fraction * 100).toInt()),
            color = colors.primary,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
    if (progress == null) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = colors.primary, trackColor = colors.surfaceContainerHigh)
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = colors.primary, trackColor = colors.surfaceContainerHigh, drawStopIndicator = {})
    }
    Text(
        // the line keeps its height while the speed is still being measured
        text = listOfNotNull(
            progress?.let { stringResource(R.string.backup_done_of, Formats.fileSize(it.doneBytes), Formats.fileSize(it.totalBytes)) },
            remainingSec?.let { remainingWords(it) },
        ).joinToString(stringResource(R.string.dot_separator)),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES),
    )
    Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(leaveTitle, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        Text(leaveText, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    }
    OutlinedButton(onClick = onCancel, enabled = cancellable, modifier = Modifier.fillMaxWidth().height(48.dp).alpha(if (cancellable) 1f else DISABLED_ALPHA)) {
        Text(stringResource(R.string.backup_cancel))
    }
}

/** «Готово» of either job: a tick in a circle and what it was about. */
@Composable
internal fun DoneMark(size: Int = 64) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
        AppIcon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, size = (size / 2).dp)
    }
}

/** What went wrong is a line on the same screen, not a toast (handoff 21c6) — and what can be done about it. */
@Composable
internal fun ProblemBlock(title: String, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(AppIcons.Alert, contentDescription = null, tint = ViolinTheme.destructive)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        }
    }
}

@Composable
internal fun TwoButtons(quiet: String, onQuiet: () -> Unit, loud: String, onLoud: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onQuiet, modifier = Modifier.weight(1f).height(48.dp)) { Text(quiet, maxLines = 1) }
        Button(onClick = onLoud, modifier = Modifier.weight(1f).height(48.dp)) { Text(loud, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A plain question with the safe answer on the left and the destructive one, in its colour, on the right (handoff `sizes`, «Диалоги»). */
@Composable
internal fun ConfirmDialog(title: String, text: String, safe: String, destructive: String, onSafe: () -> Unit, onDestructive: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSafe,
        title = { Text(title) },
        text = { Text(text) },
        dismissButton = { TextButton(onClick = onSafe) { Text(safe) } },
        confirmButton = { TextButton(onClick = onDestructive) { Text(destructive, color = ViolinTheme.destructive) } },
    )
}

@Composable
internal fun IconLine(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, contentDescription = null, tint = tint, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES))
    }
}
