package com.violinjourney.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.A4Selector
import com.violinjourney.app.core.ui.components.ListGroup
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.core.ui.components.LocalListGroupGround
import com.violinjourney.app.core.ui.components.ScreenHeader
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.TolerancePresetList
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.format.languageName
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.sound.captionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_block_title
import com.violinjourney.app.shared.resources.nav_settings
import com.violinjourney.app.shared.resources.settings_a4_note
import com.violinjourney.app.shared.resources.settings_a4_title
import com.violinjourney.app.shared.resources.settings_group_app
import com.violinjourney.app.shared.resources.settings_group_intonation
import com.violinjourney.app.shared.resources.settings_group_records
import com.violinjourney.app.shared.resources.settings_language
import com.violinjourney.app.shared.resources.settings_restart_caption
import com.violinjourney.app.shared.resources.settings_restart_onboarding
import com.violinjourney.app.shared.resources.settings_tolerance_note
import com.violinjourney.app.shared.resources.settings_tolerance_title
import com.violinjourney.app.shared.resources.sound_settings_row
import com.violinjourney.app.shared.resources.sound_settings_row_caption
import org.jetbrains.compose.resources.stringResource

// «Настройки» of the redesign (spec 3.36.8, 5.29 R8; start.html, 3).
/** The column of the groups: no wider than this, in the middle of a wider window. */
private val MaxColumnWidth = 480.dp
private val ScreenSide = 16.dp
private val ScreenBottom = 24.dp

/** A label of a group: 22 over it (8 under the header for the first), 8 to its group, 4 in from the edges of the group. */
private val GroupLabelTop = 22.dp
private val FirstGroupLabelTop = 8.dp
private val GroupLabelBottom = 8.dp
private val GroupLabelInset = 4.dp

/** A row of a choice: its fields, the note's gap beside the title or under it, and the segments under them. */
private val ChoiceRowSide = 16.dp
private val ChoiceRowVertical = 14.dp
private val NoteBeside = 12.dp
private val NoteUnder = 2.dp
private val ControlTop = 10.dp

/**
 * What a test of a row of «Интонация» measures, though a reader never meets it: the title and the note are the name of the group of
 * segments under them, so they carry this tag and nothing else.
 */
internal const val CHOICE_TITLE_TAG = "settings choice title"

/**
 * «Настройки» (spec 3.8, 3.36.8): over the tabs, without the bottom bar — the header with «назад» and the title, then four groups of
 * rows under their labels, read by them: «Интонация» (the reference and the tolerance, segments in their rows), «Записи» («Звук
 * записей»), «Данные» (the copy, the restore, the statistics and the policy — [dataBlock], with a view model of its own) and
 * «Приложение» («Язык» and «Пройти знакомство снова»). There is no main action and so no bottom zone: everything acts at once. One
 * column no wider than 480 in the middle of the window, upright and lying; it scrolls, the header stands.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    /** The arrow of the header; null — none (previews). */
    onBack: (() -> Unit)? = null,
    /** The group «Данные» (spec 3.20, 3.34): it has a view model of its own, the settings know nothing of copies. */
    dataBlock: @Composable () -> Unit = {},
    /** Opens the system's choice of the language of this app; null where the system has none (before Android 13) — there the app follows the device. */
    onLanguageClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        ScreenHeader(stringResource(Res.string.nav_settings), onBack)
        // the whole width scrolls, not only the column in its middle
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = MaxColumnWidth)
                    .fillMaxWidth()
                    .padding(start = ScreenSide, end = ScreenSide, bottom = ScreenBottom),
            ) {
                GroupLabel(stringResource(Res.string.settings_group_intonation), first = true)
                ListGroup {
                    val a4 = stringResource(Res.string.settings_a4_title)
                    val a4Note = stringResource(Res.string.settings_a4_note)
                    ChoiceRow(a4, a4Note) {
                        A4Selector(
                            optionsHz = state.a4OptionsHz,
                            selectedHz = state.a4Hz,
                            onSelect = { onIntent(SettingsIntent.A4Selected(it)) },
                            description = "$a4, $a4Note",
                        )
                    }
                    val tolerance = stringResource(Res.string.settings_tolerance_title)
                    val toleranceNote = stringResource(Res.string.settings_tolerance_note)
                    ChoiceRow(tolerance, toleranceNote) {
                        TolerancePresetList(
                            selected = state.tolerance,
                            onSelect = { onIntent(SettingsIntent.ToleranceSelected(it)) },
                            description = "$tolerance, $toleranceNote",
                        )
                    }
                }
                GroupLabel(stringResource(Res.string.settings_group_records))
                ListGroup {
                    // a way in, not a control: the default sound of all recordings has a screen of its own (spec 3.17)
                    ListRow(
                        text = stringResource(Res.string.sound_settings_row),
                        onClick = { onIntent(SettingsIntent.SoundClicked) },
                        icon = AppIcons.Sound,
                        // not read yet: the line held empty, not «без обработки» before the sound that is set (spec 3.36.8 «Загрузка»)
                        caption = if (state.read) stringResource(Res.string.sound_settings_row_caption, captionName(state.sound)) else "",
                    )
                }
                GroupLabel(stringResource(Res.string.backup_block_title))
                dataBlock()
                GroupLabel(stringResource(Res.string.settings_group_app))
                ListGroup {
                    if (onLanguageClick != null) {
                        ListRow(
                            text = stringResource(Res.string.settings_language),
                            onClick = onLanguageClick,
                            icon = AppIcons.Globe,
                            // the language names itself in itself: «Deutsch», «한국어» — whoever looks for theirs finds it
                            end = ListRowEnd.Value(languageName(Formats.language.tag), chevron = true),
                        )
                    }
                    ListRow(
                        text = stringResource(Res.string.settings_restart_onboarding),
                        onClick = { onIntent(SettingsIntent.RestartOnboardingClicked) },
                        icon = AppIcons.Repeat,
                        caption = stringResource(Res.string.settings_restart_caption),
                    )
                }
            }
        }
    }
}

/** The label of a group (spec 5.29 R8): the label of a section of R1 — a heading for a reader. */
@Composable
private fun GroupLabel(text: String, first: Boolean = false) {
    SectionLabel(
        text = text,
        modifier = Modifier.padding(
            start = GroupLabelInset,
            end = GroupLabelInset,
            top = if (first) FirstGroupLabelTop else GroupLabelTop,
            bottom = GroupLabelBottom,
        ),
    )
}

/**
 * A row of «Интонация» (spec 3.36.8, 5.29 R8): no icon, the [title] and its [note] on one line — or the note under the title where
 * they do not stand together — and under them, 10 down, the segments of the [control]. The row paints the ground of its group. A
 * reader hears the title and the note as the name of the group of segments (the control is given them), not as a line of its own.
 */
@Composable
private fun ChoiceRow(title: String, note: String, control: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalListGroupGround.current)
            .padding(horizontal = ChoiceRowSide, vertical = ChoiceRowVertical),
    ) {
        TitleWithNote(title, note, Modifier.clearAndSetSemantics { testTag = CHOICE_TITLE_TAG })
        Spacer(Modifier.height(ControlTop))
        control()
    }
}

/**
 * «Эталон A4» at 16 sp, 700 and «все ноты считаются от него» at 13 sp to the right of it on the same baseline; where the two do not
 * stand on one line with 12 between them (German, a large font) the note goes under the title, 2 down, from its start.
 */
@Composable
private fun TitleWithNote(title: String, note: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Layout(
        content = {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = note,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.5.sp),
            )
        },
        modifier = modifier,
    ) { (titleWords, noteWords), constraints ->
        val room = constraints.maxWidth
        val gap = NoteBeside.roundToPx()
        val titleWidth = titleWords.maxIntrinsicWidth(Constraints.Infinity)
        val noteWidth = noteWords.maxIntrinsicWidth(Constraints.Infinity)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        if (titleWidth + gap + noteWidth <= room) {
            val titlePlaced = titleWords.measure(loose)
            val notePlaced = noteWords.measure(loose)
            // one baseline: the taller line above it decides where both stand
            val above = maxOf(titlePlaced[FirstBaseline], notePlaced[FirstBaseline])
            val titleTop = above - titlePlaced[FirstBaseline]
            val noteTop = above - notePlaced[FirstBaseline]
            val height = maxOf(titleTop + titlePlaced.height, noteTop + notePlaced.height)
            layout(room, height) {
                titlePlaced.placeRelative(0, titleTop)
                notePlaced.placeRelative(room - notePlaced.width, noteTop)
            }
        } else {
            val titlePlaced = titleWords.measure(loose)
            val notePlaced = noteWords.measure(loose)
            val under = NoteUnder.roundToPx()
            layout(room, titlePlaced.height + under + notePlaced.height) {
                titlePlaced.placeRelative(0, 0)
                notePlaced.placeRelative(0, titlePlaced.height + under)
            }
        }
    }
}
