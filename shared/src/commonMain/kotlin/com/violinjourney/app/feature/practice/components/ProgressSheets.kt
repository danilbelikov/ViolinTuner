package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppField
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeSheet
import com.violinjourney.app.feature.practice.TrophyLine
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.path_name_photo
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.profile_name_counter
import com.violinjourney.app.shared.resources.profile_name_label
import com.violinjourney.app.shared.resources.profile_name_note
import com.violinjourney.app.shared.resources.profile_name_placeholder
import com.violinjourney.app.shared.resources.profile_no_name_letter
import com.violinjourney.app.shared.resources.profile_other_photo
import com.violinjourney.app.shared.resources.profile_pick_photo
import com.violinjourney.app.shared.resources.profile_remove_photo
import com.violinjourney.app.shared.resources.progress_trophy_names
import com.violinjourney.app.shared.resources.trophies_count
import com.violinjourney.app.shared.resources.trophies_far
import com.violinjourney.app.shared.resources.trophies_heading_description
import com.violinjourney.app.shared.resources.trophies_line_far
import com.violinjourney.app.shared.resources.trophies_line_given
import com.violinjourney.app.shared.resources.trophies_line_next
import com.violinjourney.app.shared.resources.trophies_line_remaining
import com.violinjourney.app.shared.resources.trophies_remaining
import com.violinjourney.app.shared.resources.trophies_title
import com.violinjourney.app.shared.resources.trophies_total
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// «Имя и фото» (spec 3.36.3, 5.29 R3; practice-sheets.html 7).
private val AvatarSize = 104.dp
private val AvatarTop = 6.dp
private val AvatarBottom = 12.dp
private val PhotoButtonsGap = 8.dp
private val FieldTop = 18.dp
private val NoteTop = 6.dp
private const val NOTE_SIZE = 13

// «Трофеи» (spec 3.36.3, 5.29 R3; practice-sheets.html 6).
private const val TITLE_SIZE = 22
private const val SMALL_SIZE = 13
private val TotalBottom = 6.dp
private val RowMinHeight = 68.dp
private val RowGap = 12.dp
private val TileSize = 48.dp
private val TileShape = RoundedCornerShape(14.dp)
private val TrophyArtSize = 34.dp
private const val NAME_SIZE = 15

/** The frame of the nearest trophy reaches this far past the rows on each side, its content stays in line with them. */
private val NextBleed = 12.dp
private val NextMargin = 6.dp
private val FarTop = 18.dp
private val FarBottom = 4.dp
private const val FAR_SIZE = 12
private const val FAR_TRACKING = 0.06
private val DividerThickness = 1.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * «Имя и фото» (was «Профиль», spec 3.13, 3.36.3; practice-sheets.html 7): the label, the photo — or the first letter of the name, or
 * «?» with neither — «Выбрать фото», or «Другое фото» and «Убрать фото» when there is one ([hasPhoto]); both dimmed while a picked
 * photo is copied. The field of the name with its hint inside, the counter «0 / 24» under it and the line «Видно только вам…». One
 * thing only: the row «Настройки» stands in «Мой путь». «Готово» is [NamePhotoButtons], at the bottom of the sheet — over the keyboard,
 * whose inset the sheet itself takes once. In portrait the field has the focus and the keyboard is open at once; in landscape not.
 * The name is stored however the sheet is closed (spec 3.13).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamePhotoSheetContent(
    sheet: PracticeSheet.Profile,
    hasPhoto: Boolean,
    photo: ImageBitmap?,
    onIntent: (PracticeIntent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.path_name_photo)
    // The field owns its text: the state comes back from the view model a frame later, and a field fed from it loses the cursor to
    // fast typing. The view model only hears of changes.
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(sheet.nameDraft, TextRange(sheet.nameDraft.length)))
    }
    val focus = remember { FocusRequester() }
    val portrait = LocalWindowInfo.current.containerSize.let { it.width < it.height }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }, horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(title)
        // the letter follows the field as it is typed; with nothing typed it asks
        val letter = initialOf(value.text.trim()).ifEmpty { stringResource(Res.string.profile_no_name_letter) }
        Avatar(photo, AvatarFallback.Letter(letter), AvatarSize, Modifier.padding(top = AvatarTop, bottom = AvatarBottom))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PhotoButtonsGap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(PhotoButtonsGap),
        ) {
            AppButton(
                text = stringResource(if (hasPhoto) Res.string.profile_other_photo else Res.string.profile_pick_photo),
                onClick = onPickPhoto,
                style = AppButtonStyle.Soft,
                icon = AppIcons.Gallery,
                enabled = !sheet.importingPhoto,
            )
            if (hasPhoto) {
                AppButton(
                    text = stringResource(Res.string.profile_remove_photo),
                    onClick = { onIntent(PracticeIntent.ProfilePhotoRemoved) },
                    style = AppButtonStyle.Danger,
                    enabled = !sheet.importingPhoto,
                )
            }
        }
        AppField(
            value = value,
            onValueChange = { next ->
                val text = next.text.takeCodePoints(Profile.MAX_NAME_LENGTH)
                val cut = if (text == next.text) next else TextFieldValue(text, TextRange(text.length))
                val changed = cut.text != value.text
                value = cut
                if (changed) onIntent(PracticeIntent.ProfileNameChanged(cut.text))
            },
            label = stringResource(Res.string.profile_name_label),
            modifier = Modifier.padding(top = FieldTop).focusRequester(focus),
            placeholder = stringResource(Res.string.profile_name_placeholder),
            counter = stringResource(Res.string.profile_name_counter, value.text.codePointLength(), Profile.MAX_NAME_LENGTH),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(PracticeIntent.ProfileClosed) }),
            showLabel = false,
            inSheet = true,
        )
        Text(
            text = stringResource(Res.string.profile_name_note),
            modifier = Modifier.fillMaxWidth().padding(top = NoteTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = NOTE_SIZE.sp, lineHeight = 19.sp),
        )
        // In the composition of the sheet's window, after the field: the field is there to take the focus. Only in portrait — in
        // landscape the keyboard would take the whole sheet before a word is asked for (spec 3.36.3).
        if (portrait) LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/** «Готово» of «Имя и фото»: the name is stored and «Мой путь» comes back. */
@Composable
fun NamePhotoButtons(onIntent: (PracticeIntent) -> Unit, modifier: Modifier = Modifier) {
    AppSheetButtons(main = stringResource(Res.string.profile_done), onMain = { onIntent(PracticeIntent.ProfileClosed) }, modifier = modifier)
}

/**
 * «Трофеи» — «Все трофеи» of «Мой путь» (spec 3.13, 3.36.3; practice-sheets.html 6): «Трофеи» 22 sp with «2 из 10» on the right — how
 * many are given — and «всего за скрипкой — 47 ч 17 мин»; the marks from the lowest, each a row of 68: the drawing on its tile, the
 * name, the mark; on the right the date of a given one in the colour of text (a memory, not a task) or what is left. The nearest one
 * in a dashed frame with its remainder in the accent — the one highlight of the list; «Далеко впереди» and the rows under it quieter in
 * colour, without remainders. No buttons: a swipe and «назад» give «Мой путь» back.
 */
@Composable
fun TrophiesSheetContent(lines: List<TrophyLine>, totalMs: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(Res.array.progress_trophy_names)
    val title = stringResource(Res.string.trophies_title)
    // given in fact, seen or not — as the dates of the rows under it
    val count = stringResource(Res.string.trophies_count, lines.count { it.awardedDate != null }, lines.size)
    val said = stringResource(Res.string.trophies_heading_description, title, count)
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = SMALL_SIZE.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Row(
            // «Трофеи, 2 из 10» — a heading, read once
            Modifier.fillMaxWidth().clearAndSetSemantics {
                heading()
                contentDescription = said
            },
            horizontalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).alignByBaseline(),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_SIZE.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(count, Modifier.alignByBaseline(), color = colors.onSurfaceVariant, maxLines = 1, style = small)
        }
        Text(
            text = stringResource(Res.string.trophies_total, Formats.totalTime(totalMs)),
            modifier = Modifier.padding(bottom = TotalBottom),
            color = colors.onSurfaceVariant,
            style = small,
        )
        lines.forEachIndexed { index, line ->
            // the label stands before the first far mark only
            if (line.isFar && lines.getOrNull(index - 1)?.isFar != true) FarLabel()
            TrophyRow(line, names.getOrElse(line.index) { "" })
            // a line under each row but the last; the framed one has its frame
            if (index < lines.lastIndex && !line.isNext) HorizontalDivider(thickness = DividerThickness, color = colors.outlineVariant)
        }
    }
}

/** «ДАЛЕКО ВПЕРЕДИ» (5.29 R3): a label 12 sp, 700, capitals, in the third level of text, without a line. */
@Composable
private fun FarLabel() {
    Text(
        text = stringResource(Res.string.trophies_far).uppercase(),
        modifier = Modifier.padding(top = FarTop, bottom = FarBottom),
        color = ViolinTheme.textTertiary,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = FAR_SIZE.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = FAR_TRACKING.em),
    )
}

@Composable
private fun TrophyRow(line: TrophyLine, name: String) {
    val colors = MaterialTheme.colorScheme
    val tertiary = ViolinTheme.textTertiary
    val given = line.awardedDate != null
    val hours = Formats.hoursMark(line.hours)
    val date = line.awardedDate?.let(Formats::dayAndMonth)
    val remaining = line.remainingMs?.let(Formats::remainingTime)
    val description = when {
        date != null -> stringResource(Res.string.trophies_line_given, name, hours, date)
        remaining != null && line.isNext -> stringResource(Res.string.trophies_line_next, name, hours, remaining)
        remaining != null -> stringResource(Res.string.trophies_line_remaining, name, hours, remaining)
        else -> stringResource(Res.string.trophies_line_far, name, hours)
    }
    val frame = if (line.isNext) {
        Modifier
            .padding(vertical = NextMargin)
            .bleed(NextBleed)
            .dashedFrame(colors.outlineVariant)
            .padding(horizontal = NextBleed)
    } else {
        Modifier
    }
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = SMALL_SIZE.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(frame)
            .heightIn(min = RowMinHeight)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Box(Modifier.size(TileSize).background(colors.surface, TileShape), contentAlignment = Alignment.Center) {
            TrophyIcon(line.hours, locked = !given, size = TrophyArtSize)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (line.isFar) tertiary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = NAME_SIZE.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(hours, color = if (line.isFar) tertiary else colors.onSurfaceVariant, maxLines = 1, style = small)
        }
        when {
            date != null -> Text(date, color = colors.onSurface, maxLines = 1, style = small)
            remaining != null -> Text(
                text = stringResource(Res.string.trophies_remaining, remaining),
                color = if (line.isNext) colors.primary else colors.onSurfaceVariant,
                maxLines = 1,
                style = if (line.isNext) small.copy(fontWeight = FontWeight.Bold) else small,
            )
        }
    }
}

/**
 * Wider than the place it is given by [side] on each side, the extra drawn past it: the frame of the nearest trophy reaches into the
 * fields of the sheet while its content stays in line with the other rows.
 */
private fun Modifier.bleed(side: Dp): Modifier = layout { measurable, constraints ->
    val extra = (side * 2).roundToPx()
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
    val wide = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-side.roundToPx(), 0) }
}
