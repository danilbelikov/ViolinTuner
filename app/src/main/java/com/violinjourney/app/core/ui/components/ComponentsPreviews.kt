package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.BalancePill
import com.violinjourney.app.feature.journey.GlassSquare
import com.violinjourney.app.feature.journey.TaktIcon
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.sessionsInWords
import com.violinjourney.app.feature.share.ShareInfo
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_heard_violin
import com.violinjourney.app.shared.resources.backing_heard_with
import com.violinjourney.app.shared.resources.backup_cancel
import com.violinjourney.app.shared.resources.backup_count_pieces_many
import com.violinjourney.app.shared.resources.backup_count_sessions_many
import com.violinjourney.app.shared.resources.backup_stop_confirm
import com.violinjourney.app.shared.resources.backup_stop_continue
import com.violinjourney.app.shared.resources.backup_stop_text
import com.violinjourney.app.shared.resources.backup_stop_title
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.dialog_back
import com.violinjourney.app.shared.resources.dialog_discard_changes
import com.violinjourney.app.shared.resources.dialog_discard_typed
import com.violinjourney.app.shared.resources.dialog_name_needed
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.home_travel_line
import com.violinjourney.app.shared.resources.journey_depart
import com.violinjourney.app.shared.resources.journey_depart_spend
import com.violinjourney.app.shared.resources.journey_fullscreen
import com.violinjourney.app.shared.resources.journey_missing
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.piece_delete_text
import com.violinjourney.app.shared.resources.piece_delete_title
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_field_title
import com.violinjourney.app.shared.resources.piece_form_discard_confirm
import com.violinjourney.app.shared.resources.piece_form_discard_title
import com.violinjourney.app.shared.resources.piece_key_major
import com.violinjourney.app.shared.resources.piece_key_minor
import com.violinjourney.app.shared.resources.piece_status_in_repertoire
import com.violinjourney.app.shared.resources.piece_status_learning
import com.violinjourney.app.shared.resources.piece_status_reading
import com.violinjourney.app.shared.resources.profile_name_counter
import com.violinjourney.app.shared.resources.restore_unsafe_confirm
import com.violinjourney.app.shared.resources.restore_unsafe_text
import com.violinjourney.app.shared.resources.restore_unsafe_title
import com.violinjourney.app.shared.resources.section_create
import com.violinjourney.app.shared.resources.section_delete
import com.violinjourney.app.shared.resources.section_name_label
import com.violinjourney.app.shared.resources.section_new_title
import com.violinjourney.app.shared.resources.section_rename
import com.violinjourney.app.shared.resources.session_action_rename
import com.violinjourney.app.shared.resources.session_delete_text
import com.violinjourney.app.shared.resources.session_delete_title
import com.violinjourney.app.shared.resources.session_menu_piece
import com.violinjourney.app.shared.resources.session_rename_confirm
import com.violinjourney.app.shared.resources.session_rename_hint
import com.violinjourney.app.shared.resources.session_rename_label
import com.violinjourney.app.shared.resources.session_rename_title
import com.violinjourney.app.shared.resources.sound_mode_everyone
import com.violinjourney.app.shared.resources.sound_mode_own
import com.violinjourney.app.shared.resources.video_pick
import com.violinjourney.app.shared.resources.video_pick_hint
import com.violinjourney.app.shared.resources.video_resolution
import com.violinjourney.app.shared.resources.video_shoot_backing
import com.violinjourney.app.shared.resources.video_shoot_backing_hint
import com.violinjourney.app.shared.resources.video_size
import org.jetbrains.compose.resources.stringResource

// The common parts of stages 101 and 102 (spec 3.36.1, 5.29; components.html, «Диалоги», «Меню ⋯», the segmented switch, the
// field; the «⋯» of a recording's card lives with the card, RecordCardPreviews). A window and a popup do not draw in a preview: the dialogs and the menus are their cards, over the screen dimmed as a
// dialog dims it. The other parts of stage 102 — DockPreviews, ControlsPreviews, ProgressPreviews, GlassSheetPreviews.

private const val MENUET = "Менуэт соль мажор"
private const val FULL_HD = 1080
private const val SECTION_NAME_MAX = 24

/**
 * The dim under the cards, for the picture only: a real dialog is dimmed by its platform window, not by a colour of the theme, and
 * `ViolinTheme.sheetScrim` belongs to the sheets (spec 5.29).
 */
private val PreviewDim = Color.Black.copy(alpha = 0.6f)

@Composable
private fun OverScreen(alignment: Alignment = Alignment.Center, content: @Composable BoxScope.() -> Unit) {
    ViolinTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .background(PreviewDim)
                .padding(24.dp),
            contentAlignment = alignment,
            content = content,
        )
    }
}

@Composable
private fun DeleteRecordingCard() = AppDialogCard(
    title = stringResource(Res.string.session_delete_title),
    text = stringResource(Res.string.session_delete_text),
    confirm = stringResource(Res.string.piece_delete_confirm),
    onConfirm = {},
    onDismiss = {},
    confirmTone = DialogTone.Danger,
    bin = true,
)

@Preview(name = "Dialog · «Удалить запись?» · 412", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun DeletePreview() = OverScreen { DeleteRecordingCard() }

@Preview(name = "Dialog · «Удалить запись?» · 360", widthDp = 360, heightDp = 560, locale = "ru")
@Composable
private fun Delete360Preview() = OverScreen { DeleteRecordingCard() }

@Preview(name = "Dialog · «Удалить «…»?» of a piece", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun DeletePiecePreview() = OverScreen {
    AppDialogCard(
        title = stringResource(Res.string.piece_delete_title, MENUET),
        text = stringResource(Res.string.piece_delete_text),
        confirm = stringResource(Res.string.piece_delete_confirm),
        onConfirm = {},
        onDismiss = {},
        confirmTone = DialogTone.Danger,
        bin = true,
    )
}

@Composable
private fun DiscardCard(text: String) = AppDialogCard(
    title = stringResource(Res.string.piece_form_discard_title),
    text = text,
    confirm = stringResource(Res.string.piece_form_discard_confirm),
    onConfirm = {},
    onDismiss = {},
    dismiss = stringResource(Res.string.dialog_back),
    confirmTone = DialogTone.Quiet,
)

@Preview(name = "Dialog · «Не сохранять?» · an edit", widthDp = 412, heightDp = 480, locale = "ru")
@Composable
private fun DiscardEditPreview() = OverScreen { DiscardCard(stringResource(Res.string.dialog_discard_changes, MENUET)) }

@Preview(name = "Dialog · «Не сохранять?» · a new element", widthDp = 412, heightDp = 480, locale = "ru")
@Composable
private fun DiscardNewPreview() = OverScreen { DiscardCard(stringResource(Res.string.dialog_discard_typed)) }

@Preview(name = "Dialog · «Не сохранять?» · fr, 360", widthDp = 360, heightDp = 480, locale = "fr")
@Composable
private fun DiscardFrPreview() = OverScreen { DiscardCard(stringResource(Res.string.dialog_discard_changes, MENUET)) }

@Preview(name = "Dialog · «Переименовать запись» · the hint, «Сохранить» always on", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun RenamePreview() = OverScreen(Alignment.TopCenter) {
    val text = "Менуэт — чистый прогон"
    AppDialogCard(
        title = stringResource(Res.string.session_rename_title),
        confirm = stringResource(Res.string.session_rename_confirm),
        onConfirm = {},
        onDismiss = {},
        modifier = Modifier.padding(top = 96.dp),
    ) {
        AppField(
            value = TextFieldValue(text, TextRange(text.length)),
            onValueChange = {},
            label = stringResource(Res.string.session_rename_label),
            hint = stringResource(Res.string.session_rename_hint),
        )
    }
}

@Composable
private fun NewSectionCard() = AppDialogCard(
    title = stringResource(Res.string.section_new_title),
    confirm = stringResource(Res.string.section_create),
    onConfirm = {},
    onDismiss = {},
    confirmEnabled = false,
    modifier = Modifier.padding(top = 96.dp),
) {
    AppField(
        value = TextFieldValue(""),
        onValueChange = {},
        label = stringResource(Res.string.section_name_label),
        error = stringResource(Res.string.dialog_name_needed),
        counter = stringResource(Res.string.profile_name_counter, 0, SECTION_NAME_MAX),
    )
}

@Preview(name = "Dialog · «Новый раздел» · empty: dimmed, «Нужно название», 0 / 24", widthDp = 412, heightDp = 560, locale = "ru")
@Composable
private fun NewSectionEmptyPreview() = OverScreen(Alignment.TopCenter) { NewSectionCard() }

@Preview(name = "Dialog · «Новый раздел» · empty, de, 360", widthDp = 360, heightDp = 560, locale = "de")
@Composable
private fun NewSectionDePreview() = OverScreen(Alignment.TopCenter) { NewSectionCard() }

@Composable
private fun StopCard() = AppDialogCard(
    title = stringResource(Res.string.backup_stop_title),
    text = stringResource(Res.string.backup_stop_text),
    confirm = stringResource(Res.string.backup_stop_confirm),
    onConfirm = {},
    onDismiss = {},
    dismiss = stringResource(Res.string.backup_stop_continue),
    confirmTone = DialogTone.Danger,
)

@Preview(name = "Dialog · «Остановить?» · the copy", widthDp = 412, heightDp = 400, locale = "ru")
@Composable
private fun StopPreview() = OverScreen { StopCard() }

@Composable
private fun UnsafeRestoreCard() = AppDialogCard(
    title = stringResource(Res.string.restore_unsafe_title),
    text = stringResource(
        Res.string.restore_unsafe_text,
        stringResource(Res.string.backup_count_sessions_many, 48) + ", " + stringResource(Res.string.backup_count_pieces_many, 15),
    ),
    confirm = stringResource(Res.string.restore_unsafe_confirm),
    onConfirm = {},
    onDismiss = {},
    dismiss = stringResource(Res.string.backup_cancel),
    confirmTone = DialogTone.Danger,
)

@Preview(name = "Dialog · long buttons, fr, 360: one under the other", widthDp = 360, heightDp = 480, locale = "fr")
@Composable
private fun LongButtonsFrPreview() = OverScreen { UnsafeRestoreCard() }

@Preview(name = "Dialog · long buttons, de, 360", widthDp = 360, heightDp = 480, locale = "de")
@Composable
private fun LongButtonsDePreview() = OverScreen { UnsafeRestoreCard() }

@Preview(name = "Menu · «⋯» of a section: the danger last, after a line", widthDp = 412, heightDp = 220, locale = "ru")
@Composable
private fun SectionMenuPreview() = OverScreen(Alignment.TopEnd) {
    AppMenuCard(danger = MenuDanger(stringResource(Res.string.section_delete), onClick = {})) {
        AppMenuItem(stringResource(Res.string.section_rename), icon = AppIcons.Pencil, onClick = {})
    }
}

@Preview(name = "Menu · «Видео»: items with captions", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun VideoMenuPreview() = OverScreen(Alignment.TopStart) {
    AppMenuCard(modifier = Modifier.width(248.dp)) {
        AppMenuItem(stringResource(Res.string.video_shoot_backing), icon = AppIcons.Backing, caption = stringResource(Res.string.video_shoot_backing_hint), onClick = {})
        AppMenuItem(stringResource(Res.string.video_pick), icon = AppIcons.VideoGallery, caption = stringResource(Res.string.video_pick_hint), onClick = {})
    }
}

@Preview(name = "Menu · the status of a piece: the chosen with a check", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun StatusMenuPreview() = OverScreen(Alignment.TopStart) {
    AppMenuCard {
        AppMenuItem(stringResource(Res.string.piece_status_reading), onClick = {})
        AppMenuItem(stringResource(Res.string.piece_status_learning), selected = true, onClick = {})
        AppMenuItem(stringResource(Res.string.piece_status_in_repertoire), onClick = {})
    }
}

/** «⋯» of a recording (spec 3.36.5): «Удалить…» of a video with what goes with it under it — from 100 MB bold in the colour of danger. */
@Composable
private fun RecordingMenu(megabytes: Long) = OverScreen(Alignment.TopEnd) {
    val bytes = megabytes * 1024 * 1024
    val caption = stringResource(Res.string.video_size, stringResource(Res.string.video_resolution, FULL_HD), Formats.fileSize(bytes))
    AppMenuCard(
        danger = MenuDanger(stringResource(Res.string.card_menu_delete), onClick = {}, caption = caption, captionStrong = ShareInfo.isLarge(bytes)),
    ) {
        AppMenuItem(stringResource(Res.string.session_action_rename), icon = AppIcons.Pencil, onClick = {})
        AppMenuItem(stringResource(Res.string.session_menu_piece), icon = AppIcons.TabRepertoire.normal, onClick = {})
    }
}

@Preview(name = "Menu · «⋯» of a recording: «Удалить…» with the size of its video", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun RecordingMenuPreview() = RecordingMenu(megabytes = 62)

@Preview(name = "Menu · «⋯» of a recording: a large file, bold in the colour of danger", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun RecordingMenuLargePreview() = RecordingMenu(megabytes = 612)

@Preview(name = "Menu · «⋯» of a recording, de, 360: two lines", widthDp = 360, heightDp = 300, locale = "de")
@Composable
private fun RecordingMenuGermanPreview() = RecordingMenu(megabytes = 612)

/** The segments of the player (spec 3.36.5, 5.29 R5) on the colour of its panel: their container is the ground of the screen. */
@Preview(name = "Segments · the player: A | B, the big A/B with words, compact, «С минусовкой | Только скрипка»", widthDp = 412, heightDp = 420, locale = "ru")
@Composable
private fun PlayerSegmentsPreview() = ViolinTheme {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SegmentedSwitch(labels = listOf("A", "B"), selectedIndex = 1, onSelect = {}, modifier = Modifier.width(96.dp), containerColor = colors.surface, strong = true)
        // «Звук» (stage 113): «A оригинал | B обработка», the letters a step larger and heavier
        SegmentedSwitch(
            labels = listOf("оригинал", "обработка"),
            prefixes = listOf("A", "B"),
            selectedIndex = 1,
            onSelect = {},
            containerColor = colors.surface,
        )
        SegmentedSwitch(labels = listOf("A", "B"), selectedIndex = 0, onSelect = {}, modifier = Modifier.width(96.dp), compact = true, containerColor = colors.surface, strong = true)
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
            selectedIndex = 0,
            onSelect = {},
            containerColor = colors.surface,
            strong = true,
            shrinkToTwoLines = true,
        )
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
            selectedIndex = 1,
            onSelect = {},
            compact = true,
            containerColor = colors.surface,
            strong = true,
            shrinkToTwoLines = true,
        )
    }
}

/**
 * Spanish in the column of 270 lying: «Con acompañamiento» does not stand in its half at 14 sp — the regular switch takes both to two
 * lines of 12 in its 52; the compact one keeps one line, 12 sp, its halves by the words (seen 28, pressed 48).
 */
@Preview(name = "Segments · the backing, es, 270: two lines of 12 in the same height; compact — one line of 12 by the words", widthDp = 302, heightDp = 220, locale = "es")
@Composable
private fun BackingSegmentsSpanishPreview() = ViolinTheme {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
            selectedIndex = 0,
            onSelect = {},
            containerColor = colors.surface,
            strong = true,
            shrinkToTwoLines = true,
        )
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
            selectedIndex = 0,
            onSelect = {},
            compact = true,
            containerColor = colors.surface,
            strong = true,
            shrinkToTwoLines = true,
        )
    }
}

/** Russian at the font 1.3 in the column of 640 × 360 behind a cutout (≈ 278 for the rows): the compact backing in one line of 12. */
@Preview(name = "Segments · the compact backing, ru, 278, font 1.3: one line of 12", widthDp = 310, heightDp = 120, locale = "ru", fontScale = 1.3f)
@Composable
private fun CompactBackingLargeFontPreview() = ViolinTheme {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(16.dp)) {
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.backing_heard_with), stringResource(Res.string.backing_heard_violin)),
            selectedIndex = 1,
            onSelect = {},
            compact = true,
            containerColor = colors.surface,
            strong = true,
            shrinkToTwoLines = true,
        )
    }
}

@Preview(name = "Segments · status, ♭♮♯, major / minor dimmed, «Звук», compact", widthDp = 412, heightDp = 460, locale = "ru")
@Composable
private fun SegmentsPreview() = ViolinTheme {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SegmentedSwitch(
            labels = listOf(
                stringResource(Res.string.piece_status_reading),
                stringResource(Res.string.piece_status_learning),
                stringResource(Res.string.piece_status_in_repertoire),
            ),
            selectedIndex = 1,
            onSelect = {},
        )
        SegmentedSwitch(labels = listOf("♭", "♮", "♯"), selectedIndex = 2, onSelect = {}, fontSize = 20)
        // no tonic yet: the mode belongs to nothing, as in the form of a piece
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.piece_key_major), stringResource(Res.string.piece_key_minor)),
            selectedIndex = null,
            onSelect = {},
            modifier = Modifier.alpha(0.4f),
        )
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.sound_mode_everyone), stringResource(Res.string.sound_mode_own)),
            selectedIndex = 0,
            onSelect = {},
        )
        Text("compact · A/B of the player (R5)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedSwitch(labels = listOf("A", "B"), selectedIndex = 0, onSelect = {}, compact = true, fontSize = 13, modifier = Modifier.width(120.dp))
    }
}

@Preview(name = "Segments · fr, 360: a label on two lines", widthDp = 360, heightDp = 200, locale = "fr")
@Composable
private fun SegmentsFrPreview() = ViolinTheme {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedSwitch(
            labels = listOf(stringResource(Res.string.sound_mode_everyone), stringResource(Res.string.sound_mode_own)),
            selectedIndex = 1,
            onSelect = {},
        )
        SegmentedSwitch(
            labels = listOf(
                stringResource(Res.string.piece_status_reading),
                stringResource(Res.string.piece_status_learning),
                stringResource(Res.string.piece_status_in_repertoire),
            ),
            selectedIndex = 2,
            onSelect = {},
        )
    }
}

// The field (stage 102): the one field of the app, as the dialogs, the forms of R4 and the form of an event (R9) show it.

@Composable
private fun FieldGround(content: @Composable () -> Unit) = ViolinTheme {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Preview(name = "Field · empty with a placeholder", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun FieldEmptyPreview() = FieldGround {
    AppField(
        value = TextFieldValue(""),
        onValueChange = {},
        label = stringResource(Res.string.piece_field_title),
        placeholder = MENUET,
    )
}

@Preview(name = "Field · filled", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun FieldFilledPreview() = FieldGround {
    AppField(value = TextFieldValue(MENUET, TextRange(MENUET.length)), onValueChange = {}, label = stringResource(Res.string.piece_field_title))
}

@Preview(name = "Field · «Нужно название» and 0 / 24: grey with its icon, the frame does not redden", widthDp = 412, heightDp = 160, locale = "ru")
@Composable
private fun FieldErrorPreview() = FieldGround {
    AppField(
        value = TextFieldValue(""),
        onValueChange = {},
        label = stringResource(Res.string.section_name_label),
        error = stringResource(Res.string.dialog_name_needed),
        counter = stringResource(Res.string.profile_name_counter, 0, SECTION_NAME_MAX),
    )
}

@Preview(name = "Field · a hint under the field (the name of a recording)", widthDp = 412, heightDp = 170, locale = "ru")
@Composable
private fun FieldHintPreview() = FieldGround {
    val text = "Менуэт — чистый прогон"
    AppField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = {},
        label = stringResource(Res.string.session_rename_label),
        hint = stringResource(Res.string.session_rename_hint),
    )
}

@Preview(name = "Field · several lines", widthDp = 412, heightDp = 200, locale = "ru")
@Composable
private fun FieldMultilinePreview() = FieldGround {
    val text = "Держать смычок ближе к подставке во второй части.\nСчитать паузу в 12-м такте."
    AppField(
        value = TextFieldValue(text),
        onValueChange = {},
        label = stringResource(Res.string.piece_field_notes),
        singleLine = false,
        minLines = 3,
    )
}

@Preview(name = "Field · disabled: 0.38 in its own colours", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun FieldDisabledPreview() = FieldGround {
    AppField(value = TextFieldValue(MENUET), onValueChange = {}, label = stringResource(Res.string.piece_field_title), enabled = false)
}

@Preview(name = "Field · de, 360: the reason and the counter", widthDp = 360, heightDp = 170, locale = "de")
@Composable
private fun FieldDePreview() = FieldGround {
    AppField(
        value = TextFieldValue(""),
        onValueChange = {},
        label = stringResource(Res.string.section_name_label),
        error = stringResource(Res.string.dialog_name_needed),
        counter = stringResource(Res.string.profile_name_counter, 0, SECTION_NAME_MAX),
    )
}

// ---- Stage 117 (spec 3.36.7, 5.29 R7): the plate of what is missing, the buttons of one line, the square of glass and the purse.

@Composable
private fun ZoneGround(content: @Composable ColumnScope.() -> Unit) = ViolinTheme {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap),
        content = content,
    )
}

/** The stretch of [caption] from the first of the numbers to the end of the other — what a button of one line keeps whole. */
private fun numbersOf(caption: String, first: String, second: String): String {
    val start = minOf(caption.indexOf(first), caption.indexOf(second))
    val end = maxOf(caption.lastIndexOf(first) + first.length, caption.lastIndexOf(second) + second.length)
    return caption.substring(start, end)
}

@Composable
private fun MissingPlate(missing: Long, sessions: Int?, compact: Boolean = false) {
    val takts = Formats.takts(missing)
    ShortfallPlate(
        text = stringResource(Res.string.journey_missing, takts),
        modifier = Modifier.fillMaxWidth(),
        caption = sessions?.let { sessionsInWords(it) },
        compact = compact,
        keep = takts,
        leading = { TaktIcon(size = 18.dp) },
    )
}

@Preview(name = "Plate · one line: «не хватает 1 128», words, not a sleeping button", widthDp = 412, heightDp = 100, locale = "ru")
@Composable
private fun PlateOneLinePreview() = ZoneGround { MissingPlate(1_128, sessions = null) }

@Preview(name = "Plate · two lines: «не хватает 1 128» and «примерно 4 занятия»; under it the main button of the same height", widthDp = 412, heightDp = 170, locale = "ru")
@Composable
private fun PlateTwoLinesPreview() = ZoneGround {
    MissingPlate(1_128, sessions = 4)
    AppButton(stringResource(Res.string.journey_depart, cityOf(5)), onClick = {}, Modifier.fillMaxWidth(), icon = AppIcons.Travel)
}

@Preview(name = "Plate · a window no higher than 360: 48, two lines still", widthDp = 640, heightDp = 360, locale = "ru")
@Composable
private fun PlateCompactPreview() = ZoneGround { Column(Modifier.width(360.dp)) { MissingPlate(1_128, sessions = 4, compact = true) } }

@Preview(name = "Plate · de 360 at 1.3: «es fehlen 5 999» and «etwa 20 Mal üben», each on one line", widthDp = 360, heightDp = 120, locale = "de", fontScale = 1.3f)
@Composable
private fun PlateGermanPreview() = ZoneGround { MissingPlate(5_999, sessions = 20) }

@Composable
private fun TravelButton(fromIndex: Int, missing: Long) {
    val takts = Formats.takts(missing)
    AppButton(
        text = stringResource(Res.string.home_travel),
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        icon = AppIcons.Travel,
        caption = stringResource(Res.string.home_travel_line, cityOf(fromIndex), cityToOf(fromIndex + 1), takts),
        oneLine = true,
        keep = takts,
    )
}

@Composable
private fun DepartButton(toIndex: Int, price: Long, purse: Long) {
    val spent = Formats.takts(price)
    val balance = Formats.takts(purse)
    val caption = stringResource(Res.string.journey_depart_spend, spent, balance)
    AppButton(
        text = stringResource(Res.string.journey_depart, cityOf(toIndex)),
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        icon = AppIcons.Travel,
        caption = caption,
        oneLine = true,
        keep = numbersOf(caption, spent, balance),
    )
}

@Preview(name = "Button of one line · ru 360: «В дорогу» and «Санкт-Петербург → до Москвы 1 000»; «В путь · Санкт-Петербург»", widthDp = 360, heightDp = 190, locale = "ru")
@Composable
private fun OneLineRussianPreview() = ZoneGround {
    TravelButton(fromIndex = 11, missing = 1_000)
    DepartButton(toIndex = 11, price = 6_000, purse = 147_884)
}

@Preview(name = "Button of one line · ru 360 at 1.3: the words step down to 15, the city gives way, the numbers stay whole", widthDp = 360, heightDp = 200, locale = "ru", fontScale = 1.3f)
@Composable
private fun OneLineRussianLargePreview() = ZoneGround {
    TravelButton(fromIndex = 11, missing = 1_000)
    DepartButton(toIndex = 11, price = 6_000, purse = 147_884)
}

@Preview(name = "Button of one line · en 360 at 1.3: «Saint Petersburg → 1 000 to Moscow» — the number in the middle stays whole", widthDp = 360, heightDp = 200, locale = "en", fontScale = 1.3f)
@Composable
private fun OneLineEnglishLargePreview() = ZoneGround {
    TravelButton(fromIndex = 11, missing = 1_000)
    DepartButton(toIndex = 11, price = 6_000, purse = 147_884)
}

@Preview(name = "Button of one line · de and fr 360 at 1.3: «… werden ab…», «… seront dép…» — the numbers whole", widthDp = 360, heightDp = 200, locale = "de", fontScale = 1.3f)
@Composable
private fun OneLineGermanLargePreview() = ZoneGround {
    DepartButton(toIndex = 11, price = 6_000, purse = 147_884)
    TravelButton(fromIndex = 11, missing = 1_000)
}

@Preview(name = "Button of one line · fr 360 at 1.3", widthDp = 360, heightDp = 200, locale = "fr", fontScale = 1.3f)
@Composable
private fun OneLineFrenchLargePreview() = ZoneGround {
    DepartButton(toIndex = 11, price = 6_000, purse = 147_884)
    TravelButton(fromIndex = 11, missing = 1_000)
}

@Preview(name = "Glass over a picture · the square «на весь экран» and the purse in its pill (stage 118)", widthDp = 412, heightDp = 200, locale = "ru")
@Composable
private fun GlassSquarePreview() = ViolinTheme {
    Box(Modifier.size(412.dp, 200.dp).background(Brush.linearGradient(listOf(Color(0xFFE0A070), Color(0xFF46306A), Color(0xFF7FB2E0))))) {
        GlassSquare(AppIcons.Fullscreen, stringResource(Res.string.journey_fullscreen), onClick = {}, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
        BalancePill(47_884, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
