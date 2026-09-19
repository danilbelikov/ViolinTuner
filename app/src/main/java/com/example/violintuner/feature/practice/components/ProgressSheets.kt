package com.example.violintuner.feature.practice.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.progress.Profile
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.practice.PracticeIntent
import com.example.violintuner.feature.practice.PracticeSheet
import com.example.violintuner.feature.practice.ProfileHeader
import com.example.violintuner.feature.practice.TrophyLine

private val SheetAvatar = 112.dp
private val TextButtonHeight = 40.dp
private val FieldCorner = 16.dp
private val TrophyLineHeight = 56.dp
private val TrophyLineIcon = 40.dp
private const val TABULAR_FIGURES = "tnum"
private const val FAR_ALPHA = 0.7f

/** «Профиль»: the photo and the name (spec 3.13, handoff 11d1, 11d2). Closing it any way stores the name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(sheet: PracticeSheet.Profile, header: ProfileHeader, onIntent: (PracticeIntent) -> Unit) {
    // The system photo picker: no permission is involved, the app gets one picture and no more.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onIntent(PracticeIntent.ProfilePhotoPicked(uri.toString()))
    }
    ModalBottomSheet(
        onDismissRequest = { onIntent(PracticeIntent.ProfileClosed) },
        // Whole at once: half a sheet under a keyboard would hide the field it was opened for.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ProfileSheetContent(
            sheet = sheet,
            header = header,
            onIntent = onIntent,
            onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

@Composable
internal fun ProfileSheetContent(
    sheet: PracticeSheet.Profile,
    header: ProfileHeader,
    onIntent: (PracticeIntent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // The field owns its text: the state comes back from the view model a frame later, and a
    // field fed from it loses the cursor to fast typing. The view model only hears of changes.
    var name by rememberSaveable { mutableStateOf(sheet.nameDraft) }
    // The keyboard lifts the sheet's content: «Готово» stays in sight (handoff 11d2).
    SheetColumn(modifier.imePadding()) {
        Text(
            text = stringResource(R.string.profile_title),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The letter follows the field as it is typed; with nothing typed it asks.
            val letter = initialOf(name.trim()).ifEmpty { stringResource(R.string.profile_no_name_letter) }
            Avatar(path = header.avatarPath, fallback = AvatarFallback.Letter(letter), size = SheetAvatar)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SheetTextButton(stringResource(R.string.profile_pick_photo), AppIcons.Gallery, enabled = !sheet.importingPhoto, onClick = onPickPhoto)
                if (header.avatarPath != null) {
                    SheetTextButton(stringResource(R.string.profile_remove_photo), AppIcons.Trash, enabled = !sheet.importingPhoto, destructive = true) {
                        onIntent(PracticeIntent.ProfilePhotoRemoved)
                    }
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(Profile.MAX_NAME_LENGTH)
                onIntent(PracticeIntent.ProfileNameChanged(name))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_name_label)) },
            placeholder = { Text(stringResource(R.string.profile_name_placeholder), color = colors.onSurfaceVariant) },
            trailingIcon = {
                Text(
                    text = stringResource(R.string.profile_name_counter, name.length, Profile.MAX_NAME_LENGTH),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
                    modifier = Modifier.clearAndSetSemantics { },
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(FieldCorner),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(PracticeIntent.ProfileClosed) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedLabelColor = colors.primary,
                unfocusedLabelColor = colors.primary,
                cursorColor = colors.primary,
            ),
        )
        PrimaryButton(text = stringResource(R.string.profile_done), onClick = { onIntent(PracticeIntent.ProfileClosed) })
    }
}

@Composable
private fun SheetTextButton(text: String, icon: ImageVector, enabled: Boolean, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = ViolinTheme.destructive) else ButtonDefaults.textButtonColors()
    TextButton(onClick = onClick, enabled = enabled, colors = colors, modifier = Modifier.height(TextButtonHeight)) {
        IconLabel(icon, text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** «Трофеи»: every mark, lowest first (spec 3.13, handoff 11e). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophiesSheet(lines: List<TrophyLine>, totalMs: Long, onIntent: (PracticeIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(PracticeIntent.TrophiesClosed) },
        // Whole at once: the far marks are the point of the list, not something to dig for.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        TrophiesSheetContent(lines, totalMs)
    }
}

@Composable
internal fun TrophiesSheetContent(lines: List<TrophyLine>, totalMs: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val names = stringArrayResource(R.array.progress_trophy_names)
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        SheetColumn {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier.alignByBaseline(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(AppIcons.Trophy, contentDescription = null, tint = colors.primary)
                    Text(
                        text = stringResource(R.string.trophies_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Text(
                    text = stringResource(R.string.trophies_total, Formats.totalTime(totalMs)),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Column {
                lines.forEachIndexed { index, line ->
                    // The divider stands before the first far mark only.
                    if (line.isFar && lines.getOrNull(index - 1)?.isFar != true) FarDivider()
                    TrophyLineRow(line, name = names.getOrElse(line.index) { "" })
                }
            }
        }
    }
}

@Composable
private fun FarDivider() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.trophies_far).uppercase(Formats.LOCALE),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.44.sp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.outlineVariant),
        )
    }
}

@Composable
private fun TrophyLineRow(line: TrophyLine, name: String) {
    val colors = MaterialTheme.colorScheme
    val given = line.awardedDate != null
    val hours = Formats.hoursMark(line.hours)
    val status = when {
        line.awardedDate != null -> stringResource(R.string.trophies_awarded, Formats.dayAndMonth(line.awardedDate))
        line.remainingMs != null -> stringResource(R.string.trophies_remaining, Formats.remainingTime(line.remainingMs))
        else -> ""
    }
    val description = when {
        line.awardedDate != null -> stringResource(R.string.trophies_line_given, name, hours, Formats.dayAndMonth(line.awardedDate))
        line.remainingMs != null -> stringResource(R.string.trophies_line_remaining, name, hours, Formats.remainingTime(line.remainingMs))
        else -> stringResource(R.string.trophies_line_far, name, hours)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrophyLineHeight)
            .alpha(if (line.isFar) FAR_ALPHA else 1f)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(TrophyLineIcon), contentAlignment = Alignment.Center) {
            TrophyIcon(line.hours, locked = !given, size = TrophyLineIcon)
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = name,
                color = if (given || line.isNext) colors.onSurface else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline(),
            )
            Text(
                text = hours,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            text = status,
            color = if (given) colors.onSurface else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            maxLines = 1,
        )
    }
}
