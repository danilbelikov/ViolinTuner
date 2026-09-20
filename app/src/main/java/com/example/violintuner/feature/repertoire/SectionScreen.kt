package com.example.violintuner.feature.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.components.DeleteDialog
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.repertoire.components.LocalExerciseWords
import com.example.violintuner.feature.repertoire.sections.SectionNameDialog
import com.example.violintuner.feature.repertoire.sections.sectionCountLabel
import com.example.violintuner.feature.repertoire.sections.sectionName

private val TopBarHeight = 56.dp
private val Target = 48.dp
private val MaxContentWidth = 560.dp

/** The list of one section of the repertoire (spec 3.22, handoff 24c): over the tabs, with its name and count for a title. Stateless. */
@Composable
fun SectionScreen(state: RepertoireState, onIntent: (RepertoireIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = sectionName(state.section, state.sectionName)
    CompositionLocalProvider(LocalExerciseWords provides SectionKeys.isExercise(state.section)) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(name, sectionCountLabel(state.count).takeIf { !state.loading }, custom = state.custom, onIntent = onIntent)
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) { repertoireItems(state, onIntent) }
        }
    }
    when (state.dialog) {
        SectionDialog.RENAME -> SectionNameDialog(
            title = stringResource(R.string.section_rename_title),
            confirm = stringResource(R.string.section_save),
            name = state.nameDraft,
            maxLength = state.maxNameLength,
            canConfirm = state.canRename,
            onNameChange = { onIntent(RepertoireIntent.NameChanged(it)) },
            onConfirm = { onIntent(RepertoireIntent.DialogConfirmed) },
            onDismiss = { onIntent(RepertoireIntent.DialogDismissed) },
        )
        // A red button, but the text says what really happens: nothing is lost, the elements move (handoff 24c).
        SectionDialog.DELETE -> DeleteDialog(
            title = stringResource(R.string.section_delete_title, name),
            text = state.count.total.let { total ->
                if (total == 0) {
                    stringResource(R.string.section_delete_text_empty)
                } else {
                    stringResource(Formats.pluralRu(total, R.string.section_delete_text_one, R.string.section_delete_text_few, R.string.section_delete_text_many), total)
                }
            },
            onConfirm = { onIntent(RepertoireIntent.DialogConfirmed) },
            onDismiss = { onIntent(RepertoireIntent.DialogDismissed) },
        )
        null -> Unit
    }
}

@Composable
private fun TopBar(title: String, count: String?, custom: Boolean, onIntent: (RepertoireIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Target)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.section_back), role = Role.Button) { onIntent(RepertoireIntent.BackClicked) },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            if (count != null) {
                Text(
                    text = count,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                )
            }
        }
        if (custom) {
            var open by remember { mutableStateOf(false) }
            val label = stringResource(R.string.section_menu)
            Box {
                Box(
                    modifier = Modifier
                        .size(Target)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { open = true }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.More, contentDescription = null, tint = colors.onSurfaceVariant) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceContainerHigh) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_rename)) },
                        leadingIcon = { AppIcon(AppIcons.Pencil, contentDescription = null) },
                        onClick = { open = false; onIntent(RepertoireIntent.DialogRequested(SectionDialog.RENAME)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_delete), color = ViolinTheme.destructive) },
                        leadingIcon = { AppIcon(AppIcons.Trash, contentDescription = null, tint = ViolinTheme.destructive) },
                        onClick = { open = false; onIntent(RepertoireIntent.DialogRequested(SectionDialog.DELETE)) },
                    )
                }
            }
        }
    }
}
