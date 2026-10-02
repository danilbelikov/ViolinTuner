package com.violinjourney.app.feature.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.components.ListGroup
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.analytics_row_caption
import com.violinjourney.app.shared.resources.analytics_row_off
import com.violinjourney.app.shared.resources.backup_file_name
import com.violinjourney.app.shared.resources.backup_new_records_few
import com.violinjourney.app.shared.resources.backup_new_records_many
import com.violinjourney.app.shared.resources.backup_new_records_one
import com.violinjourney.app.shared.resources.backup_percent
import com.violinjourney.app.shared.resources.backup_row_app_size
import com.violinjourney.app.shared.resources.backup_row_last
import com.violinjourney.app.shared.resources.backup_row_never
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_save
import com.violinjourney.app.shared.resources.backup_row_saving
import com.violinjourney.app.shared.resources.backup_row_stale
import com.violinjourney.app.shared.resources.backup_row_wait_restore
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.privacy_row
import com.violinjourney.app.shared.resources.privacy_row_caption
import com.violinjourney.app.shared.resources.restore_row_caption
import com.violinjourney.app.shared.resources.restore_row_running
import com.violinjourney.app.shared.resources.restore_row_wait_copy
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

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
    BackupScreen(state = state, fileName = fileName, onIntent = viewModel::onIntent, modifier = modifier, goesOnInBackground = system.goesOnInBackground)
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
    // While the copy is being brought back there is nowhere to go back to that would mean anything; after a failure that
    // changed nothing «Назад» is «Закрыть», and the worst outcome keeps its two buttons. From the passport it goes back.
    val job = state.job
    BackHandler(enabled = job is BackupJob.Restoring || job is BackupJob.Restored || job is BackupJob.RestoreFailed) {
        if (job is BackupJob.RestoreFailed && job.dataIntact) viewModel.onIntent(RestoreIntent.CloseClicked)
    }
    RestoreScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier, goesOnInBackground = system.goesOnInBackground)
}

/**
 * The block «Данные» of the settings (spec 3.20, 3.34, 3.36.8): its view model, the system's «Открыть» and the policy in the browser
 * around [DataGroup]. «Настройки» put the label of the group over it; the slot and this signature are those of the hosts of both
 * platforms (their code passes [analyticsEnabled] on as it gets it — null until the settings are read, stage 121).
 */
@Composable
fun DataBlock(
    onOpenBackup: () -> Unit,
    onOpenRestore: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * «Помогать улучшать приложение» (spec 3.34): the state belongs to the settings, the line belongs here; null until the settings
     * are read — the row holds its place and says nothing yet (3.36.8 «Загрузка»).
     */
    analyticsEnabled: Boolean? = true,
    onAnalyticsChange: (Boolean) -> Unit = {},
    viewModel: DataBlockViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val system = rememberBackupSystem(onCopyPicked = { uri -> uri?.let(onOpenRestore) })
    DataGroup(
        state = state,
        analyticsEnabled = analyticsEnabled,
        onAnalyticsChange = onAnalyticsChange,
        onOpenBackup = onOpenBackup,
        // a restore on its way is come back to without a file: the screen watches the job
        onOpenRunningRestore = { onOpenRestore("") },
        onPickCopy = system.pickCopy,
        onOpenPrivacy = system.openPrivacyPolicy,
        modifier = modifier,
    )
}

/**
 * The rows of «Данные» (spec 3.36.8, 5.29 R8) in one group: «Сохранить копию» — the one icon of the screen in the accent — with when
 * the last copy was made and what the app weighs; «Восстановить из копии»; «Помогать улучшать приложение» with its switch; the policy.
 * A copy on its way turns its row into its progress — «Копия сохраняется», «56 % · Видео 7 из 12» and a thin bar — and a restore on
 * its way its own row; meanwhile the other one sleeps at 0.38 and its caption, whole, says why: one job at a time (spec 5.14). An old
 * copy says so in the same grey words — no badge, nothing red: the app asks for nothing. Until the date of the last copy is read the
 * caption of the copy keeps its line empty, so nothing blinks from «ещё не сохраняли» to a date; until the settings are read
 * ([analyticsEnabled] null) the statistics hold the place of their caption and their switch, and a tap does nothing. Stateless.
 */
@Composable
fun DataGroup(
    state: DataBlockState,
    analyticsEnabled: Boolean?,
    onAnalyticsChange: (Boolean) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenRunningRestore: () -> Unit,
    onPickCopy: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val copying = state.running?.takeIf { !it.restore }
    val restoring = state.running?.takeIf { it.restore }
    ListGroup(modifier) {
        ListRow(
            text = stringResource(if (copying != null) Res.string.backup_row_saving else Res.string.backup_row_save),
            onClick = onOpenBackup,
            caption = copyCaption(state),
            enabled = !state.saveWaits,
            // the icon alone in the accent: the words and the chevron are those of every row
            leading = { AppIcon(AppIcons.SaveCopy, contentDescription = null, tint = colors.primary) },
            below = copying?.let { { ThinBar(it.percent) } },
        )
        ListRow(
            text = stringResource(if (restoring != null) Res.string.restore_row_running else Res.string.backup_row_restore),
            // a restore on its way is come back to; otherwise the way in is the system's «Открыть»
            onClick = if (restoring != null) onOpenRunningRestore else onPickCopy,
            icon = AppIcons.Restore,
            caption = when {
                restoring != null -> runningWords(restoring)
                state.restoreWaits -> stringResource(Res.string.restore_row_wait_copy)
                else -> stringResource(Res.string.restore_row_caption)
            },
            enabled = !state.restoreWaits,
            below = restoring?.let { { ThinBar(it.percent) } },
        )
        ListRow(
            text = stringResource(Res.string.analytics_row),
            onClick = { analyticsEnabled?.let { onAnalyticsChange(!it) } },
            icon = AppIcons.Chart,
            caption = when (analyticsEnabled) {
                // not read yet: neither «on» nor «off» — the one who turned it off is not told it is on, and nothing flips
                null -> ""
                true -> stringResource(Res.string.analytics_row_caption)
                false -> stringResource(Res.string.analytics_row_off)
            },
            end = ListRowEnd.Toggle(analyticsEnabled),
        )
        ListRow(
            text = stringResource(Res.string.privacy_row),
            onClick = onOpenPrivacy,
            icon = AppIcons.Lock,
            caption = stringResource(Res.string.privacy_row_caption),
        )
    }
}

/**
 * The caption of «Сохранить копию»: the copy on its way; the restore it waits for; nothing yet — the date is not read; or when the
 * last copy was made («ещё не сохраняли», «с тех пор 9 новых записей» once it has grown old) and, once weighed, what the app weighs.
 */
@Composable
private fun copyCaption(state: DataBlockState): String {
    state.running?.let { running -> if (!running.restore) return runningWords(running) }
    if (state.saveWaits) return stringResource(Res.string.backup_row_wait_restore)
    if (!state.dateRead) return ""
    val zone = TimeZone.currentSystemDefault()
    val last = state.lastBackupAtEpochMs
    val made = when {
        last == null -> stringResource(Res.string.backup_row_never)
        state.newSinceStale > 0 -> stringResource(
            Res.string.backup_row_stale,
            Formats.dayAndMonth(last, zone),
            plural(state.newSinceStale, Res.string.backup_new_records_one, Res.string.backup_new_records_few, Res.string.backup_new_records_many),
        )
        else -> stringResource(Res.string.backup_row_last, Formats.dayAndMonth(last, zone))
    }
    val weight = state.totalBytes?.let { stringResource(Res.string.backup_row_app_size, Formats.fileSize(it)) }
    return listOfNotNull(made, weight).joinToString(stringResource(Res.string.dot_separator))
}

/** «56 % · Видео 7 из 12», «38 % · Восстанавливаем»: how far a job is and what it is doing. */
@Composable
private fun runningWords(running: DataRunning): String =
    stringResource(Res.string.backup_percent, running.percent) + stringResource(Res.string.dot_separator) + phaseWords(running.phase)

/**
 * The phase in the words of the row of «Данные» and of the line of phases of a copy ([BackupPhases.wordsOf]): «Видео 7 из 12»,
 * «Проверка», «Восстанавливаем».
 */
@Composable
internal fun phaseWords(phase: JobPhase): String = when (phase) {
    is JobPhase.Files -> stringResource(BackupPhases.wordsOf(phase), partShortName(phase.part), phase.index, phase.count)
    else -> stringResource(BackupPhases.wordsOf(phase))
}

/**
 * The thin bar of a job on its way under the caption of its row (spec 5.29 R8): 4 high, a corner of 2, the accent over
 * surfaceContainerHigh, no gap between them. Drawn only: the percent is in the caption, a reader hears it there.
 */
@Composable
private fun ThinBar(percent: Int) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(ThinBarHeight)
            .drawBehind {
                val corner = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = corner)
                val done = size.width * percent.coerceIn(0, PERCENT_ALL) / PERCENT_ALL
                if (done > 0f) drawRoundRect(fill, size = Size(done, size.height), cornerRadius = corner)
            },
    )
}

private val ThinBarHeight = 4.dp
private const val PERCENT_ALL = 100
