package com.violinjourney.app.feature.backup

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.core.backup.stoppable
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSwitchMark
import com.violinjourney.app.core.ui.components.ListGroup
import com.violinjourney.app.core.ui.components.LocalListGroupGround
import com.violinjourney.app.core.ui.components.OneLineText
import com.violinjourney.app.core.ui.components.ScreenHeader
import com.violinjourney.app.core.ui.components.WholeWords
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.arrivalPresses
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_busy_recording
import com.violinjourney.app.shared.resources.backup_can_leave_text
import com.violinjourney.app.shared.resources.backup_can_leave_title
import com.violinjourney.app.shared.resources.backup_cancel_action
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.backup_count_trophies_few
import com.violinjourney.app.shared.resources.backup_count_trophies_many
import com.violinjourney.app.shared.resources.backup_count_trophies_one
import com.violinjourney.app.shared.resources.backup_count_video_few
import com.violinjourney.app.shared.resources.backup_count_video_many
import com.violinjourney.app.shared.resources.backup_count_video_one
import com.violinjourney.app.shared.resources.backup_done
import com.violinjourney.app.shared.resources.backup_failed_gone_text
import com.violinjourney.app.shared.resources.backup_failed_gone_title
import com.violinjourney.app.shared.resources.backup_failed_phone_full_text
import com.violinjourney.app.shared.resources.backup_failed_phone_space_title
import com.violinjourney.app.shared.resources.backup_failed_space_inside
import com.violinjourney.app.shared.resources.backup_failed_space_text
import com.violinjourney.app.shared.resources.backup_failed_space_title
import com.violinjourney.app.shared.resources.backup_failed_text
import com.violinjourney.app.shared.resources.backup_failed_title
import com.violinjourney.app.shared.resources.backup_failed_without_video
import com.violinjourney.app.shared.resources.backup_nothing_text
import com.violinjourney.app.shared.resources.backup_nothing_title
import com.violinjourney.app.shared.resources.backup_part_always
import com.violinjourney.app.shared.resources.backup_part_audio
import com.violinjourney.app.shared.resources.backup_part_data
import com.violinjourney.app.shared.resources.backup_part_sheets
import com.violinjourney.app.shared.resources.backup_part_video
import com.violinjourney.app.shared.resources.backup_phase_verifying_file
import com.violinjourney.app.shared.resources.backup_retry
import com.violinjourney.app.shared.resources.backup_save_to
import com.violinjourney.app.shared.resources.backup_saved_advice
import com.violinjourney.app.shared.resources.backup_saved_place
import com.violinjourney.app.shared.resources.backup_saved_title
import com.violinjourney.app.shared.resources.backup_saving_button
import com.violinjourney.app.shared.resources.backup_share
import com.violinjourney.app.shared.resources.backup_stay_title
import com.violinjourney.app.shared.resources.backup_stop_confirm
import com.violinjourney.app.shared.resources.backup_stop_continue
import com.violinjourney.app.shared.resources.backup_stop_text
import com.violinjourney.app.shared.resources.backup_stop_title
import com.violinjourney.app.shared.resources.backup_title
import com.violinjourney.app.shared.resources.backup_too_big_to_share
import com.violinjourney.app.shared.resources.backup_total
import com.violinjourney.app.shared.resources.backup_without_audio
import com.violinjourney.app.shared.resources.backup_without_sheets
import com.violinjourney.app.shared.resources.backup_without_video
import com.violinjourney.app.shared.resources.dot_separator
import org.jetbrains.compose.resources.stringResource

/** The faces of «Копия данных»; one crossfades into the next (spec 5.29 R8: 250 ms). */
private enum class CopyFace { CHOOSE, NOTHING, PROGRESS, SAVED, FAILED }

private const val SWAP_MS = 250

/** A share of the weight that is there is seen, however small beside gigabytes of video. */
private const val MIN_SHARE_WEIGHT = 0.012f

/** The total «≈ 252 МБ» stands on one line: 34 sp, a step smaller where it does not, down to this. */
private const val TOTAL_SP = 34f
private const val TOTAL_LEAST_SP = 24f

// The composition of a copy (spec 5.29 R8, «Копия: состав»).
private val TotalTop = 4.dp
private val SpinnerTop = 8.dp
private val Spinner = 20.dp
private val SpinnerStroke = 2.dp
private val WeightBarHeight = 12.dp
private val WeightBarCorner = 6.dp
private val WeightBarGap = 2.dp
private val WeightBarTop = 10.dp
private val GroupTop = 16.dp
private val RowHeight = 56.dp
private val RowSide = 16.dp
private val RowVertical = 8.dp
private val RowGap = 14.dp
private val Dot = 10.dp
private val DotRing = 1.5.dp
private val FileCardTop = 16.dp
private val FilePlate = 44.dp
private val FilePlateIcon = 24.dp

/** The words of a part — 16 sp / 700 — and of its caption, 13 sp: a word that does not stand whole takes them a step smaller (stage 121). */
private val PartWordsFit = WholeWordsFit(16f, 13f)
private val PartCaptionFit = WholeWordsFit(13f, 12f)

/** What a test of the counting finds, though a reader never meets it: the empty room of a button the zone holds (D15). */
internal const val COUNTING_ZONE_TAG = "backup counting zone"

private fun faceOf(state: BackupState): CopyFace = when (val job = state.job) {
    // a short copy shows no progress screen — its button says «Сохраняем…» instead (nothing blinks)
    is BackupJob.Saving -> if (job.visible) CopyFace.PROGRESS else CopyFace.CHOOSE
    is BackupJob.Saved -> CopyFace.SAVED
    is BackupJob.SaveFailed -> CopyFace.FAILED
    else -> if (state.nothingToSave) CopyFace.NOTHING else CopyFace.CHOOSE
}

/**
 * «Копия данных» (spec 3.20, 3.36.8, 5.29 R8; `start.html`, 4): what goes in — the weight first and large, its shares, the parts with
 * their counts and weights — then the making of it and how it ended, each a face of one screen under one header. Its main action is in
 * the bottom zone of R1: «Сохранить в…», «Отправить…» or why not, «Отменить», «Ещё раз», «Готово». Lying and on a small phone one column
 * of 560 in the middle; in a window no higher than 360 dp the buttons are 48, the tile 56 and the percent 40. Stateless.
 * [goesOnInBackground] — whether the progress may say «Можно свернуть приложение» (`BackupSystem`).
 */
@Composable
fun BackupScreen(state: BackupState, fileName: String, onIntent: (BackupIntent) -> Unit, modifier: Modifier = Modifier, goesOnInBackground: Boolean = true) {
    // A screen on its way out keeps the face it was closed on (the lesson of stage 119; review of stage 122): «Готово» made the outcome
    // read and gone, and the face the live state gives — the choice — would stand for the time the screen fades, «Отправить…» under the
    // finger that pressed «Готово».
    val screen = heldWhile(!state.closing, state)
    val face = faceOf(screen)
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (face == CopyFace.SAVED) {
            // ✕ is «Готово», as the system «назад» is (spec 3.36.8)
            ScreenHeader(title = null, onBack = { onIntent(BackupIntent.DoneClicked) }, close = true)
        } else {
            // leaving does not stop a copy on its way (spec 3.20)
            ScreenHeader(title = stringResource(Res.string.backup_title), onBack = { onIntent(BackupIntent.BackClicked) })
        }
        Crossfade(targetState = face, animationSpec = tween(SWAP_MS), modifier = Modifier.fillMaxSize(), label = "backupFace") { target ->
            val shown = heldWhile(faceOf(screen) == target, screen)
            val job = shown.job
            // every face, the first one too: the screen itself comes under the finger that opened it (arrivalPresses)
            val onZone = arrivalPresses(onIntent)
            when (target) {
                CopyFace.CHOOSE -> Choose(shown, fileName, onIntent, onZone)
                CopyFace.NOTHING -> NothingToSave()
                CopyFace.PROGRESS -> if (job is BackupJob.Saving) Progress(job, onZone, goesOnInBackground)
                CopyFace.SAVED -> if (job is BackupJob.Saved) Saved(job, onZone)
                CopyFace.FAILED -> if (job is BackupJob.SaveFailed) Failed(job, shown.contents, onZone)
            }
        }
    }
    if (screen.stopDialog) {
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

/**
 * What goes into the copy (spec 3.36.8, «Состав»): «Копия займёт» and the total — «≈ 252 МБ», «меньше 1 МБ» without «≈» — the bar of the
 * shares, the parts, and what is left out a card of its consequences. The zone: «Сохранить в…» — «Сохраняем…» while a short copy goes, not
 * pressed — and «Отправить…» up to 200 МБ, or why not; while a take is recorded the reason stands over the dimmed «Сохранить в…» and the
 * rest is gone. Until the parts are counted a spinner stands for the total, the parts are not there and the zone holds the height of a
 * button empty. [onZone] — the presses of the zone ([arrivalPresses]); the parts take theirs straight.
 */
@Composable
private fun Choose(state: BackupState, fileName: String, onIntent: (BackupIntent) -> Unit, onZone: (BackupIntent) -> Unit) {
    val contents = state.contents
    val saving = state.job is BackupJob.Saving
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            if (contents == null) {
                Spacer(Modifier.height(buttonHeight).testTag(COUNTING_ZONE_TAG))
            } else {
                AppButton(
                    text = stringResource(if (saving) Res.string.backup_saving_button else Res.string.backup_save_to),
                    onClick = { onZone(BackupIntent.SaveClicked) },
                    modifier = zoneRow(),
                    icon = AppIcons.SaveCopy,
                    enabled = !state.busy && !saving,
                    reason = if (state.busy) stringResource(Res.string.backup_busy_recording) else null,
                    compact = compact,
                    allLines = true,
                )
                if (!state.busy) {
                    if (state.canShare) {
                        // dimmed, not taken away, while a short copy goes: the zone does not jump (D14)
                        AppButton(
                            text = stringResource(Res.string.backup_share),
                            onClick = { onZone(BackupIntent.ShareClicked(fileName)) },
                            modifier = zoneRow(),
                            style = AppButtonStyle.Text,
                            icon = AppIcons.Share,
                            enabled = !saving,
                            allLines = true,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.backup_too_big_to_share),
                            modifier = zoneRow(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        )
                    }
                }
            }
        },
    ) {
        Total(contents, state.totalBytes)
        if (contents == null) return@BackupFace
        WeightBar(contents, state.parts, Modifier.padding(top = WeightBarTop))
        val dot = stringResource(Res.string.dot_separator)
        val counts = contents.counts
        fun weightOf(part: BackupPart) = Formats.fileSize(contents.bytes[part] ?: 0L)
        // switched only before a copy starts; a short one on its way keeps them shut
        val toggles = state.job == BackupJob.Idle
        ListGroup(Modifier.padding(top = GroupTop)) {
            PartRow(
                part = BackupPart.DATA,
                title = stringResource(Res.string.backup_part_data),
                caption = listOf(
                    sessionsWord(counts.sessions),
                    daysWord(counts.practiceDays),
                    plural(counts.trophies, Res.string.backup_count_trophies_one, Res.string.backup_count_trophies_few, Res.string.backup_count_trophies_many),
                    weightOf(BackupPart.DATA),
                ).joinToString(dot),
                included = true,
                togglesEnabled = false,
                onToggle = {},
            )
            PartRow(
                part = BackupPart.SHEETS,
                title = stringResource(Res.string.backup_part_sheets),
                caption = listOf(piecesWord(counts.pieces), pagesWord(counts.pages), weightOf(BackupPart.SHEETS)).joinToString(dot),
                included = BackupPart.SHEETS in state.parts,
                togglesEnabled = toggles,
                onToggle = { onIntent(BackupIntent.PartToggled(BackupPart.SHEETS)) },
            )
            PartRow(
                part = BackupPart.AUDIO,
                title = stringResource(Res.string.backup_part_audio),
                caption = listOf(sessionsWord(counts.withSound), weightOf(BackupPart.AUDIO)).joinToString(dot),
                included = BackupPart.AUDIO in state.parts,
                togglesEnabled = toggles,
                onToggle = { onIntent(BackupIntent.PartToggled(BackupPart.AUDIO)) },
            )
            PartRow(
                part = BackupPart.VIDEO,
                title = stringResource(Res.string.backup_part_video),
                caption = listOf(
                    // «6 видео», not «6 дублей»: the videos of events are no takes (spec 3.36.9, «Меняет» 3.36.8)
                    plural(counts.videos, Res.string.backup_count_video_one, Res.string.backup_count_video_few, Res.string.backup_count_video_many),
                    weightOf(BackupPart.VIDEO),
                ).joinToString(dot),
                included = BackupPart.VIDEO in state.parts,
                togglesEnabled = toggles,
                onToggle = { onIntent(BackupIntent.PartToggled(BackupPart.VIDEO)) },
            )
        }
        // what is left behind has a consequence, said where the choice is made — a phrase for each part that had something; no movement
        val left = BackupPart.entries.filter { it != BackupPart.DATA && it !in state.parts && (contents.bytes[it] ?: 0L) > 0L }
        if (left.isNotEmpty()) {
            InfoCard(
                text = left.map { part ->
                    stringResource(
                        when (part) {
                            BackupPart.VIDEO -> Res.string.backup_without_video
                            BackupPart.AUDIO -> Res.string.backup_without_audio
                            else -> Res.string.backup_without_sheets
                        },
                    )
                }.joinToString("\n"),
                modifier = Modifier.padding(top = BackupDimens.CardTop),
            )
        }
    }
}

/**
 * «Копия займёт» and under it the total — 34 sp / 800 on one line — or, until the parts are counted, a spinner. For a reader one phrase,
 * «Копия займёт ≈ 252 МБ».
 */
@Composable
private fun Total(contents: BackupContents?, totalBytes: Long) {
    val colors = MaterialTheme.colorScheme
    val caption = stringResource(Res.string.backup_total)
    val total = contents?.let { weightWords(totalBytes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TotalTop)
            .then(if (total != null) Modifier.clearAndSetSemantics { contentDescription = "$caption $total" } else Modifier.semantics(mergeDescendants = true) {}),
    ) {
        Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        if (total == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = SpinnerTop).size(Spinner), color = colors.onSurfaceVariant, strokeWidth = SpinnerStroke)
        } else {
            OneLineText(
                text = total,
                style = MaterialTheme.typography.displaySmall.copy(
                    color = colors.onSurface,
                    fontSize = TOTAL_SP.sp,
                    lineHeight = (TOTAL_SP * TOTAL_LINE).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                minSp = TOTAL_LEAST_SP,
            )
        }
    }
}

/**
 * The line of the total: the mockup's, which the total inherits from its page (`font: 15px/1.5`) — 51 for 34 sp on both platforms. Under
 * the font's own 1.37 em Android would pad the line back to it and iOS would not, and the two stood 5.6 dp apart (review of stage 122).
 */
private const val TOTAL_LINE = 1.5f

/**
 * The weight seen as weight (spec 3.36.8): a share of each part in its colour, 2 apart, the ends rounded and no track of its own — the gaps
 * and the ends are the ground. With three gigabytes of video out of three and a half the bar is nearly one colour, which is the truth; a
 * share left behind does not vanish, it greys. Silent: the rows say the weights.
 */
@Composable
private fun WeightBar(contents: BackupContents, parts: Set<BackupPart>, modifier: Modifier = Modifier) {
    val total = contents.totalBytes.coerceAtLeast(1).toFloat()
    val off = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = modifier.fillMaxWidth().height(WeightBarHeight).clip(RoundedCornerShape(WeightBarCorner)).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(WeightBarGap),
    ) {
        BackupPart.entries.forEach { part ->
            val bytes = contents.bytes[part] ?: 0L
            if (bytes > 0L) {
                // a megabyte beside gigabytes would not be seen at all
                Box(Modifier.weight((bytes / total).coerceAtLeast(MIN_SHARE_WEIGHT)).fillMaxHeight().background(if (part in parts) partColor(part) else off))
            }
        }
    }
}

/**
 * A part of the copy (spec 3.36.8, 5.29 R8; D16): a dot of the colour of its share — grey and ringed while it is left out — its name, 16
 * sp / 700, and the counts and weight under it, 13 sp tabular; at the end «всегда» for the data, the switch of the app for the rest — the
 * whole row toggles it, a switch with its name and caption for a reader. A part left out does not dim: the switch and the grey dot say
 * it. The words wrap at their spaces and never break a word: «всегда» stands on one line, the name and the caption take what is left;
 * a number keeps to its word where that keeps the words whole ([KeptNumbersText]).
 */
@Composable
private fun PartRow(part: BackupPart, title: String, caption: String, included: Boolean, togglesEnabled: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fixed = part == BackupPart.DATA
    val ground = LocalListGroupGround.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ground)
            .then(
                if (fixed) {
                    Modifier.semantics(mergeDescendants = true) {}
                } else {
                    Modifier.toggleable(value = included, enabled = togglesEnabled, role = Role.Switch, onValueChange = { onToggle() })
                },
            )
            .heightIn(min = RowHeight)
            .padding(horizontal = RowSide, vertical = RowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Box(
            Modifier
                .size(Dot)
                .clip(CircleShape)
                .background(if (included) partColor(part) else colors.surfaceContainerHigh)
                .then(if (included) Modifier else Modifier.border(DotRing, colors.outlineVariant, CircleShape)),
        )
        if (fixed) {
            AlwaysPart(title, caption, Modifier.weight(1f))
        } else {
            PartWords(title, caption, Modifier.weight(1f))
            AppSwitchMark(checked = included)
        }
    }
}

/** The name of a part and its caption, one under the other, each a step smaller where a word of it does not stand whole. */
@Composable
private fun PartWords(title: String, caption: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(text = title, color = colors.onSurface, autoSize = PartWordsFit, style = partTitleStyle())
        KeptNumbersText(text = caption, style = partCaptionStyle(), color = colors.onSurfaceVariant, autoSize = PartCaptionFit)
    }
}

/**
 * The words of the data and «всегда» (spec 3.36.8, 5.29 R8): «всегда» on the right, as long as the name and the caption stand whole
 * beside it — each down to its least size ([PartWordsFit], [PartCaptionFit]); where a word of either does not (fr «Analyses, séances,
 * progression, réglages» — «64 enregistrements · …» beside «toujours» at 320 × 544 at 1.5: «enregistrements» is 148 dp in 12 sp, the
 * room 131), «всегда» stands under the caption, 2 below it, and the words take the whole width (review of stage 122). Measured here,
 * in the width the row leaves, with the measurer of the text.
 */
@Composable
private fun AlwaysPart(title: String, caption: String, modifier: Modifier = Modifier) {
    val always = stringResource(Res.string.backup_part_always)
    val titleStyle = partTitleStyle()
    val captionStyle = partCaptionStyle()
    val alwaysStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val beside = remember(title, caption, always, titleStyle, captionStyle, alwaysStyle, room, density, direction) {
            if (!constraints.hasBoundedWidth) return@remember true
            val alwaysWidth = measurer.measure(always, alwaysStyle, softWrap = false, maxLines = 1, density = density, layoutDirection = direction).size.width
            val words = room - alwaysWidth - with(density) { RowGap.roundToPx() }
            words > 0 &&
                WholeWords.at(measurer, title, titleStyle.copy(fontSize = PartWordsFit.minSp.sp), words, density, direction) &&
                WholeWords.at(measurer, caption, captionStyle.copy(fontSize = PartCaptionFit.minSp.sp), words, density, direction)
        }
        val alwaysText: @Composable (Modifier) -> Unit = { place ->
            Text(text = always, modifier = place, color = ViolinTheme.textTertiary, maxLines = 1, softWrap = false, style = alwaysStyle)
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RowGap)) {
                PartWords(title, caption, Modifier.weight(1f))
                alwaysText(Modifier)
            }
        } else {
            Column {
                PartWords(title, caption)
                alwaysText(Modifier.padding(top = AlwaysUnderTop))
            }
        }
    }
}

/** «всегда» under the caption where it does not stand beside the words (5.29 R8, review of stage 122): 2 below, as a note under a name. */
private val AlwaysUnderTop = 2.dp

@Composable
private fun partTitleStyle() = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)

@Composable
private fun partCaptionStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.5.sp, fontFeatureSettings = TABULAR_FIGURES)

/** «Пока нечего сохранять» (spec 3.36.8): in the middle, the tile with the archive, the title and the old text; no zone. */
@Composable
private fun NothingToSave() {
    val compact = currentDockMetrics().compact
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val room = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier.widthIn(max = BackupDimens.ColumnMax).fillMaxWidth().heightIn(min = room).padding(horizontal = BackupDimens.Side, vertical = BackupDimens.EndGap),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ResultTile(TileKind.Empty, compact)
                ResultTitle(stringResource(Res.string.backup_nothing_title), center = true)
                ResultText(stringResource(Res.string.backup_nothing_text), center = true)
            }
        }
    }
}

/**
 * The copy on its way (spec 3.36.8, «Идёт»): the line of its phases — the parts it writes, from the job itself — the percent, the bar, the
 * gigabytes and the time left, then two cards: whether the app may be left, and not to turn the phone off. The zone: «Отменить», the
 * dialog «Остановить?» behind it; on «Проверка» it sleeps under «Проверяем файл» — there is nothing left to cancel.
 */
@Composable
private fun Progress(job: BackupJob.Saving, onZone: (BackupIntent) -> Unit, goesOnInBackground: Boolean) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            AppButton(
                text = stringResource(Res.string.backup_cancel_action),
                onClick = { onZone(BackupIntent.CancelClicked) },
                modifier = zoneRow(),
                style = AppButtonStyle.Outline,
                enabled = job.stoppable,
                reason = if (job.verifying) stringResource(Res.string.backup_phase_verifying_file) else null,
                compact = compact,
                allLines = true,
            )
        },
    ) {
        PhaseRow(BackupFacts.copySteps(job), Modifier.padding(top = BackupDimens.PhasesTop, bottom = BackupDimens.PhasesToPercent))
        JobProgress(job.progress, job.remainingSec, compact)
        InfoCard(stringResource(if (goesOnInBackground) Res.string.backup_can_leave_title else Res.string.backup_stay_title), Modifier.padding(top = BackupDimens.FirstCardTop))
        InfoCard(stringResource(Res.string.backup_can_leave_text), Modifier.padding(top = BackupDimens.CardTop))
    }
}

/**
 * «Копия сохранена» (spec 3.36.8): the tile with the tick, the title, the card of the file — its name on up to two lines, its weight and
 * where it went (only the weight where the place is not told) — what is in it as chips, and the one next step: take the file off the
 * phone. The zone: «Готово».
 */
@Composable
private fun Saved(job: BackupJob.Saved, onZone: (BackupIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            AppButton(stringResource(Res.string.backup_done), onClick = { onZone(BackupIntent.DoneClicked) }, modifier = zoneRow(), compact = compact, allLines = true)
        },
    ) {
        ResultTile(TileKind.Done, compact)
        ResultTitle(stringResource(Res.string.backup_saved_title))
        FileCard(job)
        FactChips(BackupFacts.savedChips(job.manifest))
        InfoCard(stringResource(Res.string.backup_saved_advice), Modifier.padding(top = BackupDimens.CardTop))
    }
}

/** The file of a saved copy: a plate with the archive, its name on up to two lines, «3,4 ГБ · Загрузки». */
@Composable
private fun FileCard(job: BackupJob.Saved) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(top = FileCardTop).fillMaxWidth().background(colors.surfaceContainer, AppShapes.M).padding(BackupDimens.CardPadding),
        horizontalArrangement = Arrangement.spacedBy(BackupDimens.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(FilePlate).background(colors.surfaceContainerHigh, AppShapes.S), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.Archive, contentDescription = null, size = FilePlateIcon, tint = colors.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = job.fileName,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            )
            val size = Formats.fileSize(job.bytes)
            Text(
                text = BackupFacts.keptNumbers(job.place?.let { stringResource(Res.string.backup_saved_place, size, it) } ?: size),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/**
 * «Не получилось» — a screen, not a toast (spec 3.20, 3.36.8): the tile of a failure, the title and text of its reason — a copy for
 * «Отправить…» that did not fit the phone itself is said to be just that, in its title as in its text — and, where the place the copy went
 * was full and the copy had video that weighs something, what it would weigh without it. The zone: «Ещё раз» (the same copy, chosen in
 * the system's «Сохранить как…») and «Закрыть».
 */
@Composable
private fun Failed(job: BackupJob.SaveFailed, contents: BackupContents?, onZone: (BackupIntent) -> Unit) {
    val compact = currentDockMetrics().compact
    BackupFace(
        dock = {
            AppButton(stringResource(Res.string.backup_retry), onClick = { onZone(BackupIntent.RetryClicked) }, modifier = zoneRow(), compact = compact, allLines = true)
            AppButton(
                stringResource(Res.string.backup_close),
                onClick = { onZone(BackupIntent.DoneClicked) },
                modifier = zoneRow(),
                style = AppButtonStyle.Text,
                allLines = true,
            )
        },
    ) {
        ResultTile(TileKind.Problem, compact)
        val (title, text) = when (job.reason) {
            // the archive for «Отправить…» did not fit the phone: its title says the phone, as its text does
            SaveFailure.NO_SPACE -> if (job.missingBytes > 0) {
                stringResource(Res.string.backup_failed_phone_space_title) to stringResource(Res.string.backup_failed_space_inside, Formats.fileSize(job.missingBytes))
            } else {
                stringResource(Res.string.backup_failed_space_title) to stringResource(Res.string.backup_failed_space_text)
            }
            // the phone's own memory ran out under the snapshot of the database or the archive: another card would not help
            SaveFailure.PHONE_FULL -> stringResource(Res.string.backup_failed_title) to stringResource(Res.string.backup_failed_phone_full_text)
            SaveFailure.UNAVAILABLE -> stringResource(Res.string.backup_failed_gone_title) to stringResource(Res.string.backup_failed_gone_text)
            SaveFailure.FAILED -> stringResource(Res.string.backup_failed_title) to stringResource(Res.string.backup_failed_text)
        }
        ResultTitle(title)
        ResultText(text)
        BackupFacts.withoutVideoBytes(job, contents)?.let { bytes ->
            InfoCard(stringResource(Res.string.backup_failed_without_video, weightWords(bytes)), Modifier.padding(top = BackupDimens.CardTop))
        }
    }
}
