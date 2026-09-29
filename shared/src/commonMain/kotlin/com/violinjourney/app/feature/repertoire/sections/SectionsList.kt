package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.FieldDialog
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.SectionKeys
import com.violinjourney.app.feature.repertoire.components.dashedBorder
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.repertoire_welcome_text
import com.violinjourney.app.shared.resources.repertoire_welcome_title
import com.violinjourney.app.shared.resources.section_bar_description
import com.violinjourney.app.shared.resources.section_bar_description_pieces
import com.violinjourney.app.shared.resources.section_empty_count
import com.violinjourney.app.shared.resources.section_etudes
import com.violinjourney.app.shared.resources.section_learned
import com.violinjourney.app.shared.resources.section_name_label
import com.violinjourney.app.shared.resources.section_own_add
import com.violinjourney.app.shared.resources.section_pieces
import com.violinjourney.app.shared.resources.section_scales
import com.violinjourney.app.shared.resources.section_strokes
import org.jetbrains.compose.resources.stringResource

// The sections of the tab «Репертуар» (spec 3.36.4, 5.29 R4; repertoire.html 1, landscape.html 1). The fields of the tiles at
// their sides are in [TileFit]: the room of a name is counted from them.
private val RowGap = 10.dp
private val TileMinHeight = 112.dp
private val TileMinHeightLandscape = 72.dp
private val PlateIcon = 20.dp
private const val NAME_LINE = 1.25f
private val OwnRowMinHeight = 60.dp
private val AddRowMinHeight = 52.dp
private val AddRowIcon = 20.dp
private val WelcomePadding = 18.dp
private val WelcomeGap = 12.dp
private val BarHeight = 6.dp
private val EmptyBarHeight = 7.dp
private val BarGap = 2.dp
private const val BAR_MS = 400
private const val TABULAR_FIGURES = "tnum"

/**
 * How the tiles of the four built-in sections stand (spec 3.36.4): [Upright] — 112 at the least, the plate over the name; [Beside] —
 * 72, the plate to the left of the name (landscape). Two by two, or one under another in a column narrower than [NarrowColumn] or
 * too narrow for the longest word of the names ([TileFit]).
 */
enum class TileLook { Upright, Beside }

/** A column narrower than this puts the tiles one under another (spec 3.36.4, «Landscape вкладки»: «уже 320 dp»). */
val NarrowColumn = 320.dp

/**
 * How the sections stand in a column: how the tiles look, how many stand in a row and the one size of their names in sp ([TileFit]);
 * how long the bar of a row of one's own is ([SectionsLayout.ownRowBar]).
 */
@Immutable
data class SectionsColumn(val look: TileLook, val columns: Int, val nameSp: Float, val ownBar: Dp)

/**
 * The sections in a column [content] wide (spec 3.36.4, 5.29 R4): the four names measured word by word at the size of the tile, so
 * that none of them breaks inside a word — upright the names step down together, in landscape the tiles go one under another; in
 * a narrow column the bar of a row of one's own gives way to its words.
 */
@Composable
fun rememberSectionsColumn(look: TileLook, content: Dp): SectionsColumn {
    val widestAt = rememberWidestNameWord()
    val ownText = rememberOwnRowText()
    return remember(widestAt, ownText, look, content) {
        val columns = TileFit.columns(look, content, widestAt(TileFit.NAME_SP))
        SectionsColumn(look, columns, TileFit.nameSize(TileFit.nameRoom(look, content, columns), widestAt), SectionsLayout.ownRowBar(content, ownText))
    }
}

/**
 * The widest word of the four built-in names at a size of the tile name, in dp ([TileFit.words]) — what [rememberSectionsColumn] and
 * the choice of the landscape columns ([SectionsLayout.leftNeed]) measure. A new function when the words, the font or the density
 * change.
 */
@Composable
fun rememberWidestNameWord(): (sizeSp: Float) -> Dp {
    val names = PieceSection.entries.map { sectionName(SectionRef.BuiltIn(it), null) }
    val style = tileNameStyle(TileFit.NAME_SP)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // the style is a key: on iOS Manrope comes a frame after the first one, and the fit must follow the real font
    return remember(names, style, density, measurer) {
        val words = TileFit.words(names)
        val widestAt: (Float) -> Dp = { sizeSp ->
            val sized = style.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * NAME_LINE).sp)
            with(density) { words.maxOf { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width }.toDp() }
        }
        widestAt
    }
}

/** A wide letter and the ellipsis — how narrow a name of one's own may be cut. Measured, never shown. */
private const val FIRST_LETTER = "M\u2026"

/**
 * The least room of the words of a row of one's own ([SectionsLayout.ownRowBar], [SectionsLayout.ownRowNeed]): the first letter of
 * its name before the ellipsis, in the style of the name, or the widest word of a count — «выучено 888 из 888» or «пока пусто» — in
 * the style of the count, whichever is wider: the count goes on two lines, but a word of it is not broken.
 */
@Composable
fun rememberOwnRowText(): Dp {
    val nameStyle = ownNameStyle()
    val countStyle = countStyle()
    val countWords = TileFit.words(listOf(stringResource(Res.string.section_learned, COUNT_PROBE, COUNT_PROBE), stringResource(Res.string.section_empty_count)))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(nameStyle, countStyle, countWords, density, measurer) {
        val name = measurer.measure(FIRST_LETTER, nameStyle, softWrap = false, maxLines = 1).size.width
        val count = countWords.maxOf { measurer.measure(it, countStyle, softWrap = false, maxLines = 1).size.width }
        with(density) { maxOf(name, count).toDp() }
    }
}

/** A count of three figures on either side, the widest a count of a section is measured with. */
private const val COUNT_PROBE = 888

/**
 * The sections of the tab, as items of its lazy list (spec 3.36.4): on an empty repertoire the welcome card first; the four built-in
 * sections as tiles two by two (one by one when [tiles] has one column), the player's own as rows under them, then «Свой раздел».
 * Nothing while the data is read — the title and the bottom zone only, nothing blinks. The time by element is not here: it stands
 * under the sections in one column and in a column of its own in landscape.
 */
fun LazyListScope.sectionItems(state: SectionsState, onIntent: (SectionsIntent) -> Unit, layout: SectionsColumn, top: Dp) {
    if (state.loading) return
    val gridTop = if (state.total.total == 0) {
        item(key = "welcome") { WelcomeCard(Modifier.padding(top = top)) }
        WelcomeGap
    } else {
        top
    }
    state.builtIn.chunked(layout.columns).forEachIndexed { index, row ->
        item(key = "tiles-$index") {
            Row(
                modifier = Modifier
                    .padding(top = if (index == 0) gridTop else TileFit.Gap)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(TileFit.Gap),
            ) {
                row.forEach { card ->
                    SectionTile(card, layout, onClick = { onIntent(SectionsIntent.SectionClicked(card.ref)) }, Modifier.weight(1f).fillMaxHeight())
                }
                // a last row of one in two columns keeps the width of a tile
                repeat(layout.columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    state.own.forEach { card ->
        item(key = "own-" + SectionKeys.keyOf(card.ref)) {
            OwnSectionRow(card, layout.ownBar, onClick = { onIntent(SectionsIntent.SectionClicked(card.ref)) }, modifier = Modifier.padding(top = RowGap))
        }
    }
    item(key = "ownAdd") { OwnSectionAddRow(onClick = { onIntent(SectionsIntent.NewSectionClicked) }, modifier = Modifier.padding(top = RowGap)) }
}

/** The name of a section: a word of the interface for the four built-in ones, the player's own otherwise. */
@Composable
fun sectionName(ref: SectionRef, name: String?): String = when (ref) {
    is SectionRef.Custom -> name.orEmpty()
    is SectionRef.BuiltIn -> stringResource(
        when (ref.section) {
            PieceSection.PIECES -> Res.string.section_pieces
            PieceSection.SCALES -> Res.string.section_scales
            PieceSection.ETUDES -> Res.string.section_etudes
            PieceSection.STROKES -> Res.string.section_strokes
        },
    )
}

/** The icon of a section (spec 3.36.4): a note, a scale, an étude, a bow; a folder for one of the player's own. */
fun sectionIcon(ref: SectionRef): ImageVector = when (ref) {
    is SectionRef.Custom -> AppIcons.Folder
    is SectionRef.BuiltIn -> when (ref.section) {
        PieceSection.PIECES -> AppIcons.NoteOne
        PieceSection.SCALES -> AppIcons.Scale
        PieceSection.ETUDES -> AppIcons.Etude
        PieceSection.STROKES -> AppIcons.Bow
    }
}

/** «выучено 4 из 12», or «пока пусто». */
@Composable
fun sectionCountLabel(count: SectionCount): String =
    if (count.total == 0) stringResource(Res.string.section_empty_count) else stringResource(Res.string.section_learned, count.learned, count.total)

/**
 * The shares of a section in words (spec 3.36.4): «разбираю 1, учу 3, в репертуаре 2» — the third step as the section says it,
 * «выучено» in «Гаммы», «Этюды» and «Штрихи» (3.22). Null for an empty section: there are no shares to tell.
 */
@Composable
fun sharesDescription(ref: SectionRef, count: SectionCount): String? {
    if (count.total == 0) return null
    val words = if (SectionKeys.isExercise(ref)) Res.string.section_bar_description else Res.string.section_bar_description_pieces
    return stringResource(words, count.reading, count.learning, count.learned)
}

/** «выучено 2 из 6: разбираю 1, учу 3, в репертуаре 2», or «пока пусто»: the count of a section as one phrase for TalkBack. */
@Composable
fun countDescription(ref: SectionRef, count: SectionCount): String {
    val label = sectionCountLabel(count)
    return sharesDescription(ref, count)?.let { "$label: $it" } ?: label
}

/** The plate of the icon of a section — 36 at a corner of 12 on surfaceContainerHigh, the icon in the accent (5.29 R4). */
@Composable
fun SectionPlate(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = TileFit.Plate, iconSize: Dp = PlateIcon) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShapes.S),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = null, size = iconSize, tint = MaterialTheme.colorScheme.primary) }
}

/**
 * A built-in section as a tile (spec 3.36.4): the plate, the name of 16 sp / 800 on up to two lines — smaller when a word of the
 * names would not fit its line ([TileFit]) — the count and, at the bottom, its shares. The tile grows with its words, 112 at the
 * least (72 in landscape, the plate to the left). One target and one phrase for TalkBack — «Произведения, выучено 2 из 6: разбираю
 * 1, учу 3, в репертуаре 2» — a button.
 */
@Composable
private fun SectionTile(card: SectionCard, layout: SectionsColumn, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = sectionName(card.ref, card.name)
    val description = "$name, " + countDescription(card.ref, card.count)
    val column = modifier
        .clip(AppShapes.M)
        .background(colors.surfaceContainer)
        .clickable(role = Role.Button, onClick = onClick)
        .clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
        }
    when (layout.look) {
        TileLook.Upright -> Column(
            modifier = column
                .heightIn(min = TileMinHeight)
                .padding(start = TileFit.UprightStart, top = 14.dp, end = TileFit.UprightEnd, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionPlate(sectionIcon(card.ref), Modifier.padding(bottom = 2.dp))
            TileName(name, layout.nameSp)
            TileCount(card.count)
            Spacer(Modifier.weight(1f))
            SectionBar(card.count)
        }
        TileLook.Beside -> Column(
            modifier = column
                .heightIn(min = TileMinHeightLandscape)
                .padding(start = TileFit.BesideStart, top = 10.dp, end = TileFit.BesideEnd, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TileFit.PlateGap)) {
                SectionPlate(sectionIcon(card.ref))
                Column(Modifier.weight(1f)) {
                    TileName(name, layout.nameSp)
                    TileCount(card.count)
                }
            }
            // 8 over the bar at the least; a tile stretched to its neighbour keeps the bar at its bottom
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            SectionBar(card.count)
        }
    }
}

/** The name on a tile, [sizeSp] of [SectionsColumn.nameSp]: up to two lines, broken only between words. */
@Composable
private fun TileName(name: String, sizeSp: Float) {
    Text(
        text = name,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = tileNameStyle(sizeSp),
    )
}

/** 800 on a line of 1.25 of its size — 16 on 20 (5.29 R4); the same style the names are measured in. */
@Composable
private fun tileNameStyle(sizeSp: Float): TextStyle =
    MaterialTheme.typography.titleMedium.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * NAME_LINE).sp, fontWeight = FontWeight.ExtraBold)

/** When all of a section is learnt its count steps forward: there is nothing left to learn (3.22). */
@Composable
private fun TileCount(count: SectionCount) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = sectionCountLabel(count),
        color = if (count.allLearned) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = countStyle(),
    )
}

/** The count of a section on its tile or row — 13 sp / 600, tabular figures; the same style it is measured in. */
@Composable
private fun countStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES)

/** The name of a section of one's own in its row — 16 sp / 800 on 21; the same style it is measured in. */
@Composable
private fun ownNameStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)

/**
 * A section of the player's own as a row under the tiles (spec 3.36.4): the folder on its plate, the name on one line with an
 * ellipsis, the count, and a short bar of shares at the end — 60 at the least. One phrase for TalkBack, a button.
 */
@Composable
private fun OwnSectionRow(card: SectionCard, bar: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = sectionName(card.ref, card.name)
    val description = "$name, " + countDescription(card.ref, card.count)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OwnRowMinHeight)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = SectionsLayout.OwnRowSide, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SectionsLayout.OwnRowGap),
    ) {
        SectionPlate(AppIcons.Folder)
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ownNameStyle(),
            )
            TileCount(card.count)
        }
        SectionBar(card.count, Modifier.width(bar))
    }
}

/** «Свой раздел» (spec 3.36.4): a quiet dashed row with a plus — a section is made rarely. Opens «Новый раздел». */
@Composable
private fun OwnSectionAddRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AddRowMinHeight)
            .dashedBorder(colors.outlineVariant, corner = 18.dp)
            .clip(AppShapes.M)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        AppIcon(AppIcons.Plus, contentDescription = null, size = AddRowIcon, tint = colors.onSurfaceVariant)
        Text(
            text = stringResource(Res.string.section_own_add),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** An empty repertoire (spec 3.36.4): one warm word under the title, over the tiles that show what the sections will be. */
@Composable
private fun WelcomeCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .padding(WelcomePadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.repertoire_welcome_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            text = stringResource(Res.string.repertoire_welcome_text),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.5.sp),
        )
    }
}

/**
 * The shares of a section (spec 3.36.4, 5.29 R4): «разбираю», «учу», «выучено» from the left, always in that order, in the three
 * tones of violet on a track of surfaceContainerHigh, 2 apart — light is only what is learnt, so «0 из 3» has no light at all. No
 * legend. An empty section is not an empty track but a dashed outline 7 high: the empty must not read as full. The shares settle
 * over 400 ms when they change. Says nothing itself: whoever places it says what it is ([countDescription]).
 */
@Composable
fun SectionBar(count: SectionCount, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (count.total == 0) {
        Box(
            modifier
                .fillMaxWidth()
                .height(EmptyBarHeight)
                .dashedBorder(colors.outlineVariant, corner = EmptyBarHeight / 2),
        )
        return
    }
    val exercise = ViolinTheme.exerciseColors
    val total = count.total.toFloat()
    val reading by animateFloatAsState(count.reading / total, tween(BAR_MS), label = "sectionReading")
    val learning by animateFloatAsState(count.learning / total, tween(BAR_MS), label = "sectionLearning")
    val learned by animateFloatAsState(count.learned / total, tween(BAR_MS), label = "sectionLearned")
    val track = colors.surfaceContainerHigh
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .drawBehind {
                // a capsule that cuts the shares, as the mockup's track with its overflow hidden: only the ends are round
                val capsule = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))) }
                clipPath(capsule) {
                    drawRect(track)
                    val gap = BarGap.toPx()
                    val shares = listOf(reading to exercise.reading, learning to exercise.learning, learned to exercise.learned).filter { it.first > 0.001f }
                    val room = size.width - gap * (shares.size - 1).coerceAtLeast(0)
                    var x = 0f
                    shares.forEach { (share, color) ->
                        val width = room * share
                        drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
                        x += width + gap
                    }
                }
            },
    )
}

/**
 * «Новый раздел» and «Переименовать раздел» (spec 3.36.1): one field with its counter; while it is empty «Создать» / «Сохранить» is
 * dimmed and «Нужно название» stands by the counter.
 */
@Composable
fun SectionNameDialog(
    title: String,
    confirm: String,
    name: String,
    maxLength: Int,
    canConfirm: Boolean,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FieldDialog(
        title = title,
        label = stringResource(Res.string.section_name_label),
        initial = name,
        confirm = confirm,
        // the view model holds the name: it has heard every change of the field
        onConfirm = { onConfirm() },
        onDismiss = onDismiss,
        onValueChange = onNameChange,
        maxLength = maxLength,
        confirmEnabled = canConfirm,
    )
}
