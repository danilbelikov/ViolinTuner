package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.add_kind_etude
import com.violinjourney.app.shared.resources.add_kind_own
import com.violinjourney.app.shared.resources.add_kind_piece
import com.violinjourney.app.shared.resources.add_kind_piece_caption
import com.violinjourney.app.shared.resources.add_kind_scale
import com.violinjourney.app.shared.resources.add_kind_scale_caption
import com.violinjourney.app.shared.resources.add_kind_stroke
import com.violinjourney.app.shared.resources.repertoire_add_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val PlateSize = 40.dp
private val PlateIcon = 24.dp
private val TitleBottom = 8.dp

/** A built-in kind the sheet offers, with the caption that says beforehand who has a photo and whose notes draw themselves. */
private class Kind(val section: PieceSection, val icon: ImageVector, val name: StringResource, val caption: StringResource?)

private val Kinds = listOf(
    Kind(PieceSection.PIECES, AppIcons.NoteOne, Res.string.add_kind_piece, Res.string.add_kind_piece_caption),
    Kind(PieceSection.SCALES, AppIcons.Scale, Res.string.add_kind_scale, Res.string.add_kind_scale_caption),
    Kind(PieceSection.ETUDES, AppIcons.Etude, Res.string.add_kind_etude, null),
    Kind(PieceSection.STROKES, AppIcons.Bow, Res.string.add_kind_stroke, null),
)

/**
 * «Что добавить?» (spec 3.36.4, repertoire.html 1): the one way in of «Добавить в репертуар» — a piece, a scale, an étude, a stroke,
 * then each section of the player's own by the alphabet. A row closes the sheet and opens the form of a new element of that section
 * ([SectionsIntent.KindPicked]); a swipe, a tap outside and «назад» only hide it ([SectionsIntent.AddSheetHidden]). No new section
 * is made here — that is «Свой раздел» on the tab. Up while [SectionsState.adding]; the built-in rows wait for no data, the player's
 * own come as soon as they are read. The frame of R1, never wider than 640; with many sections of one's own it scrolls.
 */
@Composable
fun AddSheet(state: SectionsState, onIntent: (SectionsIntent) -> Unit) {
    // One value for the whole time it is up: the sections of one's own read meanwhile change what it shows, and a list read while
    // it slides down after a swipe does not count as a new value to bring it up again.
    AppSheet(
        value = Unit.takeIf { state.adding },
        onHide = { onIntent(SectionsIntent.AddSheetHidden) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
    ) {
        AddSheetContent(state.own, onPick = { onIntent(SectionsIntent.KindPicked(it)) })
    }
}

/** The content of «Что добавить?»: its title and its rows — also the preview of the sheet, which a preview cannot open as a window. */
@Composable
fun ColumnScope.AddSheetContent(own: List<SectionCard>, onPick: (SectionRef) -> Unit) {
    Text(
        text = stringResource(Res.string.repertoire_add_title),
        modifier = Modifier.padding(bottom = TitleBottom).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
    )
    Kinds.forEach { kind ->
        ListRow(
            text = stringResource(kind.name),
            onClick = { onPick(SectionRef.BuiltIn(kind.section)) },
            caption = kind.caption?.let { stringResource(it) },
            leading = { SectionPlate(kind.icon, size = PlateSize, iconSize = PlateIcon) },
            strong = true,
        )
    }
    own.forEach { card ->
        ListRow(
            text = stringResource(Res.string.add_kind_own, card.name.orEmpty()),
            onClick = { onPick(card.ref) },
            leading = { SectionPlate(AppIcons.Folder, size = PlateSize, iconSize = PlateIcon) },
            strong = true,
            singleLine = true,
        )
    }
}
