package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.TabTitle
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_repertoire
import com.violinjourney.app.shared.resources.section_create
import com.violinjourney.app.shared.resources.section_new_title
import org.jetbrains.compose.resources.stringResource

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val TitleTop = 12.dp
private val LandscapeTitleTop = 8.dp
private val LandscapeTimeWidth = 360.dp

/**
 * The tab «Репертуар» (spec 3.36.1): its title, then the sections (spec 3.22) with «Время по элементам» (spec 3.28) as
 * they were under the switch of «Записи» — upright one column up to 560 dp, in landscape the time in the left column of
 * 360 dp and the sections on the right (handoff 30h6). Stage R4 draws the tab anew. Stateless.
 */
@Composable
fun SectionsScreen(state: SectionsState, onIntent: (SectionsIntent) -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(Res.string.nav_repertoire)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (maxWidth > maxHeight) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
                TabTitle(title, Modifier.padding(top = LandscapeTitleTop), compact = true)
                Row(modifier = Modifier.padding(top = LandscapeTitleTop), horizontalArrangement = Arrangement.spacedBy(ScreenPadding)) {
                    state.time?.let { time ->
                        PieceTimeCardView(
                            card = time,
                            visibleRows = TIME_ROWS_LANDSCAPE,
                            onIntent = onIntent,
                            modifier = Modifier
                                .width(LandscapeTimeWidth)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = ScreenPadding)) {
                        sectionItems(state, onIntent, showTime = false)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = TitleTop, bottom = ScreenPadding),
            ) {
                item(key = "title") { TabTitle(title) }
                sectionItems(state, onIntent, showTime = true)
            }
        }
    }
    state.newName?.let { name ->
        SectionNameDialog(
            title = stringResource(Res.string.section_new_title),
            confirm = stringResource(Res.string.section_create),
            name = name,
            maxLength = state.maxNameLength,
            canConfirm = state.canCreate,
            onNameChange = { onIntent(SectionsIntent.NameChanged(it)) },
            onConfirm = { onIntent(SectionsIntent.CreateConfirmed) },
            onDismiss = { onIntent(SectionsIntent.DialogDismissed) },
        )
    }
}
