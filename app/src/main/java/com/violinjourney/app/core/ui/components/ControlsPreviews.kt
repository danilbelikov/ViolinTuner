package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.backup_row_last
import com.violinjourney.app.shared.resources.backup_title
import com.violinjourney.app.shared.resources.form_section
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.nav_settings
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
import com.violinjourney.app.shared.resources.restore_busy_saving
import com.violinjourney.app.shared.resources.restore_button
import com.violinjourney.app.shared.resources.restore_title
import com.violinjourney.app.shared.resources.section_etudes
import com.violinjourney.app.shared.resources.section_pieces
import com.violinjourney.app.shared.resources.section_scales
import com.violinjourney.app.shared.resources.section_strokes
import com.violinjourney.app.shared.resources.settings_a4_title
import com.violinjourney.app.shared.resources.settings_tolerance_title
import com.violinjourney.app.shared.resources.tuning_string_hz
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
        ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Device, end = ListRowEnd.Toggle(checked = true))
    }
    SectionLabel("Мой путь", Modifier.padding(top = 6.dp))
    ListGroup {
        ListRow("Имя и фото", onClick = {}, icon = AppIcons.Gallery)
        ListRow(stringResource(Res.string.nav_settings), onClick = {}, icon = AppIcons.Gear)
        ListRow("Все трофеи", onClick = {}, icon = AppIcons.Trophy, accent = true)
    }
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
    ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Device, end = ListRowEnd.Toggle(checked = false))
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
        ListRow(stringResource(Res.string.analytics_row), onClick = {}, icon = AppIcons.Device, end = ListRowEnd.Toggle(checked = true))
    }
}

@Preview(name = "Rows · font 1.3", widthDp = 360, heightDp = 760, locale = "ru", fontScale = 1.3f)
@Composable
private fun RowsLargeFontPreview() = Kit { SettingsGroups() }
