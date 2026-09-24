package com.violinjourney.app.feature.backup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.R
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import java.io.File
import kotlin.system.exitProcess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

private const val ZIP_TYPE = "application/zip"
private const val FILES_AUTHORITY_SUFFIX = ".files"

/** What the system's «Открыть» is asked for: a copy is a zip, but file managers and clouds call a zip all sorts of things. */
val BACKUP_FILE_TYPES = arrayOf(ZIP_TYPE, "application/x-zip-compressed", "application/octet-stream", "*/*")

/** «Интонация · копия · 20 сентября 2026.zip» — a name a person recognises in a folder a year later. */
@Composable
private fun backupFileName(): String {
    val today = SystemWallClock.today()
    return stringResource(R.string.backup_file_name, Formats.dayAndMonth(today) + " " + today.year)
}

@Composable
fun BackupRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val fileName = backupFileName()
    // The place is the person's to pick, in the system's own window; the app gets a stream into it and needs no permission.
    val place = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri ->
        viewModel.onIntent(BackupIntent.PlacePicked(uri?.toString(), fileName))
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    BackupEffect.PickPlace -> place.launch(fileName)
                    is BackupEffect.ShareFile -> context.shareBackup(File(effect.path))
                    BackupEffect.Close -> currentOnClose()
                }
            }
        }
    }
    BackHandler { viewModel.onIntent(BackupIntent.BackClicked) }
    BackupScreen(state = state, fileName = fileName, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun RestoreRoute(onClose: () -> Unit, onOpenBackup: () -> Unit, modifier: Modifier = Modifier, viewModel: RestoreViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenBackup by rememberUpdatedState(onOpenBackup)
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onIntent(RestoreIntent.FilePicked(uri?.toString()))
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    RestoreEffect.PickFile -> file.launch(BACKUP_FILE_TYPES)
                    RestoreEffect.OpenBackup -> currentOnOpenBackup()
                    RestoreEffect.Close -> currentOnClose()
                    RestoreEffect.Restart -> context.restartApp()
                }
            }
        }
    }
    // while the copy is being brought back there is nowhere to go back to that would mean anything
    BackHandler(enabled = state.job.let { it !is com.violinjourney.app.core.backup.BackupJob.Idle }) {}
    RestoreScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

/**
 * The process starts anew: the copy lies unpacked and marked, and the start of the next process
 * puts it in place before anything has opened the database (spec 5.14). The launcher activity is
 * asked for first, the process ends after.
 */
private fun Context.restartApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) startActivity(intent)
    exitProcess(0)
}

private fun Context.shareBackup(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName$FILES_AUTHORITY_SUFFIX", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = ZIP_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(intent, getString(R.string.backup_share)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.share_no_app, Toast.LENGTH_SHORT).show()
    }
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
    viewModel: DataBlockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { onOpenRestore(it.toString()) } }
    val running = state.runningPercent
    Column(modifier = modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp))) {
        val caption = when {
            running != null && !state.restoring -> stringResource(R.string.backup_row_running, running)
            state.lastBackupAtEpochMs == null -> stringResource(R.string.backup_row_never)
            state.newSinceStale > 0 -> stringResource(
                R.string.backup_row_stale,
                Formats.dayAndMonth(state.lastBackupAtEpochMs!!, TimeZone.currentSystemDefault()),
                plural(state.newSinceStale, R.string.backup_new_records_one, R.string.backup_new_records_few, R.string.backup_new_records_many),
            )
            else -> stringResource(R.string.backup_row_last, Formats.dayAndMonth(state.lastBackupAtEpochMs!!, TimeZone.currentSystemDefault()))
        }
        DataRow(AppIcons.SaveCopy, stringResource(R.string.backup_row_save), caption, progress = running?.takeIf { !state.restoring }, onClick = onOpenBackup)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        DataRow(
            icon = AppIcons.Restore,
            title = stringResource(R.string.backup_row_restore),
            caption = running?.takeIf { state.restoring }?.let { stringResource(R.string.backup_row_running, it) },
            progress = running?.takeIf { state.restoring },
            // a restore on its way is come back to; otherwise the way in is the system's «Открыть»
            onClick = { if (state.restoring) onOpenRestore("") else file.launch(BACKUP_FILE_TYPES) },
        )
        state.totalBytes?.let { bytes ->
            Text(
                text = stringResource(R.string.backup_row_total, Formats.fileSize(bytes)),
                modifier = Modifier.heightIn(min = 40.dp).padding(start = 56.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        AnalyticsRow(enabled = analyticsEnabled, onChange = onAnalyticsChange)
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
        val context = LocalContext.current
        DataRow(
            icon = AppIcons.Lock,
            title = stringResource(R.string.privacy_row),
            caption = stringResource(R.string.privacy_row_caption),
            progress = null,
            captionLines = 2,
            onClick = { context.openPrivacyPolicy() },
        )
    }
}

/**
 * The policy the stores link to (spec 3.34): Google Play wants it inside an app that hears the
 * microphone and sends statistics, not only on the store's page. One page in both languages.
 */
private const val PRIVACY_POLICY_URL = "https://danilbelikov.github.io/violin-journey/privacy/"

private fun Context.openPrivacyPolicy() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.privacy_no_browser, Toast.LENGTH_SHORT).show()
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
            Text(stringResource(R.string.analytics_row), color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.analytics_row_caption), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
