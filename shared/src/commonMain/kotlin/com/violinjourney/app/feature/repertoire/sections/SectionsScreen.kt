package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.TabTitle
import com.violinjourney.app.core.ui.components.appButtonMinWidth
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.nav_repertoire
import com.violinjourney.app.shared.resources.repertoire_add_any
import com.violinjourney.app.shared.resources.section_create
import com.violinjourney.app.shared.resources.section_new_title
import org.jetbrains.compose.resources.stringResource

// The tab «Репертуар» of R4 (spec 3.36.4, 5.29 R4; repertoire.html 1, landscape.html 1).
private val ScreenPadding = SectionsLayout.ScreenPadding
private val MaxContentWidth = 560.dp
private val TitleTop = 12.dp
private val CountTop = 4.dp

/** From the title to the tiles — 12, in landscape too: landscape changes only the size of the title and puts the count beside it (5.29 R4). */
private val TilesTop = 12.dp
private val LandscapeTop = 8.dp
private val LandscapeTitleMinHeight = 32.dp
private val LandscapeTitleGap = 12.dp

/** The label of the time on the right stands level with the line of the title on the left: 8 + (32 − 18) / 2. */
private val LandscapeTimeTop = 15.dp

/**
 * The tab «Репертуар» (spec 3.36.4): the title and the count of everything, the four built-in sections as tiles two by two, the
 * player's own as rows, «Свой раздел», and under them «Время по элементам»; «Добавить в репертуар» pinned in the bottom zone over
 * the tabs opens «Что добавить?». Upright — one column up to 560. In landscape the sections are the way in: they stand on the left
 * with the bottom zone as wide as their column, the time on the right in a column of 360 that scrolls to the bar (was the other
 * way round in 3.28 and R1); an empty repertoire has no time, and a window whose left column would not hold a tile, a row of one's
 * own and the button (a split screen) has no room for two columns — the tab is then one column in the middle, the time under the
 * sections ([SectionsLayout]). While the data is read — the title and the
 * bottom zone only (the landscape keeps its columns, the right one empty: a repertoire is empty more rarely than not). Stateless.
 */
@Composable
fun SectionsScreen(state: SectionsState, onIntent: (SectionsIntent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        val content = minOf(maxWidth, MaxContentWidth) - ScreenPadding * 2
        val leftNeed = rememberLeftColumnNeed()
        when (SectionsLayout.of(maxWidth, maxHeight, loading = state.loading, hasTime = state.time != null, leftNeed = leftNeed)) {
            SectionsLayout.Kind.Upright -> OneColumn(state, onIntent, landscape = false, content = content)
            SectionsLayout.Kind.Columns -> LandscapeColumns(state, onIntent)
            SectionsLayout.Kind.Wide -> OneColumn(state, onIntent, landscape = true, content = content)
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
    AddSheet(state, onIntent)
}

/**
 * What the left column of landscape needs inside its fields ([SectionsLayout.leftNeed]), measured: the widest word of the names at
 * their smallest size on a tile alone in its row, the words of a row of one's own beside its shortest bar, and «Добавить в
 * репертуар» at the height the window gives its zone.
 */
@Composable
private fun rememberLeftColumnNeed(): Dp {
    val widestAt = rememberWidestNameWord()
    val ownText = rememberOwnRowText()
    val button = appButtonMinWidth(stringResource(Res.string.repertoire_add_any), icon = true, compact = currentDockMetrics().compact)
    return remember(widestAt, ownText, button) {
        SectionsLayout.leftNeed(widestWord = widestAt(TileFit.MIN_NAME_SP), ownText = ownText, button = button)
    }
}

/**
 * Upright, and in landscape without the two columns: one column up to 560 in the middle with the bottom zone as wide, «Время по
 * элементам» under the sections, three rows. [landscape] — the title and the tiles of landscape, lower. [content] — the width inside
 * the fields.
 */
@Composable
private fun OneColumn(state: SectionsState, onIntent: (SectionsIntent) -> Unit, landscape: Boolean, content: Dp) {
    val layout = rememberSectionsColumn(if (landscape) TileLook.Beside else TileLook.Upright, content)
    AppDock(
        dock = { AddButton(onIntent) },
        modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
        metrics = currentDockMetrics().copy(side = ScreenPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                top = if (landscape) LandscapeTop else TitleTop,
                end = ScreenPadding,
                bottom = LocalDockInset.current + ScreenPadding,
            ),
        ) {
            item(key = "title") { if (landscape) LandscapeTitle(state) else Title(state) }
            sectionItems(state, onIntent, layout, top = TilesTop)
            val time = state.time
            if (time != null) {
                item(key = "time") { PieceTimeSection(time, visibleRows = TIME_ROWS_UPRIGHT, onIntent = onIntent) }
            }
        }
    }
}

/**
 * Landscape (landscape.html 1): the sections and their bottom zone on the left, 16 from the edge; «Время по элементам» on the right,
 * the card 360 wide and 16 from the edge (the 16 between the columns is the left one's), five rows, scrolling to the bar. A left
 * column narrower than [NarrowColumn] (640 × 360), or too narrow for the longest word of the names beside the plates ([TileFit]),
 * puts the tiles one under another.
 */
@Composable
private fun LandscapeColumns(state: SectionsState, onIntent: (SectionsIntent) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val layout = rememberSectionsColumn(TileLook.Beside, maxWidth - ScreenPadding * 2)
            AppDock(
                dock = { AddButton(onIntent) },
                modifier = Modifier.fillMaxSize(),
                metrics = currentDockMetrics().copy(side = ScreenPadding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        top = LandscapeTop,
                        end = ScreenPadding,
                        bottom = LocalDockInset.current + ScreenPadding,
                    ),
                ) {
                    item(key = "title") { LandscapeTitle(state) }
                    sectionItems(state, onIntent, layout, top = TilesTop)
                }
            }
        }
        Column(
            modifier = Modifier
                .width(SectionsLayout.TimeColumn)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = ScreenPadding, bottom = ScreenPadding),
        ) {
            state.time?.let { time ->
                PieceTimeSection(time, visibleRows = TIME_ROWS_LANDSCAPE, onIntent = onIntent, top = LandscapeTimeTop)
            }
        }
    }
}

/** «Добавить в репертуар»: one button for everything, the section is picked in the sheet — it works while the data is read too. */
@Composable
private fun DockScope.AddButton(onIntent: (SectionsIntent) -> Unit) {
    AppButton(
        text = stringResource(Res.string.repertoire_add_any),
        onClick = { onIntent(SectionsIntent.AddToRepertoireClicked) },
        modifier = Modifier.fillMaxWidth(),
        icon = AppIcons.Plus,
        compact = compact,
    )
}

/** The title of the tab and under it the count of everything, 14 sp / 600 — none while the data is read. */
@Composable
private fun Title(state: SectionsState) {
    Column {
        TabTitle(stringResource(Res.string.nav_repertoire))
        if (!state.loading) Count(state, Modifier.padding(top = CountTop))
    }
}

/**
 * Landscape: the title of 24 sp and the count on one line, 12 apart, by their baseline (5.29 R4). When the two do not fit the column
 * (640 × 360, a large font, a long count in Portuguese), the count goes under the title, 4 under it, as upright, and is cut there if
 * it must: the title of the tab is never cut for the count ([SectionsLayout.countBesideTitle]). No count while the data is read.
 */
@Composable
private fun LandscapeTitle(state: SectionsState) {
    Layout(
        content = {
            TabTitle(stringResource(Res.string.nav_repertoire), compact = true)
            if (!state.loading) Count(state)
        },
        modifier = Modifier.heightIn(min = LandscapeTitleMinHeight),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val title = measurables[0].measure(loose)
        val count = measurables.getOrNull(1)
        if (count == null) {
            layout(constraints.constrainWidth(title.width), constraints.constrainHeight(title.height)) { title.place(0, 0) }
        } else {
            val gap = LandscapeTitleGap.roundToPx()
            if (SectionsLayout.countBesideTitle(title.width, gap, count.maxIntrinsicWidth(Constraints.Infinity), constraints.maxWidth)) {
                val beside = count.measure(loose.copy(maxWidth = (constraints.maxWidth - title.width - gap).coerceAtLeast(0)))
                val baseline = maxOf(title[FirstBaseline], beside[FirstBaseline])
                val titleY = baseline - title[FirstBaseline]
                val countY = baseline - beside[FirstBaseline]
                val height = maxOf(titleY + title.height, countY + beside.height)
                layout(constraints.constrainWidth(title.width + gap + beside.width), constraints.constrainHeight(height)) {
                    title.place(0, titleY)
                    beside.place(title.width + gap, countY)
                }
            } else {
                val under = count.measure(loose)
                val countY = title.height + CountTop.roundToPx()
                layout(constraints.constrainWidth(maxOf(title.width, under.width)), constraints.constrainHeight(countY + under.height)) {
                    title.place(0, 0)
                    under.place(0, countY)
                }
            }
        }
    }
}

/** «выучено 3 из 15» — 14 sp / 600, text-2, tabular figures; one line, cut only when even a line of its own is too narrow. */
@Composable
private fun Count(state: SectionsState, modifier: Modifier = Modifier) {
    Text(
        text = sectionCountLabel(state.total),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    )
}
