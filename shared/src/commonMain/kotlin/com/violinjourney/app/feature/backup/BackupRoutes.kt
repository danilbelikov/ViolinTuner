package com.violinjourney.app.feature.backup

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.analytics_row_caption
import com.violinjourney.app.shared.resources.backup_file_name
import com.violinjourney.app.shared.resources.backup_new_records_few
import com.violinjourney.app.shared.resources.backup_new_records_many
import com.violinjourney.app.shared.resources.backup_new_records_one
import com.violinjourney.app.shared.resources.backup_row_last
import com.violinjourney.app.shared.resources.backup_row_never
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_running
import com.violinjourney.app.shared.resources.backup_row_save
import com.violinjourney.app.shared.resources.backup_row_stale
import com.violinjourney.app.shared.resources.backup_row_total
import com.violinjourney.app.shared.resources.privacy_row
import com.violinjourney.app.shared.resources.privacy_row_caption
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone


/** «Интонация · копия · 20 сентября 2026.zip» — a name a person recognises in a folder a year later. */
@Composable
private fun backupFileName(): String {
    val today = SystemWallClock.today()
    return stringResource(Res.string.backup_file_name, Formats.dayAndMonth(today) + " " + today.year)
}

@Composable
fun BackupRoute(onClose: () -> Unit, viewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val fileName = backupFileName()
    // The place is the person's to pick, in the system's own window; the app gets a stream into it and needs no permission.
    val system = rememberBackupSystem(onPlacePicked = { uri -> viewModel.onIntent(BackupIntent.PlacePicked(uri, fileName)) })
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    BackupEffect.PickPlace -> system.pickPlace(fileName)
                    is BackupEffect.ShareFile -> system.shareFile(effect.path)
                    BackupEffect.Close -> currentOnClose()
                }
            }
        }
    }
    BackHandler { viewModel.onIntent(BackupIntent.BackClicked) }
    BackupScreen(state = state, fileName = fileName, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun RestoreRoute(onClose: () -> Unit, onOpenBackup: () -> Unit, viewModel: RestoreViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenBackup by rememberUpdatedState(onOpenBackup)
    val system = rememberBackupSystem(onCopyPicked = { uri -> viewModel.onIntent(RestoreIntent.FilePicked(uri)) })
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    RestoreEffect.PickFile -> system.pickCopy()
                    RestoreEffect.OpenBackup -> currentOnOpenBackup()
                    RestoreEffect.Close -> currentOnClose()
                    RestoreEffect.Restart -> system.restart()
                }
            }
        }
    }
    // while the copy is being brought back there is nowhere to go back to that would mean anything
    BackHandler(enabled = state.job.let { it !is com.violinjourney.app.core.backup.BackupJob.Idle }) {}
    RestoreScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The block «Данные» of the settings (handoff 21a): two ways in and a line of honesty. An old
 * copy says so in words, in the same colour — no badge, no dot on the tab: the app asks for nothing.
 */
@Composable
fun DataBlock(
    onOpenBackup: () -> Unit,
    onOpenRestore: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    /** «Помогать улучшать приложение» (spec 3.34): the state belongs to the settings, the line belongs here. */
    analyticsEnabled: Boolean = true,
    onAnalyticsChange: (Boolean) -> Unit = {},
    viewModel: DataBlockViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val system = rememberBackupSystem(onCopyPicked = { uri -> uri?.let(onOpenRestore) })
    val running = state.runningPercent
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp))) {
        val caption = when {
            running != null && !state.restoring -> stringResource(Res.string.backup_row_running, running)
            state.lastBackupAtEpochMs == null -> stringResource(Res.string.backup_row_never)
            state.newSinceStale > 0 -> stringResource(
                Res.string.backup_row_stale,
                Formats.dayAndMonth(state.lastBackupAtEpochMs!!, TimeZone.currentSystemDefault()),
                plural(state.newSinceStale, Res.string.backup_new_records_one, Res.string.backup_new_records_few, Res.string.backup_new_records_many),
            )
            else -> stringResource(Res.string.backup_row_last, Formats.dayAndMonth(state.lastBackupAtEpochMs!!, TimeZone.currentSystemDefault()))
        }
        DataRow(AppIcons.SaveCopy, stringResource(Res.string.backup_row_save), caption, progress = running?.takeIf { !state.restoring }, onClick = onOpenBackup)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        DataRow(
            icon = AppIcons.Restore,
            title = stringResource(Res.string.backup_row_restore),
            caption = running?.takeIf { state.restoring }?.let { stringResource(Res.string.backup_row_running, it) },
            progress = running?.takeIf { state.restoring },
            // a restore on its way is come back to; otherwise the way in is the system's «Открыть»
            onClick = { if (state.restoring) onOpenRestore("") else system.pickCopy() },
        )
        state.totalBytes?.let { bytes ->
            Text(
                text = stringResource(Res.string.backup_row_total, Formats.fileSize(bytes)),
                modifier = Modifier.heightIn(min = 40.dp).padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        AnalyticsRow(enabled = analyticsEnabled, onChange = onAnalyticsChange)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        DataRow(
            icon = AppIcons.Lock,
            title = stringResource(Res.string.privacy_row),
            caption = stringResource(Res.string.privacy_row_caption),
            progress = null,
            captionLines = 2,
            onClick = system.openPrivacyPolicy,
        )
    }
}

/**
 * The last line of the block (spec 3.34): what leaves the phone stands next to what stays on it,
 * and one line does not deserve a section of its own. The icon is a stand-in until the set gets
 * the chart of the handoff.
 */
@Composable
private fun AnalyticsRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).toggleable(value = enabled, role = Role.Switch, onValueChange = onChange).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(AppIcons.Device, contentDescription = null, tint = colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.analytics_row), color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.analytics_row_caption), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun DataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, caption: String?, progress: Int?, captionLines: Int = 1, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            if (caption != null) Text(caption, color = colors.onSurfaceVariant, maxLines = captionLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES))
            if (progress != null) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = colors.primary, trackColor = colors.surfaceContainerHigh, drawStopIndicator = {})
            }
        }
        AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}
