package com.violinjourney.app.feature.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.MenuDanger
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.feature.repertoire.sections.SectionNameDialog
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.section_back
import com.violinjourney.app.shared.resources.section_delete
import com.violinjourney.app.shared.resources.section_delete_text_empty
import com.violinjourney.app.shared.resources.section_delete_text_few
import com.violinjourney.app.shared.resources.section_delete_text_many
import com.violinjourney.app.shared.resources.section_delete_text_one
import com.violinjourney.app.shared.resources.section_delete_title
import com.violinjourney.app.shared.resources.section_menu
import com.violinjourney.app.shared.resources.section_rename
import com.violinjourney.app.shared.resources.section_rename_title
import com.violinjourney.app.shared.resources.section_save
import org.jetbrains.compose.resources.stringResource

private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val Target = 48.dp
private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp

/**
 * The list of one section of the repertoire (spec 3.22, 3.36.4): over the tabs, with its name in the header, «⋯» for a section of
 * one's own, the count as a bar under it, and «Добавить …» of the section pinned in the bottom zone. One column up to 560 in the
 * middle, landscape too (with a header of 48); the bottom zone as wide. While the data is read — the header and the bottom zone,
 * which works at once. Stateless.
 */
@Composable
fun SectionScreen(state: RepertoireState, onIntent: (RepertoireIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = sectionName(state.section, state.sectionName)
    CompositionLocalProvider(LocalExerciseWords provides SectionKeys.isExercise(state.section)) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface),
        ) {
            val landscape = maxWidth > maxHeight
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TopBar(name, custom = state.custom, height = if (landscape) TopBarHeightLandscape else TopBarHeight, onIntent = onIntent)
                AppDock(
                    dock = {
                        AppButton(
                            text = stringResource(addLabelOf(state.section)),
                            onClick = { onIntent(RepertoireIntent.AddClicked) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = AppIcons.Plus,
                            compact = compact,
                        )
                    },
                    modifier = Modifier
                        .widthIn(max = MaxContentWidth)
                        .weight(1f),
                    metrics = currentDockMetrics().copy(side = ScreenPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = LocalDockInset.current + ScreenPadding),
                    ) { repertoireItems(state, onIntent) }
                }
            }
        }
    }
    when (state.dialog) {
        SectionDialog.RENAME -> SectionNameDialog(
            title = stringResource(Res.string.section_rename_title),
            confirm = stringResource(Res.string.section_save),
            name = state.nameDraft,
            maxLength = state.maxNameLength,
            canConfirm = state.canRename,
            onNameChange = { onIntent(RepertoireIntent.NameChanged(it)) },
            onConfirm = { onIntent(RepertoireIntent.DialogConfirmed) },
            onDismiss = { onIntent(RepertoireIntent.DialogDismissed) },
        )
        // The bin and the coral word of every deletion, but the text says what really happens: nothing is lost, the elements move
        // (handoff 24c).
        SectionDialog.DELETE -> DeleteDialog(
            title = stringResource(Res.string.section_delete_title, name),
            text = state.count.total.let { total ->
                if (total == 0) {
                    stringResource(Res.string.section_delete_text_empty)
                } else {
                    stringResource(Formats.plural(total, Res.string.section_delete_text_one, Res.string.section_delete_text_few, Res.string.section_delete_text_many), total)
                }
            },
            onConfirm = { onIntent(RepertoireIntent.DialogConfirmed) },
            onDismiss = { onIntent(RepertoireIntent.DialogDismissed) },
        )
        null -> Unit
    }
}

/** The header (5.29 R4): «назад» 48, the name of 18 sp / 800 on one line, «⋯» 48 for a section of one's own — «Переименовать», «Удалить раздел». */
@Composable
private fun TopBar(title: String, custom: Boolean, height: Dp, onIntent: (RepertoireIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Target)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(Res.string.section_back), role = Role.Button) { onIntent(RepertoireIntent.BackClicked) },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp)
                .semantics { heading() },
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        if (custom) {
            var open by remember { mutableStateOf(false) }
            val label = stringResource(Res.string.section_menu)
            Box {
                Box(
                    modifier = Modifier
                        .size(Target)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { open = true }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.More, contentDescription = null, tint = colors.onSurfaceVariant) }
                AppMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    danger = MenuDanger(stringResource(Res.string.section_delete), onClick = { open = false; onIntent(RepertoireIntent.DialogRequested(SectionDialog.DELETE)) }),
                ) {
                    AppMenuItem(
                        text = stringResource(Res.string.section_rename),
                        icon = AppIcons.Pencil,
                        onClick = { open = false; onIntent(RepertoireIntent.DialogRequested(SectionDialog.RENAME)) },
                    )
                }
            }
        }
    }
}
