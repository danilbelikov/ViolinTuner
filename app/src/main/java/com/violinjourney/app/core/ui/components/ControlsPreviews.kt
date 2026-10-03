package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.backup_row_last
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_save
import com.violinjourney.app.shared.resources.backup_row_saving
import com.violinjourney.app.shared.resources.backup_title
import com.violinjourney.app.shared.resources.event_add_mic
import com.violinjourney.app.shared.resources.event_mic_reason
import com.violinjourney.app.shared.resources.event_rest_on_weekdays
import com.violinjourney.app.shared.resources.event_series_following_lesson
import com.violinjourney.app.shared.resources.event_series_from_date
import com.violinjourney.app.shared.resources.event_series_this_lesson
import com.violinjourney.app.shared.resources.form_section
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.home_slot_deskTop
import com.violinjourney.app.shared.resources.nav_settings
import com.violinjourney.app.shared.resources.path_all_trophies
import com.violinjourney.app.shared.resources.path_name_photo
import com.violinjourney.app.shared.resources.path_title
import com.violinjourney.app.shared.resources.piece_delete_confirm
import com.violinjourney.app.shared.resources.piece_edit
import com.violinjourney.app.shared.resources.piece_sheets_add_short
import com.violinjourney.app.shared.resources.piece_status_in_repertoire
import com.violinjourney.app.shared.resources.piece_status_learning
import com.violinjourney.app.shared.resources.piece_status_reading
import com.violinjourney.app.shared.resources.piece_title_error
import com.violinjourney.app.shared.resources.practice_discard
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.practice_stop
import com.violinjourney.app.shared.resources.privacy_row
import com.violinjourney.app.shared.resources.restore_busy_saving
import com.violinjourney.app.shared.resources.restore_button
import com.violinjourney.app.shared.resources.restore_title
import com.violinjourney.app.shared.resources.section_etudes
import com.violinjourney.app.shared.resources.section_pieces
import com.violinjourney.app.shared.resources.section_scales
import com.violinjourney.app.shared.resources.section_strokes
import com.violinjourney.app.shared.resources.settings_a4_title
import com.violinjourney.app.shared.resources.settings_language
import com.violinjourney.app.shared.resources.settings_tolerance_title
import com.violinjourney.app.shared.resources.shop_all
import com.violinjourney.app.shared.resources.shop_group_instrument
import com.violinjourney.app.shared.resources.shop_group_music
import com.violinjourney.app.shared.resources.shop_place_clear
import com.violinjourney.app.shared.resources.take_grant_permission
import com.violinjourney.app.shared.resources.tuning_string_hz
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// The buttons, the chips and the rows of stage 102 (spec 3.36.1, 5.29; components.html, «Кнопки, чипы, строки»).

@Composable
private fun Kit(ground: @Composable () -> Color = { MaterialTheme.colorScheme.surface }, content: @Composable ColumnScope.() -> Unit) = ViolinTheme {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ground())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ColumnScope.AllButtons(compact: Boolean = false) {
    AppButton(stringResource(Res.string.practice_save), onClick = {}, Modifier.fillMaxWidth(), icon = AppIcons.Check, compact = compact)
    AppButton(stringResource(Res.string.practice_stop), onClick = {}, Modifier.fillMaxWidth(), style = AppButtonStyle.Outline, icon = AppIcons.Flag, compact = compact)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppButton(stringResource(Res.string.piece_edit), onClick = {}, Modifier.weight(1f), style = AppButtonStyle.Soft, icon = AppIcons.Pencil)
        AppButton(stringResource(Res.string.piece_sheets_add_short), onClick = {}, Modifier.weight(1f), style = AppButtonStyle.Soft, icon = AppIcons.Plus)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppButton(stringResource(Res.string.history_filter_all), onClick = {}, style = AppButtonStyle.Text)
        AppButton(stringResource(Res.string.piece_delete_confirm), onClick = {}, style = AppButtonStyle.Danger)
    }
    AppButton(stringResource(Res.string.practice_discard), onClick = {}, Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    AppButton(stringResource(Res.string.restore_button), onClick = {}, Modifier.fillMaxWidth(), style = AppButtonStyle.DangerFilled, icon = AppIcons.Restore, compact = compact)
    AppButton(
        stringResource(Res.string.practice_save),
        onClick = {},
        Modifier.fillMaxWidth(),
        enabled = false,
        reason = stringResource(Res.string.piece_title_error),
        compact = compact,
    )
}

@Preview(name = "Buttons · every look: main, outline, soft, text, danger, quiet, filled danger, dimmed with its reason", widthDp = 412, heightDp = 620, locale = "ru")
@Composable
private fun ButtonsPreview() = Kit { AllButtons() }

@Preview(name = "Buttons · compact 48: a window no higher than 360", widthDp = 412, heightDp = 580, locale = "ru")
@Composable
private fun ButtonsCompactPreview() = Kit { AllButtons(compact = true) }

@Preview(name = "Buttons · de, 360", widthDp = 360, heightDp = 640, locale = "de")
@Composable
private fun ButtonsDePreview() = Kit { AllButtons() }

@Preview(name = "Buttons · fr, 360, font 1.3: two lines, the button grows", widthDp = 360, heightDp = 760, locale = "fr", fontScale = 1.3f)
@Composable
private fun ButtonsFrPreview() = Kit { AllButtons() }

/** The answers of a deletion of a repeat (spec 3.36.9, 5.29 R9): outlines with coral words, neither filled, the dates under them. */
@Composable
private fun ColumnScope.DangerOutlines() {
    val date = Formats.dayAndMonth(LocalDate(2026, 10, 19))
    AppButton(
        stringResource(Res.string.event_series_this_lesson), onClick = {}, Modifier.fillMaxWidth().heightIn(min = 60.dp), style = AppButtonStyle.OutlineDanger,
        caption = stringArrayResource(Res.array.event_rest_on_weekdays).first(),
    )
    AppButton(
        stringResource(Res.string.event_series_following_lesson), onClick = {}, Modifier.fillMaxWidth().heightIn(min = 60.dp), style = AppButtonStyle.OutlineDanger,
        caption = stringResource(Res.string.event_series_from_date, date),
    )
    AppButton(stringResource(Res.string.piece_delete_confirm), onClick = {}, Modifier.fillMaxWidth(), style = AppButtonStyle.OutlineDanger)
}

@Preview(name = "Buttons · outline danger: the answers of a deletion of a repeat, with their dates; alone without a caption", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun OutlineDangerPreview() = Kit { DangerOutlines() }

@Preview(name = "Buttons · outline danger, de, 360, font 1.3: the words and the dates grow the button", widthDp = 360, heightDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun OutlineDangerGermanPreview() = Kit { DangerOutlines() }

@Composable
private fun FilterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppChip(stringResource(Res.string.history_filter_all), selected = true, onClick = {}, count = 5)
        AppChip(stringResource(Res.string.piece_status_reading), selected = false, onClick = {}, count = 1)
        AppChip(stringResource(Res.string.piece_status_learning), selected = false, onClick = {}, count = 2)
        AppChip(stringResource(Res.string.piece_status_in_repertoire), selected = false, onClick = {}, count = 2)
    }
}

@Composable
private fun ChoiceRow(inSheet: Boolean) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("30 мин", "45 мин", "1 ч", "1,5 ч").forEachIndexed { index, text ->
            AppChip.Choice(text, selected = index == 3, onClick = {}, inSheet = inSheet)
        }
    }
}

@Preview(name = "Chips · filter: a capsule of 40 with a frame, the count in the third level", widthDp = 412, heightDp = 100, locale = "ru")
@Composable
private fun FilterChipsPreview() = Kit { FilterRow() }

/** The chips of the shop by place (stage 119, spec 3.36.7): the place first — chosen, a cross of 18 inside it — then «Всё» and the rows. */
@Composable
private fun PlaceRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppChip(
            stringResource(Res.string.home_slot_deskTop),
            selected = true,
            onClick = {},
            trailing = AppIcons.Close,
            onClickLabel = stringResource(Res.string.shop_place_clear),
        )
        AppChip(stringResource(Res.string.shop_all), selected = false, onClick = {})
        AppChip(stringResource(Res.string.shop_group_instrument), selected = false, onClick = {})
        AppChip(stringResource(Res.string.shop_group_music), selected = false, onClick = {})
    }
}

@Preview(name = "Chips · the place of the shop first: chosen, with its cross; «Всё» not chosen (stage 119)", widthDp = 412, heightDp = 100, locale = "ru")
@Composable
private fun PlaceChipPreview() = Kit { PlaceRow() }

@Preview(name = "Chips · the place of the shop, de 360 at 1.3: the ribbon scrolls sideways", widthDp = 360, heightDp = 110, locale = "de", fontScale = 1.3f)
@Composable
private fun PlaceChipGermanPreview() = Kit { PlaceRow() }

@Preview(name = "Chips · choice on a screen and in a sheet, dimmed, actions", widthDp = 412, heightDp = 330, locale = "ru")
@Composable
private fun ChoiceChipsPreview() = ViolinTheme {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow(inSheet = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppChip.Choice(stringResource(Res.string.section_pieces), selected = false, onClick = {})
                AppChip.Choice(stringResource(Res.string.section_scales), selected = false, onClick = {}, enabled = false)
            }
        }
        // the ground of a sheet: the chips go one step lighter
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow(inSheet = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("0", "+10 мин", "+30 мин").forEach { text ->
                    AppChip.Choice(text, selected = null, onClick = {}, modifier = Modifier.widthIn(min = 60.dp), inSheet = true)
                }
            }
        }
    }
}

@Preview(name = "Chips · choice with the pencil of 16 before its words: «Другая…» and a length of one's own, chosen (stage 98б)", widthDp = 412, heightDp = 120, locale = "ru")
@Composable
private fun ChoiceIconChipsPreview() = ViolinTheme {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppChip.Choice("Другая…", selected = false, onClick = {}, icon = AppIcons.Pencil)
        AppChip.Choice("2 ч 30 мин", selected = true, onClick = {}, icon = AppIcons.Pencil)
        AppChip.Choice("Без длительности", selected = false, onClick = {})
    }
}

@Preview(name = "Chips · fr, 360", widthDp = 360, heightDp = 170, locale = "fr")
@Composable
private fun ChipsFrPreview() = Kit {
    FilterRow()
    ChoiceRow(inSheet = false)
}

@Composable
private fun ColumnScope.SettingsGroups() {
    SectionLabel("Интонация")
    ListGroup {
        ListRow(stringResource(Res.string.settings_a4_title), onClick = {}, icon = AppIcons.Fork, end = ListRowEnd.Value(stringResource(Res.string.tuning_string_hz, 440)))
        ListRow(stringResource(Res.string.settings_tolerance_title), onClick = {}, icon = AppIcons.NoteOne, end = ListRowEnd.Value("Средний ±8"))
    }
    SectionLabel("Данные", Modifier.padding(top = 6.dp))
    ListGroup {
        ListRow(stringResource(Res.string.backup_title), onClick = {}, icon = AppIcons.SaveCopy, caption = stringResource(Res.string.backup_row_last, "21 сентября"))
        ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Chart, end = ListRowEnd.Toggle(checked = true))
    }
    SectionLabel(stringResource(Res.string.path_title), Modifier.padding(top = 6.dp))
    ListGroup {
        ListRow(stringResource(Res.string.path_name_photo), onClick = {}, icon = AppIcons.Person)
        ListRow(stringResource(Res.string.nav_settings), onClick = {}, icon = AppIcons.Gear)
        ListRow(stringResource(Res.string.path_all_trophies), onClick = {}, end = ListRowEnd.Arrow, accent = true)
    }
}

@Preview(name = "Rows · «Мой путь»: «Все трофеи» with the arrow, «Имя и фото» with the photo of 32", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun PathRowsPreview() = Kit(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    val photo = remember { standInPhoto() }
    ListRow(stringResource(Res.string.path_all_trophies), onClick = {}, end = ListRowEnd.Arrow, accent = true)
    ListRow(
        stringResource(Res.string.path_name_photo),
        onClick = {},
        icon = AppIcons.Person,
        leading = { Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape)) },
    )
    ListRow(stringResource(Res.string.path_name_photo), onClick = {}, icon = AppIcons.Person)
    ListRow(stringResource(Res.string.nav_settings), onClick = {}, icon = AppIcons.Gear)
}

@Preview(name = "Rows · groups «Интонация · Данные · Мой путь»", widthDp = 412, heightDp = 640, locale = "ru")
@Composable
private fun RowsPreview() = Kit { SettingsGroups() }

@Preview(name = "Rows · a sheet: the section chosen with a check, a dimmed row, a switch off", widthDp = 412, heightDp = 400, locale = "ru")
@Composable
private fun SheetRowsPreview() = Kit(ground = { MaterialTheme.colorScheme.surfaceContainer }) {
    SectionLabel(stringResource(Res.string.form_section))
    Column(Modifier.selectableGroup()) {
        ListRow(stringResource(Res.string.section_pieces), onClick = {}, end = ListRowEnd.Check(checked = true))
        ListRow(stringResource(Res.string.section_etudes), onClick = {}, end = ListRowEnd.Check(checked = false))
        ListRow(stringResource(Res.string.section_strokes), onClick = {}, end = ListRowEnd.Check(checked = false))
        ListRow(stringResource(Res.string.section_scales), onClick = {}, end = ListRowEnd.Check(checked = false), enabled = false)
    }
    ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Chart, end = ListRowEnd.Toggle(checked = false))
}

@Preview(name = "Rows · a dimmed row in a group: its ground and the lines stay, the reason in its caption reads whole", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun DimmedRowInGroupPreview() = Kit {
    SectionLabel("Данные")
    ListGroup {
        ListRow(stringResource(Res.string.backup_title), onClick = {}, icon = AppIcons.SaveCopy, caption = stringResource(Res.string.backup_row_last, "21 сентября"))
        ListRow(
            stringResource(Res.string.restore_title),
            onClick = {},
            icon = AppIcons.Restore,
            caption = stringResource(Res.string.restore_busy_saving),
            end = ListRowEnd.None,
            enabled = false,
        )
        ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Chart, end = ListRowEnd.Toggle(checked = true))
    }
}

/**
 * The two parameters stage 121 gave the row (spec 3.36.8, 5.29 R8): a value with the chevron after it — «Язык» opens the system's
 * screen and shows what is set; something under the caption — the thin bar of a copy on its way; and an empty caption that holds the
 * place of its line while the date of the last copy is read.
 */
@Preview(name = "Rows · value with chevron, progress under caption, an empty caption holding its line, a switch not known yet", widthDp = 412, heightDp = 380, locale = "ru")
@Composable
private fun ValueAndProgressRowsPreview() = Kit {
    val colors = MaterialTheme.colorScheme
    ListGroup {
        ListRow(stringResource(Res.string.settings_language), onClick = {}, icon = AppIcons.Globe, end = ListRowEnd.Value("Русский", chevron = true))
        ListRow(
            stringResource(Res.string.backup_row_saving),
            onClick = {},
            caption = "56 % · Видео 7 из 12",
            leading = { AppIcon(AppIcons.SaveCopy, contentDescription = null, tint = colors.primary) },
            below = {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(0.56f).fillMaxHeight().background(colors.primary))
                }
            },
        )
        ListRow(stringResource(Res.string.backup_row_save), onClick = {}, icon = AppIcons.SaveCopy, caption = "")
        // the settings not read yet: the caption and the switch hold their places, a tap toggles nothing
        ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Chart, caption = "", end = ListRowEnd.Toggle(checked = null))
    }
}

/**
 * A narrow phone at the font 1.5 (spec 5.29 R8, stage 121): the value of «Язык» gives way to the word of the title — «Sprache» whole
 * at 16 sp, «Deutsch» cut; «Datenschutzerklärung» steps down to stand whole, and in 13 sp, the least, even that is not enough: the limit.
 */
@Preview(name = "Rows · de 320 at 1.5: «Sprache» whole, «Deutsch» gives way; long words a step smaller", widthDp = 320, heightDp = 300, locale = "de", fontScale = 1.5f)
@Composable
private fun NarrowLargeFontRowsPreview() = Kit {
    ListGroup {
        ListRow(stringResource(Res.string.settings_language), onClick = {}, icon = AppIcons.Globe, end = ListRowEnd.Value("Deutsch", chevron = true))
        ListRow(stringResource(Res.string.backup_row_restore), onClick = {}, icon = AppIcons.Restore)
        ListRow(stringResource(Res.string.privacy_row), onClick = {}, icon = AppIcons.Lock)
    }
}

@Preview(name = "Rows · font 1.3", widthDp = 360, heightDp = 760, locale = "ru", fontScale = 1.3f)
@Composable
private fun RowsLargeFontPreview() = Kit { SettingsGroups() }

/**
 * A word at the end of a row (spec 3.36.9: «Записать звук» without the microphone): the reason in the place of the caption, whole, and
 * «Разрешить доступ» a text button of 48 — beside the words where they keep their lines, under them on a phone.
 */
@Composable
private fun ColumnScope.TextActionRows() {
    ListRow(
        stringResource(Res.string.event_add_mic), onClick = {}, icon = AppIcons.Mic, caption = stringResource(Res.string.event_mic_reason),
        end = ListRowEnd.TextAction(stringResource(Res.string.take_grant_permission)) {}, strong = true,
    )
}

@Preview(name = "Rows · a word at the end: «Разрешить доступ» under the reason on a phone", widthDp = 360, heightDp = 160, locale = "ru")
@Composable
private fun TextActionPreview() = Kit { TextActionRows() }

@Preview(name = "Rows · a word at the end, 640 lying: beside the reason", widthDp = 640, heightDp = 120, locale = "ru")
@Composable
private fun TextActionWidePreview() = Kit { TextActionRows() }

@Preview(name = "Rows · a word at the end, de 360 at 1.3", widthDp = 360, heightDp = 220, locale = "de", fontScale = 1.3f)
@Composable
private fun TextActionGermanPreview() = Kit { TextActionRows() }

// The header of a screen over the tabs (spec 3.36.8, 5.29 R8; start.html, `.navbar`): made with R8, «Настройки» and the screens of a
// copy stand under it from stages 121–122.

@Composable
private fun Headers() = ViolinTheme {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        ScreenHeader(stringResource(Res.string.nav_settings), onBack = {})
        ScreenHeader(stringResource(Res.string.restore_title), onBack = {})
        ScreenHeader(title = null, onBack = {}, close = true)
        ScreenHeader(stringResource(Res.string.backup_title), onBack = null)
    }
}

// the height of a header follows the window (taller than wide — 56), so the upright previews are as tall as a phone
@Preview(name = "Header · «назад» and the title, ✕ alone, the title alone: 56", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun HeadersPreview() = Headers()

@Preview(name = "Header · de 360 at 1.3: «Aus Kopie wiederherstellen» on one line with an ellipsis", widthDp = 360, heightDp = 640, locale = "de", fontScale = 1.3f)
@Composable
private fun HeadersGermanPreview() = Headers()

@Preview(name = "Header · lying: 48", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun HeadersLyingPreview() = Headers()
