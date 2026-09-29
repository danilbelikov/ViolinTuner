package com.violinjourney.app.feature.repertoire.form

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.feature.repertoire.sections.SectionPlate
import com.violinjourney.app.feature.repertoire.sections.sectionIcon
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.form_section
import com.violinjourney.app.shared.resources.section_scales_only
import org.jetbrains.compose.resources.stringResource

// The sheet «Раздел» (spec 3.36.4, 5.29 R4, «Листы «Что добавить?» и «Раздел»»).
private val TitleBottom = 8.dp
private val PlateSize = 40.dp
private val PlateIcon = 24.dp
private const val TITLE_SIZE = 20

/**
 * The content of the sheet «Раздел» (spec 3.36.4): a row for every section there is, each with the plate of its icon — the built-in
 * ones in their order, the player's own by the alphabet, a long name on one line with an ellipsis — and the check on the current one.
 * «Гаммы» stands at 0.38 with «только для гамм» under it and does not answer (3.22): TalkBack reads «Гаммы, только для гамм,
 * недоступно». A row puts the section into the draft at once and closes the sheet ([PieceFormIntent.SectionSelected]). Also the
 * preview of the sheet.
 */
@Composable
fun ColumnScope.SectionSheetContent(state: PieceFormState, onIntent: (PieceFormIntent) -> Unit) {
    Text(
        text = stringResource(Res.string.form_section),
        modifier = Modifier.padding(bottom = TitleBottom).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = TITLE_SIZE.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
    )
    state.sections.forEach { option ->
        ListRow(
            text = sectionName(option.ref, option.name),
            onClick = { onIntent(PieceFormIntent.SectionSelected(option.ref)) },
            caption = if (option.enabled) null else stringResource(Res.string.section_scales_only),
            end = ListRowEnd.Check(option.ref == state.section),
            enabled = option.enabled,
            leading = { SectionPlate(sectionIcon(option.ref), size = PlateSize, iconSize = PlateIcon) },
            strong = true,
            singleLine = option.ref is SectionRef.Custom,
        )
    }
}
