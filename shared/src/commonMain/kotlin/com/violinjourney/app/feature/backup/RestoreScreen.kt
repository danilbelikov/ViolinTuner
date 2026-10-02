package com.violinjourney.app.feature.backup

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupFileProblem
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.stoppable
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.ScreenHeader
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_cancel
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.backup_retry
import com.violinjourney.app.shared.resources.backup_stop_confirm
import com.violinjourney.app.shared.resources.backup_stop_continue
import com.violinjourney.app.shared.resources.backup_stop_title
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.restore_busy
import com.violinjourney.app.shared.resources.restore_busy_saving
import com.violinjourney.app.shared.resources.restore_button
import com.violinjourney.app.shared.resources.restore_can_leave_title
import com.violinjourney.app.shared.resources.restore_can_stop
import com.violinjourney.app.shared.resources.restore_cannot_stop
import com.violinjourney.app.shared.resources.restore_confirm_button
import com.violinjourney.app.shared.resources.restore_confirm_text
import com.violinjourney.app.shared.resources.restore_confirm_title
import com.violinjourney.app.shared.resources.restore_copy_from
import com.violinjourney.app.shared.resources.restore_copy_version
import com.violinjourney.app.shared.resources.restore_damaged_text
import com.violinjourney.app.shared.resources.restore_damaged_title
import com.violinjourney.app.shared.resources.restore_done_title
import com.violinjourney.app.shared.resources.restore_empty_app
import com.violinjourney.app.shared.resources.restore_failed_lost_text
import com.violinjourney.app.shared.resources.restore_failed_lost_title
import com.violinjourney.app.shared.resources.restore_failed_text
import com.violinjourney.app.shared.resources.restore_failed_title
import com.violinjourney.app.shared.resources.restore_no_room_any_title
import com.violinjourney.app.shared.resources.restore_no_room_even_unsafe_text
import com.violinjourney.app.shared.resources.restore_no_room_free_text
import com.violinjourney.app.shared.resources.restore_no_room_ok
import com.violinjourney.app.shared.resources.restore_no_room_text
import com.violinjourney.app.shared.resources.restore_no_room_title
import com.violinjourney.app.shared.resources.restore_not_ours_text
import com.violinjourney.app.shared.resources.restore_not_ours_title
import com.violinjourney.app.shared.resources.restore_now_title
import com.violinjourney.app.shared.resources.restore_opening
import com.violinjourney.app.shared.resources.restore_pick_another
import com.violinjourney.app.shared.resources.restore_progress_title
import com.violinjourney.app.shared.resources.restore_reading
import com.violinjourney.app.shared.resources.restore_replaces
import com.violinjourney.app.shared.resources.restore_retry_same
import com.violinjourney.app.shared.resources.restore_save_first
import com.violinjourney.app.shared.resources.restore_start_clean
import com.violinjourney.app.shared.resources.restore_stay_title
import com.violinjourney.app.shared.resources.restore_title
import com.violinjourney.app.shared.resources.restore_too_new_text
import com.violinjourney.app.shared.resources.restore_too_new_title
import com.violinjourney.app.shared.resources.restore_unsafe_button
import com.violinjourney.app.shared.resources.restore_unsafe_confirm
import com.violinjourney.app.shared.resources.restore_unsafe_text
import com.violinjourney.app.shared.resources.restore_unsafe_title
import com.violinjourney.app.shared.resources.restore_warning
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The faces of «Восстановить из копии»; one crossfades into the next. «Открываем ваши данные…» stands apart, as the start of the app. */
private enum class RestoreFace { PASSPORT, PROGRESS, DONE, FAILED }

private const val SWAP_MS = 250

/** The coral of the ground of the warning, over surfaceContainer (spec 5.29 R8: «DangerSoft на 0,12 поверх surface»). */
private const val WARNING_ALPHA = 0.12f

// The passport and «заменит» (spec 5.29 R8, «Восстановление»).
private val PassportPadding = 16.dp
private val OriginTop = 2.dp
private val ReplacesHeight = 30.dp
private val ReplacesArrow = 18.dp
private val ReplacesGap = 4.dp
private val EmptyAppTop = 12.dp
private val ReadingTop = 12.dp
private val ReadingSpinner = 20.dp
private val ReadingGap = 12.dp
private val SpinnerStroke = 2.dp
private val OpeningSpinner = 16.dp
private val OpeningGap = 12.dp

/** Between the buttons of the zone of a restore: 8 (spec 5.29 R8), closer than the rows of other zones. */
private val ZoneButtonGap = 8.dp

private fun faceOf(state: RestoreState): RestoreFace = when (state.job) {
    is BackupJob.Restoring -> RestoreFace.PROGRESS
    is BackupJob.Restored -> RestoreFace.DONE
    is BackupJob.RestoreFailed -> RestoreFace.FAILED
    else -> RestoreFace.PASSPORT
}

/**
 * «Восстановить из копии» (spec 3.20, 3.36.8, 5.29 R8; `start.html`, 5): the passport of the copy — one card with chips — what it replaces,
 * a dashed card of what is in the app now, the warning last before the buttons; the bringing back with its line of steps; how it ended.
 * The main actions are in the bottom zone of R1: «Сначала сохранить текущие данные» over the one filled dangerous «Восстановить», a plain
 * «Восстановить» into an empty app, «Понятно» first where there is no room. Lying and on a small phone one column of 560 in the middle; in a
 * window no higher than 360 dp the buttons are 48, the tile 56 and the percent 40. Stateless. [today] — what «this year» is for the date
 * of the copy; [goesOnInBackground] — whether the progress may say «Можно свернуть приложение».
 */
@Composable
fun RestoreScreen(
    state: RestoreState,
    onIntent: (RestoreIntent) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    goesOnInBackground: Boolean = true,
    today: LocalDate = SystemWallClock.today(),
) {
    val colors = MaterialTheme.colorScheme
    // A screen on its way out keeps the face it was closed on (the lesson of stage 119; review of stage 122): «Закрыть» made the failure
    // read and gone, and the face the live state gives — the passport — would put «Восстановить» under the finger while the screen fades.
    val screen = heldWhile(!state.closing, state)
    if (screen.opening) {
        // The start screen of the app with one line under it: the process starts anew beneath, and the next one shows the same — one scene, not two.
        Column(modifier = modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(modifier = Modifier.size(OpeningSpinner), color = colors.onSurfaceVariant, strokeWidth = SpinnerStroke)
            Text(stringResource(Res.string.restore_opening), modifier = Modifier.padding(top = OpeningGap), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
        }
        return
    }
    val face = faceOf(screen)
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        when (face) {
            RestoreFace.PASSPORT -> ScreenHeader(title = stringResource(Res.string.restore_title), onBack = { onIntent(RestoreIntent.CloseClicked) })
            // nowhere to go back to while the copy comes back: the system «назад» does not lead away either (RestoreRoute)
            RestoreFace.PROGRESS -> ScreenHeader(title = stringResource(Res.string.restore_progress_title), onBack = null)
            RestoreFace.DONE -> ScreenHeader(title = null, onBack = null)
            RestoreFace.FAILED -> ScreenHeader(title = stringResource(Res.string.restore_title), onBack = null)
        }
        Crossfade(targetState = face, animationSpec = tween(SWAP_MS), modifier = Modifier.fillMaxSize(), label = "restoreFace") { target ->
            val shown = heldWhile(faceOf(screen) == target, screen)
            val job = shown.job
            // every face, the first one too: the screen itself comes under the finger that opened it (zonePresses)
            val onZone = zonePresses(onIntent)
            when (target) {
                RestoreFace.PASSPORT -> Passport(shown, zone, today, onZone)
                RestoreFace.PROGRESS -> if (job is BackupJob.Restoring) Progress(job, onZone, goesOnInBackground)
                RestoreFace.DONE -> if (job is BackupJob.Restored) Done(job)
                RestoreFace.FAILED -> if (job is BackupJob.RestoreFailed) Failed(job, onZone)
            }
        }
    }
    val ready = screen.stage as? RestoreStage.Ready
    when (screen.dialog) {
        // not "are you sure?" but what exactly goes: only what there is (spec 3.36.8)
        RestoreDialog.REPLACE -> if (ready != null) ConfirmDialog(
            title = stringResource(Res.string.restore_confirm_title),
            text = stringResource(Res.string.restore_confirm_text, countsLine(ready.current.counts)),
            safe = stringResource(Res.string.backup_cancel),
            destructive = stringResource(Res.string.restore_confirm_button),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        RestoreDialog.UNSAFE -> if (ready != null) ConfirmDialog(
            title = stringResource(Res.string.restore_unsafe_title),
            text = stringResource(Res.string.restore_unsafe_text, countsLine(ready.current.counts)),
            safe = stringResource(Res.string.backup_cancel),
            destructive = stringResource(Res.string.restore_unsafe_confirm),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        RestoreDialog.STOP -> ConfirmDialog(
            title = stringResource(Res.string.backup_stop_title),
            text = stringResource(Res.string.restore_failed_text),
            safe = stringResource(Res.string.backup_stop_continue),
            destructive = stringResource(Res.string.backup_stop_confirm),
            onSafe = { onIntent(RestoreIntent.DialogDismissed) },
            onDestructive = { onIntent(RestoreIntent.DialogConfirmed) },
        )
        null -> Unit
    }
}

/** The copy picked: read, not a copy of ours, or ready — over the data, into an empty app, or without the room for it. */
@Composable
private fun Passport(state: RestoreState, zone: TimeZone, today: LocalDate, onIntent: (RestoreIntent) -> Unit) {
    when (val stage = state.stage) {
        // the file is read, or fetched first from the cloud: no zone and nothing to stop (spec 3.20)
        RestoreStage.Reading -> BackupFace(dock = null) { Reading() }
        is RestoreStage.Unfit -> Unfit(stage.problem, onIntent)
        is RestoreStage.Ready -> {
            // the function is there, only not now: the reason stands over the button that waits (spec 3.36.8)
            val waitFor = when {
                state.savingCopy -> Res.string.restore_busy_saving
                state.busy -> Res.string.restore_busy
                else -> null
            }
            when {
                stage.copy.missingBytes > 0 && stage.missingEvenUnsafeBytes > 0 -> NoRoomAtAll(stage, onIntent)
                stage.copy.missingBytes > 0 -> NoRoom(stage, waitFor, onIntent)
                else -> Ready(stage, waitFor, zone, today, onIntent)
            }
        }
    }
}

@Composable
private fun Reading() {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(top = ReadingTop), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReadingGap)) {
        CircularProgressIndicator(modifier = Modifier.size(ReadingSpinner), color = colors.onSurfaceVariant, strokeWidth = SpinnerStroke)
        Text(stringResource(Res.string.restore_reading), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
    }
}

/** Not a copy of ours, too new, or damaged: the tile of a failure, the old words, and «Выбрать другой файл» in the zone. */
@Composable
private fun Unfit(problem: BackupFileProblem, onIntent: (RestoreIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    val (title, text) = when (problem) {
        BackupFileProblem.NotOurs -> Res.string.restore_not_ours_title to Res.string.restore_not_ours_text
        BackupFileProblem.TooNew -> Res.string.restore_too_new_title to Res.string.restore_too_new_text
        BackupFileProblem.Damaged -> Res.string.restore_damaged_title to Res.string.restore_damaged_text
    }
    BackupFace(
        dock = {
            AppButton(
                stringResource(Res.string.restore_pick_another),
                onClick = { onIntent(RestoreIntent.PickAnotherClicked) },
                modifier = zoneRow(),
                compact = compact,
                allLines = true,
            )
        },
    ) {
        ResultTile(TileKind.Problem, compact)
        ResultTitle(stringResource(title))
        ResultText(stringResource(text))
    }
}

/**
 * The passport with room for the safe way. Over data: the passport, «→ заменит», the dashed card of what is in the app now and the warning
 * last before the zone — «Сначала сохранить текущие данные» outlined over the one filled dangerous «Восстановить». Into an empty app: the
 * passport and the old line under it, a plain «Восстановить» in the accent and no second question (spec 3.20, п. 2).
 */
@Composable
private fun Ready(stage: RestoreStage.Ready, waitFor: StringResource?, zone: TimeZone, today: LocalDate, onIntent: (RestoreIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    val overData = !stage.current.counts.isEmpty
    val reason = waitFor?.let { stringResource(it) }
    BackupFace(
        dock = {
            if (overData) {
                Column(zoneRow(), verticalArrangement = Arrangement.spacedBy(ZoneButtonGap)) {
                    SaveFirst(compact, onIntent)
                    AppButton(
                        text = stringResource(Res.string.restore_button),
                        onClick = { onIntent(RestoreIntent.RestoreClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        style = AppButtonStyle.DangerFilled,
                        icon = AppIcons.Restore,
                        enabled = waitFor == null,
                        reason = reason,
                        compact = compact,
                        allLines = true,
                    )
                }
            } else {
                AppButton(
                    text = stringResource(Res.string.restore_button),
                    onClick = { onIntent(RestoreIntent.RestoreClicked) },
                    modifier = zoneRow(),
                    icon = AppIcons.Restore,
                    enabled = waitFor == null,
                    reason = reason,
                    compact = compact,
                    allLines = true,
                )
            }
        },
    ) {
        PassportCard(stage.copy, zone, today, Modifier.padding(top = PassportTop))
        if (overData) {
            Replaces()
            NowCard(stage.current)
            Warning()
        } else {
            Text(
                text = stringResource(Res.string.restore_empty_app),
                modifier = Modifier.padding(top = EmptyAppTop),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = cardWords(),
            )
        }
    }
}

private val PassportTop = 4.dp

/** «Сначала сохранить текущие данные»: the way without a loss, outlined, with the icon of the copy; it takes the person to the copy. */
@Composable
private fun SaveFirst(compact: Boolean, onIntent: (RestoreIntent) -> Unit) {
    AppButton(
        text = stringResource(Res.string.restore_save_first),
        onClick = { onIntent(RestoreIntent.SaveFirstClicked) },
        modifier = Modifier.fillMaxWidth(),
        style = AppButtonStyle.Outline,
        icon = AppIcons.SaveCopy,
        compact = compact,
        allLines = true,
    )
}

/**
 * Not enough room for the safe way (spec 3.36.8): the tile of a failure in the place of the passport, «Не хватает 1,8 ГБ, чтобы восстановить
 * безопасно» and the old text; the zone — «Понятно», the safety net outlined, and last the dangerous way as a coral word that opens «Удалить
 * всё и восстановить?»; while a take is recorded or a copy saved the dangerous word sleeps with its reason over it.
 */
@Composable
private fun NoRoom(stage: RestoreStage.Ready, waitFor: StringResource?, onIntent: (RestoreIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            Column(zoneRow(), verticalArrangement = Arrangement.spacedBy(ZoneButtonGap)) {
                AppButton(
                    stringResource(Res.string.restore_no_room_ok),
                    onClick = { onIntent(RestoreIntent.CloseClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    compact = compact,
                    allLines = true,
                )
                SaveFirst(compact, onIntent)
                AppButton(
                    text = stringResource(Res.string.restore_unsafe_button),
                    onClick = { onIntent(RestoreIntent.UnsafeClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    style = AppButtonStyle.Danger,
                    enabled = waitFor == null,
                    reason = waitFor?.let { stringResource(it) },
                    allLines = true,
                )
            }
        },
    ) {
        ResultTile(TileKind.Problem, compact)
        ResultTitle(stringResource(Res.string.restore_no_room_title, Formats.fileSize(stage.copy.missingBytes)))
        ResultText(stringResource(Res.string.restore_no_room_text))
    }
}

/**
 * Not enough room even without the media in the app, or an empty app without room (spec 3.20, п. 5): the old title and text, only
 * «Понятно» — deleting data for a restore bound to fail is not offered, and a safety net is no use.
 */
@Composable
private fun NoRoomAtAll(stage: RestoreStage.Ready, onIntent: (RestoreIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            AppButton(
                stringResource(Res.string.restore_no_room_ok),
                onClick = { onIntent(RestoreIntent.CloseClicked) },
                modifier = zoneRow(),
                compact = compact,
                allLines = true,
            )
        },
    ) {
        ResultTile(TileKind.Problem, compact)
        ResultTitle(stringResource(Res.string.restore_no_room_any_title, Formats.fileSize(stage.copy.missingBytes)))
        ResultText(
            if (stage.mediaBytes > 0) {
                stringResource(Res.string.restore_no_room_even_unsafe_text, Formats.fileSize(stage.missingEvenUnsafeBytes))
            } else {
                stringResource(Res.string.restore_no_room_free_text)
            },
        )
    }
}

/**
 * The passport of a copy (spec 3.36.8, 5.29 R8): one card — «Копия от 12 сентября» (of another year — with it) and its weight on the right;
 * the device and the version of the app that made it; what is in it as chips and what is not dashed. No file name and no hour: a copy is
 * known by what it holds. For a reader one phrase: «Копия от 12 сентября, 3,4 ГБ, Pixel 10a, версия 1.4: 41 день занятий, уровень 9, 64
 * записи, 12 произведений, без видео».
 */
@Composable
private fun PassportCard(copy: BackupCandidate.Copy, zone: TimeZone, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val manifest = copy.manifest
    val made = Instant.fromEpochMilliseconds(manifest.createdAtEpochMs).toLocalDateTime(zone).date
    val title = stringResource(Res.string.restore_copy_from, Formats.recordDate(made, withYear = made.year != today.year))
    val weight = Formats.fileSize(copy.fileBytes ?: manifest.totalBytes)
    val origin = listOfNotNull(
        manifest.device.takeIf { it.isNotBlank() },
        manifest.appVersion.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.restore_copy_version, it) },
    )
    val chips = BackupFacts.passportChips(manifest)
    val inside = chips.facts.map { chipWords(it) } + chips.missing.map { missingWords(it) }
    val said = (listOf(title, weight) + origin).joinToString(SAID_SEPARATOR) + (if (inside.isEmpty()) "" else SAID_LIST + inside.joinToString(SAID_SEPARATOR))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .padding(PassportPadding)
            .clearAndSetSemantics { contentDescription = said },
    ) {
        CardHead(title, weight, titleColor = colors.onSurface)
        if (origin.isNotEmpty()) {
            Text(
                text = origin.joinToString(stringResource(Res.string.dot_separator)),
                modifier = Modifier.padding(top = OriginTop),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        FactChips(chips)
    }
}

/** How a passport is read: its parts one after another, then what it holds. */
private const val SAID_SEPARATOR = ", "
private const val SAID_LIST = ": "

/** The head of a card of the restore: its name, 17 sp / 800, and its weight on the right, 13 sp tabular, on one baseline. */
@Composable
private fun CardHead(title: String, weight: String, titleColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CardHeadGap)) {
        // «Копия от 12 сентября»: the day stays with its month where the line holds them (de «Kopie vom 12.» / «September» at 360 × 1.3)
        KeptNumbersText(
            text = title,
            modifier = Modifier.weight(1f).alignByBaseline(),
            color = titleColor,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
        )
        Text(
            text = weight,
            modifier = Modifier.alignByBaseline(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

private val CardHeadGap = 8.dp

/** «→ заменит» between the copy and what is in the app now: quiet, in the middle, 30 high (spec 5.29 R8). */
@Composable
private fun Replaces() {
    val tertiary = ViolinTheme.textTertiary
    Row(Modifier.fillMaxWidth().heightIn(min = ReplacesHeight), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(AppIcons.ArrowRight, contentDescription = null, size = ReplacesArrow, tint = tertiary)
        Spacer(Modifier.width(ReplacesGap))
        Text(
            text = stringResource(Res.string.restore_replaces),
            color = tertiary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** What is in the app now — the same form as the passport, dashed and without a ground: «Сейчас в приложении», its weight, its chips. */
@Composable
private fun NowCard(current: BackupContents) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().dashedFrame(colors.outlineVariant).padding(PassportPadding)) {
        CardHead(stringResource(Res.string.restore_now_title), Formats.fileSize(current.totalBytes), titleColor = colors.onSurfaceVariant)
        FactChips(BackupFacts.Chips(BackupFacts.nowChips(current.counts), missing = emptyList()))
    }
}

/** The warning, last before the buttons (spec 3.36.8): the sign of a failure in the coral on the coral at 0.12 over surfaceContainer. */
@Composable
private fun Warning() {
    val colors = MaterialTheme.colorScheme
    val danger = ViolinTheme.dangerSoft
    Row(
        modifier = Modifier
            .padding(top = BackupDimens.CardTop)
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .background(danger.copy(alpha = WARNING_ALPHA), AppShapes.M)
            .padding(BackupDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(BackupDimens.CardGap),
    ) {
        AppIcon(AppIcons.Alert, contentDescription = null, size = BackupDimens.CardIcon, tint = danger)
        Text(stringResource(Res.string.restore_warning), modifier = Modifier.weight(1f), color = colors.onSurface, style = cardWords())
    }
}

/**
 * The bringing back (spec 3.36.8): the line of its steps — «Проверяем» only without a safety net — the percent, the bar, the gigabytes and
 * the time left; whether the app may be left; and last, right over the zone, where the data are — still in place and it can be stopped, or
 * it can no longer. «Остановить» and that card follow one rule, [stoppable], not the number of a phase.
 */
@Composable
private fun Progress(job: BackupJob.Restoring, onIntent: (RestoreIntent) -> Unit, goesOnInBackground: Boolean) {
    val compact = currentDockMetrics().compact
    val stoppable = job.stoppable
    BackupFace(
        dock = {
            // no reason over it: the card right above says why it sleeps
            AppButton(
                text = stringResource(Res.string.backup_stop_confirm),
                onClick = { onIntent(RestoreIntent.CancelClicked) },
                modifier = zoneRow(),
                style = AppButtonStyle.Outline,
                enabled = stoppable,
                compact = compact,
                allLines = true,
            )
        },
    ) {
        PhaseRow(BackupFacts.restoreSteps(job), Modifier.padding(top = BackupDimens.PhasesTop, bottom = BackupDimens.PhasesToPercent))
        JobProgress(job.progress, job.remainingSec, compact)
        InfoCard(stringResource(if (goesOnInBackground) Res.string.restore_can_leave_title else Res.string.restore_stay_title), Modifier.padding(top = BackupDimens.FirstCardTop))
        InfoCard(stringResource(if (stoppable) Res.string.restore_can_stop else Res.string.restore_cannot_stop), Modifier.padding(top = BackupDimens.CardTop))
    }
}

/** «Данные восстановлены»: the tile with the tick, the title and what came back as chips; no buttons — the app starts anew in a moment. */
@Composable
private fun Done(job: BackupJob.Restored) {
    val compact = currentDockMetrics().compact
    BackupFace(dock = null) {
        ResultTile(TileKind.Done, compact)
        ResultTitle(stringResource(Res.string.restore_done_title))
        FactChips(BackupFacts.passportChips(job.manifest))
    }
}

/**
 * The restore failed (spec 3.36.8): the tile of a failure. The data in place — «Ещё раз» and «Закрыть»; the worst case, the media gone
 * on the way without a safety net — «Ещё раз с этим файлом» and «Начать с чистого приложения» as a coral word: it wipes what is left.
 */
@Composable
private fun Failed(job: BackupJob.RestoreFailed, onIntent: (RestoreIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            if (job.dataIntact) {
                AppButton(stringResource(Res.string.backup_retry), onClick = { onIntent(RestoreIntent.RetryClicked) }, modifier = zoneRow(), compact = compact, allLines = true)
                AppButton(
                    stringResource(Res.string.backup_close),
                    onClick = { onIntent(RestoreIntent.CloseClicked) },
                    modifier = zoneRow(),
                    style = AppButtonStyle.Text,
                    allLines = true,
                )
            } else {
                AppButton(
                    stringResource(Res.string.restore_retry_same),
                    onClick = { onIntent(RestoreIntent.RetryClicked) },
                    modifier = zoneRow(),
                    compact = compact,
                    allLines = true,
                )
                AppButton(
                    stringResource(Res.string.restore_start_clean),
                    onClick = { onIntent(RestoreIntent.StartCleanClicked) },
                    modifier = zoneRow(),
                    style = AppButtonStyle.Danger,
                    allLines = true,
                )
            }
        },
    ) {
        ResultTile(TileKind.Problem, compact)
        if (job.dataIntact) {
            ResultTitle(stringResource(Res.string.restore_failed_title))
            ResultText(stringResource(Res.string.restore_failed_text))
        } else {
            // The worst frame, and an honest one: the data are gone — but the copy was checked whole before they went.
            ResultTitle(stringResource(Res.string.restore_failed_lost_title))
            ResultText(stringResource(Res.string.restore_failed_lost_text))
        }
    }
}
