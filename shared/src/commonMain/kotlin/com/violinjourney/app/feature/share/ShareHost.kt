package com.violinjourney.app.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.captionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.share_add_text
import com.violinjourney.app.shared.resources.share_backing
import com.violinjourney.app.shared.resources.share_backing_caption
import com.violinjourney.app.shared.resources.share_busy
import com.violinjourney.app.shared.resources.share_continue
import com.violinjourney.app.shared.resources.share_failed_text
import com.violinjourney.app.shared.resources.share_failed_title
import com.violinjourney.app.shared.resources.share_failed_video_text
import com.violinjourney.app.shared.resources.share_failed_video_title
import com.violinjourney.app.shared.resources.share_file_details
import com.violinjourney.app.shared.resources.share_file_details_stereo
import com.violinjourney.app.shared.resources.share_format_description
import com.violinjourney.app.shared.resources.share_large_file
import com.violinjourney.app.shared.resources.share_notes
import com.violinjourney.app.shared.resources.share_notes_caption_backing
import com.violinjourney.app.shared.resources.share_notes_caption_original
import com.violinjourney.app.shared.resources.share_notes_caption_processed
import com.violinjourney.app.shared.resources.share_notes_too_long_few
import com.violinjourney.app.shared.resources.share_notes_too_long_many
import com.violinjourney.app.shared.resources.share_notes_too_long_one
import com.violinjourney.app.shared.resources.share_original
import com.violinjourney.app.shared.resources.share_original_caption
import com.violinjourney.app.shared.resources.share_percent
import com.violinjourney.app.shared.resources.share_preparing
import com.violinjourney.app.shared.resources.share_preparing_video
import com.violinjourney.app.shared.resources.share_processed
import com.violinjourney.app.shared.resources.share_processed_caption
import com.violinjourney.app.shared.resources.share_remaining
import com.violinjourney.app.shared.resources.share_retry
import com.violinjourney.app.shared.resources.share_send_as_shot
import com.violinjourney.app.shared.resources.share_send_original
import com.violinjourney.app.shared.resources.share_size_kb
import com.violinjourney.app.shared.resources.share_size_mb
import com.violinjourney.app.shared.resources.share_sound_as_recorded
import com.violinjourney.app.shared.resources.share_sound_only
import com.violinjourney.app.shared.resources.share_sound_with_processing
import com.violinjourney.app.shared.resources.share_title
import com.violinjourney.app.shared.resources.share_video
import com.violinjourney.app.shared.resources.share_video_caption
import com.violinjourney.app.shared.resources.share_video_details
import com.violinjourney.app.shared.resources.share_video_original
import com.violinjourney.app.shared.resources.share_video_original_caption
import com.violinjourney.app.shared.resources.share_video_processed
import org.jetbrains.compose.resources.stringResource

// The sheet «Поделиться» (spec 3.36.5, 5.29 R5; records.html 5).
private val VariantMinHeight = 64.dp
private val VariantMinHeightLandscape = 56.dp
private val VariantVertical = 10.dp
private val VariantSide = 14.dp
private val VariantGap = 12.dp
private val VariantsGap = 8.dp
private val VariantsTop = 12.dp
private val VariantBorder = 1.5.dp
private const val CHOSEN_GROUND_ALPHA = 0.10f
private val RadioSize = 22.dp
private val RadioRing = 2.dp
private val RadioChosenRing = 6.dp
private val FormatChipHeight = 24.dp
private val FormatChipSide = 8.dp
private val FormatChipCorner = RoundedCornerShape(7.dp)
private val FileLineTop = 12.dp
private val FileLineGap = 12.dp
private val CheckTop = 12.dp
private val CheckMinHeight = 48.dp
private val CheckSize = 22.dp
private val CheckCorner = RoundedCornerShape(6.dp)
private val CheckRing = 2.dp
private val CheckMark = 16.dp
private val CheckGap = 12.dp
private val ContinueTop = 14.dp
private val ProgressTop = 18.dp
private val ProgressHeight = 8.dp
private val ProgressCorner = 5.dp
private val ProgressLineTop = 8.dp
private val CancelTop = 16.dp
private val FailedTop = 12.dp
private val FailedVertical = 12.dp
private val FailedSide = 14.dp
private val FailedGap = 10.dp
private val FailedButtonsTop = 16.dp
private val FailedButtonsGap = 10.dp
private const val PERCENT = 100f

/** A variant that cannot be chosen — «Видео с нотами» of a recording too long for it (spec 3.37) — stands at the dimming of R1. */
private const val UNAVAILABLE_ALPHA = 0.38f
private const val BYTES_PER_KB = 1_024L
private const val TABULAR_FIGURES = "tnum"
private const val EXTENSION_DOT = "."

/**
 * Hosts «Поделиться» on a screen: shows the sheet of [viewModel] and hands the finished file to the system. A route puts it beside its
 * screen and calls [ShareViewModel.start].
 */
@Composable
fun ShareHost(viewModel: ShareViewModel) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val send = rememberFileSender()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ShareEffect.Send -> send(effect.file, effect.text, effect.type)
                }
            }
        }
    }
    ShareSheetFrame(sheet, viewModel::onIntent)
}

/**
 * «Поделиться» as a bottom sheet of R1 (spec 3.36.5): one frame, three faces — the choice, the file being made, the failure — each in
 * the place of the one before, with its buttons at the bottom. While a file is made — its progress, or «Готовим…» on «Продолжить» —
 * the sheet holds: no handle (its room stays: the sheet keeps its height, its top edge does not move), a swipe, a tap beside it and
 * «назад» do not close it; the way out is «Отмена» or the file, handed to the system. The choice and the failure a swipe only hides
 * ([ShareIntent.Dismissed]). Public for the tests of the frame; [ShareHost] puts it on a screen.
 */
@Composable
fun ShareSheetFrame(sheet: ShareSheet?, onIntent: (ShareIntent) -> Unit) {
    val holds = sheet is ShareSheet.Preparing || (sheet as? ShareSheet.Choose)?.busy == true
    val landscape = windowIsLandscape()
    AppSheet(
        value = sheet,
        onHide = { onIntent(ShareIntent.Dismissed) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        dismissible = !holds,
        keepHandleRoom = true,
        bottom = { shown -> { ShareSheetButtons(shown, onIntent) } },
    ) { shown -> ShareSheetContent(shown, onIntent, landscape) }
}

/** The window is wider than it is high: the variants are rows of 56, not 64 (5.29 R5). */
@Composable
private fun windowIsLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

/**
 * What the sheet «Поделиться» says above its buttons, face by face ([ShareSheet]): the variants, the file and «Добавить текст»; the
 * file being made; what did not work. Public for the previews, with [ShareSheetButtons] under it.
 */
@Composable
fun ColumnScope.ShareSheetContent(sheet: ShareSheet, onIntent: (ShareIntent) -> Unit, landscape: Boolean) {
    when (sheet) {
        is ShareSheet.Choose -> Choose(sheet, onIntent, landscape)
        is ShareSheet.Preparing -> Preparing(sheet)
        is ShareSheet.Failed -> Failed(sheet.info.video)
    }
}

/**
 * The buttons at the bottom of the sheet, face by face: «Продолжить» — «Готовим…», dimmed, while a short preparation runs (5.29 R5:
 * a disabled button, 0.38 in its own colours); «Отмена», an outline; «Ещё раз» and «Отправить оригинал» / «Отправить как снято» under it.
 * A face that has just taken the place of another does not take the second tap of the finger that pressed the one before
 * ([AppSheetButtons]).
 */
@Composable
fun ShareSheetButtons(sheet: ShareSheet, onIntent: (ShareIntent) -> Unit) {
    when (sheet) {
        is ShareSheet.Choose -> AppSheetButtons(
            main = stringResource(if (sheet.busy) Res.string.share_busy else Res.string.share_continue),
            onMain = { onIntent(ShareIntent.ContinueClicked) },
            mainEnabled = !sheet.busy,
            top = ContinueTop,
        )
        is ShareSheet.Preparing -> AppSheetButtons(
            main = stringResource(Res.string.dialog_cancel),
            onMain = { onIntent(ShareIntent.CancelClicked) },
            mainIcon = AppIcons.Close,
            mainStyle = AppButtonStyle.Outline,
            top = CancelTop,
        )
        // with a video take it is the picture that is worth sending: the way out is «как снято», not the sound alone (spec 3.19)
        is ShareSheet.Failed -> AppSheetButtons(
            main = stringResource(Res.string.share_retry),
            onMain = { onIntent(ShareIntent.RetryClicked) },
            second = stringResource(if (sheet.info.video) Res.string.share_send_as_shot else Res.string.share_send_original),
            onSecond = { onIntent(ShareIntent.SendOriginalClicked) },
            top = FailedButtonsTop,
            gap = FailedButtonsGap,
        )
    }
}

/** The title of a face, 20 sp / 800 — for TalkBack the heading of the sheet (spec 3.36.5: the titles of sheets are headings). */
@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
    )
}

/**
 * The choice (spec 3.36.5): the variants from «как слышно» to «как было», each a row with a radio, its name, its caption and the chip
 * of its format; the line of the file of the one chosen — its name and «2:05 · ≈ 2 МБ · моно», from 100 MB in the colour of danger
 * with a word of what it means; «Добавить текст» with the text itself under it.
 */
@Composable
private fun Choose(sheet: ShareSheet.Choose, onIntent: (ShareIntent) -> Unit, landscape: Boolean) {
    val info = sheet.info
    SheetTitle(stringResource(Res.string.share_title))
    Column(
        modifier = Modifier.padding(top = VariantsTop).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(VariantsGap),
    ) {
        variantsOf(info).forEach { variant ->
            val unavailable = variant == ShareVariant.NOTES && info.notes?.tooLong == true
            Variant(
                title = variantTitle(info, variant),
                caption = variantCaption(info, variant),
                format = info.extensionOf(variant),
                selected = sheet.variant == variant,
                enabled = !sheet.busy && !unavailable,
                unavailable = unavailable,
                landscape = landscape,
            ) { onIntent(ShareIntent.VariantSelected(variant)) }
        }
    }
    FileLine(info, sheet.variant)
    AddText(sheet.withText, info.message, enabled = !sheet.busy) { onIntent(ShareIntent.TextToggled(it)) }
}

/**
 * What the sheet offers, in its order (spec 3.17, 3.19, 3.32, 3.37): the video with the notes first, where it is made; the mix of a take
 * under a backing; a video — with its processed sound, as shot, its sound alone; a sound — processed, the original. Without processing
 * «Обработанный звук» would be the original twice: it is not offered then.
 */
private fun variantsOf(info: ShareInfo): List<ShareVariant> = buildList {
    if (info.notes != null) add(ShareVariant.NOTES)
    if (info.backing) add(ShareVariant.BACKING)
    if (info.processed) add(ShareVariant.PROCESSED)
    add(ShareVariant.ORIGINAL)
    if (info.video) add(ShareVariant.SOUND)
}

@Composable
private fun variantTitle(info: ShareInfo, variant: ShareVariant): String = stringResource(
    when (variant) {
        ShareVariant.BACKING -> Res.string.share_backing
        ShareVariant.PROCESSED -> if (info.video) Res.string.share_video_processed else Res.string.share_processed
        ShareVariant.ORIGINAL -> when {
            !info.video -> Res.string.share_original
            info.processed -> Res.string.share_video_original
            else -> Res.string.share_video
        }
        ShareVariant.SOUND -> Res.string.share_sound_only
        ShareVariant.NOTES -> Res.string.share_notes
    },
)

/** The caption of a variant in words, without numbers: the size, «моно» and the resolution are the line of the file's (spec 3.36.5). */
@Composable
private fun variantCaption(info: ShareInfo, variant: ShareVariant): String = when (variant) {
    ShareVariant.BACKING -> stringResource(Res.string.share_backing_caption)
    ShareVariant.PROCESSED -> stringResource(Res.string.share_processed_caption, captionName(info.caption))
    ShareVariant.ORIGINAL -> stringResource(
        when {
            !info.video -> Res.string.share_original_caption
            info.processed -> Res.string.share_video_original_caption
            else -> Res.string.share_video_caption
        },
    )
    ShareVariant.SOUND -> stringResource(if (info.processed) Res.string.share_sound_with_processing else Res.string.share_sound_as_recorded)
    ShareVariant.NOTES -> notesCaption(info.notes)
}

/** «лента нот и итог в конце · звук с обработкой» — and the sound it carries; a recording too long for it says why it cannot be chosen. */
@Composable
private fun notesCaption(notes: NotesOffer?): String = when {
    notes == null -> ""
    notes.tooLong -> stringResource(
        Formats.plural(notes.limitMinutes, Res.string.share_notes_too_long_one, Res.string.share_notes_too_long_few, Res.string.share_notes_too_long_many),
        notes.limitMinutes,
    )
    else -> stringResource(
        when (notes.sound) {
            NotesSound.BACKING -> Res.string.share_notes_caption_backing
            NotesSound.PROCESSED -> Res.string.share_notes_caption_processed
            NotesSound.ORIGINAL -> Res.string.share_notes_caption_original
        },
    )
}

/**
 * A variant (5.29 R5): 64 at the least (56 lying), on the ground of the screen at a corner of 18; the chosen one framed in the accent
 * over the accent at 10 %; a radio of 22, the name, the caption and the chip of the format. A radio button for TalkBack, its words one.
 * [unavailable] — it cannot be chosen at all (spec 3.37): dimmed, and TalkBack says so with the reason in the caption.
 */
@Composable
private fun Variant(
    title: String,
    caption: String,
    format: String,
    selected: Boolean,
    enabled: Boolean,
    unavailable: Boolean,
    landscape: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ground = if (selected) colors.primary.copy(alpha = CHOSEN_GROUND_ALPHA).compositeOver(colors.surface) else colors.surface
    Row(
        modifier = Modifier
            .alpha(if (unavailable) UNAVAILABLE_ALPHA else 1f)
            .fillMaxWidth()
            .heightIn(min = if (landscape) VariantMinHeightLandscape else VariantMinHeight)
            .clip(AppShapes.M)
            .background(ground)
            .border(VariantBorder, if (selected) colors.primary else Color.Transparent, AppShapes.M)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = VariantSide, vertical = VariantVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VariantGap),
    ) {
        Radio(selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
            Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        FormatChip(format)
    }
}

/** The radio of a variant: a ring of 2 in the third level of text; chosen — a ring of 6 in the accent. */
@Composable
private fun Radio(selected: Boolean) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else ViolinTheme.textTertiary
    Box(
        Modifier
            .size(RadioSize)
            .drawBehind {
                val width = (if (selected) RadioChosenRing else RadioRing).toPx()
                drawCircle(ring, radius = (size.minDimension - width) / 2, style = Stroke(width))
            },
    )
}

/**
 * «.m4a», «.mp4», «.mov» — the file a variant makes, before it is asked (5.29 R5: 24 at a corner of 7 on surface-2, 12 sp, 800). TalkBack
 * says «файл mp4» (spec 3.36.5, 3.37), not the dot.
 */
@Composable
private fun FormatChip(format: String) {
    val description = stringResource(Res.string.share_format_description, format.removePrefix(EXTENSION_DOT))
    Box(
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = description }
            .height(FormatChipHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, FormatChipCorner)
            .padding(horizontal = FormatChipSide),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/**
 * The line of the file of the chosen variant (spec 3.19, 3.36.5): its name, and «2:05 · ≈ 2 МБ · моно» — «стерео» of the mix,
 * «3:40 · 1080p · ≈ 62 МБ» of a video; from 100 MB the size is said in the colour of danger, bold, and under it what it means —
 * messengers squeeze such a file or refuse it.
 */
@Composable
private fun FileLine(info: ShareInfo, variant: ShareVariant) {
    val colors = MaterialTheme.colorScheme
    val asVideo = info.video && variant != ShareVariant.SOUND
    val bytes = info.bytesOf(variant)
    val large = ShareInfo.isLarge(bytes)
    Row(
        modifier = Modifier.padding(top = FileLineTop).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FileLineGap),
    ) {
        AppIcon(if (asVideo) AppIcons.Video else AppIcons.FileAudio, contentDescription = null, tint = colors.onSurfaceVariant)
        Column {
            Text(
                text = info.fileNameOf(variant),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            val details = if (asVideo) {
                // «Видео с нотами» is made no larger than 1080 on its short side (spec 5.30): its own resolution
                val resolution = info.notes?.takeIf { variant == ShareVariant.NOTES }?.resolution ?: info.resolution
                stringResource(Res.string.share_video_details, Formats.duration(info.durationMs), resolution, Formats.fileSize(bytes))
            } else {
                stringResource(
                    if (variant == ShareVariant.BACKING) Res.string.share_file_details_stereo else Res.string.share_file_details,
                    Formats.duration(info.durationMs), sizeText(bytes),
                )
            }
            Text(
                text = details,
                // a size that messengers squeeze or refuse is said in the colour of danger and, below, in words
                color = if (large) ViolinTheme.dangerSoft else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (large) FontWeight.Bold else FontWeight.Normal, fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            if (large) {
                Text(stringResource(Res.string.share_large_file), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
            }
        }
    }
}

/** «Добавить текст» — a box of 22 in the accent at a corner of 6, the words and under them the text that goes along (5.29 R5). */
@Composable
private fun AddText(checked: Boolean, message: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(top = CheckTop)
            .fillMaxWidth()
            .heightIn(min = CheckMinHeight)
            .clip(AppShapes.S)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CheckGap),
    ) {
        Box(
            modifier = Modifier
                .size(CheckSize)
                .clip(CheckCorner)
                .then(if (checked) Modifier.background(colors.primary) else Modifier.border(CheckRing, ViolinTheme.textTertiary, CheckCorner)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) AppIcon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, size = CheckMark)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.share_add_text), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            Text(message, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
    }
}

/**
 * The file being made (spec 3.36.5): «Готовим файл» — «Готовим видео» for a variant with the picture — and the percent, the bar, and
 * the line of the variant chosen with its format and, once there is an estimate, the seconds left: «С минусовкой · .mp4 · осталось
 * около 20 с». One description for TalkBack — the heading of the sheet while the file is made, as the title it begins with.
 */
@Composable
private fun Preparing(sheet: ShareSheet.Preparing) {
    val colors = MaterialTheme.colorScheme
    val info = sheet.info
    val separator = stringResource(Res.string.dot_separator)
    val title = stringResource(if (info.video && sheet.variant != ShareVariant.SOUND) Res.string.share_preparing_video else Res.string.share_preparing)
    val line = listOfNotNull(
        variantTitle(info, sheet.variant),
        info.extensionOf(sheet.variant),
        sheet.remainingSec?.let { stringResource(Res.string.share_remaining, it) },
    ).joinToString(separator)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.Bottom) {
            SheetTitle(title, Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.share_percent, sheet.percent),
                color = colors.primary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        val fraction = sheet.percent / PERCENT
        Spacer(
            Modifier
                .padding(top = ProgressTop)
                .fillMaxWidth()
                .height(ProgressHeight)
                .clearAndSetSemantics { }
                .drawBehind {
                    val corner = CornerRadius(ProgressCorner.toPx())
                    drawRoundRect(colors.surfaceContainerHigh, size = size, cornerRadius = corner)
                    drawRoundRect(colors.primary, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = corner)
                },
        )
        Text(
            text = line,
            modifier = Modifier.padding(top = ProgressLineTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * What did not work (spec 3.36.5): a plate in the sheet, not a toast — the sign of a failure in the colour of danger, «Не получилось
 * подготовить файл» / «…видео» and that the recording and its settings are in place. Why it failed the app does not know and does
 * not guess.
 */
@Composable
private fun Failed(video: Boolean) {
    val colors = MaterialTheme.colorScheme
    SheetTitle(stringResource(Res.string.share_title))
    Row(
        modifier = Modifier
            .padding(top = FailedTop)
            .fillMaxWidth()
            .clip(AppShapes.Control)
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = FailedSide, vertical = FailedVertical)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FailedGap),
    ) {
        AppIcon(AppIcons.Alert, contentDescription = null, tint = ViolinTheme.dangerSoft)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(if (video) Res.string.share_failed_video_title else Res.string.share_failed_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(if (video) Res.string.share_failed_video_text else Res.string.share_failed_text),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
    }
}

/** «340 КБ», «2,1 МБ». */
@Composable
private fun sizeText(bytes: Long): String {
    val kb = bytes / BYTES_PER_KB
    return if (kb < BYTES_PER_KB) stringResource(Res.string.share_size_kb, kb.coerceAtLeast(1)) else stringResource(Res.string.share_size_mb, Formats.oneDecimal(kb / BYTES_PER_KB.toDouble()))
}

/**
 * Hands a prepared file to the system's «Поделиться», with [text] beside it where the receiver takes one (spec 3.17); `type` — what the
 * receivers are told the file is, decided by the recording ([ShareInfo.typeOf], plan D48): Android picks the receivers by it, iOS by the
 * file itself.
 */
@Composable
expect fun rememberFileSender(): (file: PlatformFile, text: String?, type: String) -> Unit
