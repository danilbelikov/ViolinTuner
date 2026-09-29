package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.components.SegmentFit
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.SheetThumb
import com.violinjourney.app.feature.repertoire.components.THUMB_DIM
import com.violinjourney.app.feature.repertoire.components.THUMB_DIM_FIRST
import com.violinjourney.app.feature.repertoire.components.dashedBorder
import com.violinjourney.app.feature.repertoire.scale.NotationSizes
import com.violinjourney.app.feature.repertoire.scale.ScaleNotation
import com.violinjourney.app.feature.repertoire.scale.scaleRange
import com.violinjourney.app.feature.repertoire.scale.scaleTitle
import com.violinjourney.app.feature.repertoire.scale.systemCount
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.piece_sheet_page
import com.violinjourney.app.shared.resources.piece_sheets_add
import com.violinjourney.app.shared.resources.piece_sheets_camera
import com.violinjourney.app.shared.resources.piece_sheets_gallery
import com.violinjourney.app.shared.resources.piece_sheets_none
import com.violinjourney.app.shared.resources.piece_sheets_pages
import com.violinjourney.app.shared.resources.scale_add_photo
import com.violinjourney.app.shared.resources.scale_add_photo_hint
import com.violinjourney.app.shared.resources.scale_more_systems_few
import com.violinjourney.app.shared.resources.scale_more_systems_many
import com.violinjourney.app.shared.resources.scale_more_systems_one
import com.violinjourney.app.shared.resources.scale_notes
import com.violinjourney.app.shared.resources.scale_open_stand
import org.jetbrains.compose.resources.stringResource

// The music of an element (spec 3.36.4, 5.29 R4, «Произведение»; repertoire.html 3).
private val TileCorner = 12.dp
private val TileGap = 8.dp
private val NumberPlate = 20.dp
private val NumberCorner = 7.dp
private val NumberInset = 5.dp
private val PlusSize = 28.dp
private val EmptyTileWidth = 150.dp
private val EmptyTileSide = 8.dp
private val EmptyTileCorner = 14.dp
private const val EMPTY_NAME_SP = EmptySheetsFit.NAME_SP
private const val EMPTY_NAME_LINE = 16f / 13f
private val EmptyIcon = 28.dp
private val ImportBar = 3.dp
private const val TILE_APPEAR_MS = 200
private const val PAPER_WHILE_COPIED = 0.5f
private const val TABULAR_FIGURES = "tnum"

/** The tiles of the strip: 76 × 98 upright, 66 × 88 lying (5.29 R4). */
internal class SheetMetrics(val tileWidth: Dp, val tileHeight: Dp) {
    companion object {
        val Portrait = SheetMetrics(tileWidth = 76.dp, tileHeight = 98.dp)
        val Landscape = SheetMetrics(tileWidth = 66.dp, tileHeight = 88.dp)
    }
}

/** Callbacks of the two ways to add a page: they open system screens, which is the route's business. */
class AddPhotoActions(val onCamera: () -> Unit, val onGallery: () -> Unit)

/**
 * «Ноты» of a piece (spec 3.36.4): the title with «4 стр.» on the right, the strip of numbered pages that scrolls sideways and a «+»
 * with the menu «Сфотографировать» / «Из галереи»; a tap on a page opens the stand there. Without pages — two tiles side by side,
 * «Сфотографировать» and «Из галереи», and no count ([emptyTilesFill]: in landscape they share the column). [adding] false while a
 * take is recorded: the camera and the gallery would end it, so the ways to add a page sleep; the pages still open the stand, which
 * keeps the take. [titled] false — the left column of landscape, where the strip stands under the status without a title.
 */
@Composable
internal fun SheetsBlock(
    state: PieceState,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    metrics: SheetMetrics,
    adding: Boolean,
    side: Dp,
    titled: Boolean = true,
    emptyTilesFill: Boolean = false,
) {
    val empty = state.pages.isEmpty() && state.importing == 0
    Column {
        if (titled) {
            BlockTitle(
                stringResource(Res.string.piece_sheets_none),
                Modifier.padding(horizontal = side),
                end = if (state.pages.isEmpty()) null else stringResource(Res.string.piece_sheets_pages, state.pages.size),
            )
        }
        if (empty) {
            EmptySheets(addPhoto, adding, metrics, fill = emptyTilesFill, modifier = Modifier.padding(horizontal = side))
        } else {
            PageStrip(state, onIntent, addPhoto, metrics, adding, side, firstPage = 0)
        }
    }
}

/** The strip of pages; [firstPage] — the index on the stand of the first photo: 1 for a scale, whose drawn notes are page one. */
@Composable
private fun PageStrip(state: PieceState, onIntent: (PieceIntent) -> Unit, addPhoto: AddPhotoActions, metrics: SheetMetrics, adding: Boolean, side: Dp, firstPage: Int) {
    LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
        itemsIndexed(state.pages, key = { _, tile -> tile.pageId }) { index, tile ->
            PageTile(tile, first = index == 0 && firstPage == 0, metrics = metrics) { onIntent(PieceIntent.PageClicked(index + firstPage)) }
        }
        items(state.importing, key = { "importing-$it" }) { ImportingTile(metrics) }
        item(key = "add") { AddTile(metrics, addPhoto, Modifier.dimmedWhen(!adding)) }
    }
}

@Composable
private fun PageTile(tile: SheetTile, first: Boolean, metrics: SheetMetrics, onClick: () -> Unit) {
    val description = stringResource(Res.string.piece_sheet_page, tile.number)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(TileCorner))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        SheetThumb(tile.thumbPath, metrics.tileWidth, metrics.tileHeight, TileCorner, dim = if (first) THUMB_DIM_FIRST else THUMB_DIM)
        // over a photo — dense glass (spec 3.36.1, 5.29 R4)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(NumberInset)
                .height(NumberPlate)
                .widthIn(min = NumberPlate)
                .background(ViolinTheme.glassStrong, RoundedCornerShape(NumberCorner))
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tile.number.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/** A photo on its way in holds its place in the strip from the moment it was picked (handoff 13g). */
@Composable
private fun ImportingTile(metrics: SheetMetrics) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible, enter = expandHorizontally(tween(TILE_APPEAR_MS)) + fadeIn(tween(TILE_APPEAR_MS))) {
        Box(
            modifier = Modifier
                .size(metrics.tileWidth, metrics.tileHeight)
                .clip(RoundedCornerShape(TileCorner))
                .background(ViolinTheme.repertoireColors.paper.copy(alpha = PAPER_WHILE_COPIED)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(ImportBar),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

/** «+» at the end of the strip: a dashed tile, the menu of the two ways. */
@Composable
private fun AddTile(metrics: SheetMetrics, addPhoto: AddPhotoActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val said = stringResource(Res.string.piece_sheets_add)
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(metrics.tileWidth, metrics.tileHeight)
                .clip(RoundedCornerShape(TileCorner))
                .dashedBorder(colors.outlineVariant, TileCorner)
                .clickable(role = Role.Button) { menuOpen = true }
                .semantics { contentDescription = said },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Plus, contentDescription = null, tint = colors.onSurfaceVariant, size = PlusSize)
        }
        PhotoMenu(menuOpen, addPhoto) { menuOpen = false }
    }
}

@Composable
private fun PhotoMenu(open: Boolean, addPhoto: AddPhotoActions, onDismiss: () -> Unit) {
    AppMenu(expanded = open, onDismissRequest = onDismiss) {
        AppMenuItem(stringResource(Res.string.piece_sheets_camera), icon = AppIcons.Camera, onClick = { onDismiss(); addPhoto.onCamera() })
        AppMenuItem(stringResource(Res.string.piece_sheets_gallery), icon = AppIcons.Gallery, onClick = { onDismiss(); addPhoto.onGallery() })
    }
}

/**
 * No pages yet: the two ways as two tiles — one first step instead of a card with two buttons (spec 3.36.4). A word of their names
 * never breaks by the letter: upright the tiles are 150 wide while both names keep their widest word in it, else they widen to share
 * the row — equally, or by their words; lying ([fill]) they share the column. Where even that is too narrow the names step down
 * together, 13 sp to 11.5 ([EmptySheetsFit], as the steps of the status): «Сфотографировать» of 13 sp is 129 dp, a half of the left
 * column lying leaves it 114.
 */
@Composable
private fun EmptySheets(addPhoto: AddPhotoActions, adding: Boolean, metrics: SheetMetrics, fill: Boolean, modifier: Modifier) {
    val names = listOf(stringResource(Res.string.piece_sheets_camera), stringResource(Res.string.piece_sheets_gallery))
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = EMPTY_NAME_SP.sp, lineHeight = (EMPTY_NAME_SP * EMPTY_NAME_LINE).sp, fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.dimmedWhen(!adding).fillMaxWidth()) {
        val row = constraints.maxWidth
        val plan = remember(names, style, measurer, density, row, fill) {
            with(density) {
                val gap = TileGap.toPx()
                val around = List(names.size) { (EmptyTileSide * 2).toPx() }
                val labelsAt = { sizeSp: Float ->
                    val sized = style.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * EMPTY_NAME_LINE).sp)
                    names.map { name ->
                        SegmentFit.Label(
                            line = measurer.measure(name, sized, softWrap = false, maxLines = 1).size.width.toFloat(),
                            word = name.split(' ', '\n', '\t').filter { it.isNotEmpty() }
                                .maxOfOrNull { measurer.measure(it, sized, softWrap = false, maxLines = 1).size.width.toFloat() } ?: 0f,
                        )
                    }
                }
                EmptySheetsFit.plan(row.toFloat(), tile = if (fill) null else EmptyTileWidth.toPx(), gap, around, slack = 1.dp.toPx(), labelsAt = labelsAt)
            }
        }
        val sized = if (plan.sizeSp == EMPTY_NAME_SP) style else style.copy(fontSize = plan.sizeSp.sp, lineHeight = (plan.sizeSp * EMPTY_NAME_LINE).sp)
        val widths = with(density) { plan.widths.map { it.toDp() } }
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            EmptyTile(AppIcons.Camera, names[0], metrics.tileHeight, Modifier.width(widths[0]), sized, addPhoto.onCamera)
            EmptyTile(AppIcons.Gallery, names[1], metrics.tileHeight, Modifier.width(widths[1]), sized, addPhoto.onGallery)
        }
    }
}

@Composable
private fun EmptyTile(icon: ImageVector, text: String, height: Dp, modifier: Modifier, style: TextStyle, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(EmptyTileCorner))
            .dashedBorder(colors.outlineVariant, EmptyTileCorner)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = EmptyTileSide, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant, size = EmptyIcon)
        Text(text, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = style)
    }
}

private const val WHOLE_SYSTEMS = 3
private const val FOLDED_SYSTEMS = 2
private const val FOLD_MS = 250

/**
 * «Ноты» of a scale (spec 3.22, 3.36.4): the title with its range on the right — «G3 – G6», why the octaves are three — the notes
 * the app draws, and under them the strip of its photos, or the quiet line «Добавить фото нот» with «например, аппликатура из
 * сборника» and the menu of the «+». [titled] false — the right column of landscape, where the left one names them.
 */
@Composable
internal fun ScaleNotesBlock(
    scale: Scale,
    state: PieceState,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    adding: Boolean,
    side: Dp,
    landscape: Boolean,
    titled: Boolean = true,
) {
    Column {
        if (titled) BlockTitle(stringResource(Res.string.scale_notes), Modifier.padding(horizontal = side), end = scaleRange(scale))
        DrawnScale(scale, landscape, Modifier.padding(horizontal = side)) { onIntent(PieceIntent.PageClicked(0)) }
        if (state.pages.isEmpty() && state.importing == 0) {
            var menuOpen by remember { mutableStateOf(false) }
            Box(Modifier.padding(start = side, end = side, top = 10.dp).dimmedWhen(!adding)) {
                QuietAddRow(
                    icon = AppIcons.Camera,
                    text = stringResource(Res.string.scale_add_photo),
                    caption = stringResource(Res.string.scale_add_photo_hint),
                    onClick = { menuOpen = true },
                    bottomLine = true,
                )
                PhotoMenu(menuOpen, addPhoto) { menuOpen = false }
            }
        } else {
            Box(Modifier.padding(top = 10.dp)) {
                PageStrip(state, onIntent, addPhoto, if (landscape) SheetMetrics.Landscape else SheetMetrics.Portrait, adding, side, firstPage = 1)
            }
        }
    }
}

/** «Ноты · G3 – G6» in the left column of a scale lying (spec 3.36.4): its notes are drawn in the right one; this opens the stand. */
@Composable
internal fun ScaleNotesRow(scale: Scale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val words = stringResource(Res.string.scale_notes) + stringResource(Res.string.dot_separator) + scaleRange(scale)
    val open = stringResource(Res.string.scale_open_stand)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = open, onClick = onClick)
            .clearAndSetSemantics { contentDescription = words }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(AppIcons.Scale, contentDescription = null, tint = colors.primary)
        Text(
            words,
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
        AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary)
    }
}

/**
 * The notes of a scale on its screen (spec 3.22, handoff 24f): light ink on a dark card — paper here would be the one white
 * rectangle of a dark screen; paper is for the stand. Up to three systems stand whole, a longer scale shows two and «ещё N систем»,
 * which unfolds it in place. A tap opens the stand.
 */
@Composable
private fun DrawnScale(scale: Scale, landscape: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val space = if (landscape) NotationSizes.CardLandscape else NotationSizes.Card
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.scale_open_stand), onClick = onClick)
            .animateContentSize(tween(FOLD_MS)),
    ) {
        val inner = maxWidth - 24.dp
        val systems = systemCount(scale, inner.value, space)
        val folded = systems > WHOLE_SYSTEMS && !expanded
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScaleNotation(
                scale, space, ViolinTheme.exerciseColors.inkOnDark,
                maxSystems = FOLDED_SYSTEMS.takeIf { folded }, name = scaleTitle(scale.spec),
            )
            if (folded) {
                val more = systems - FOLDED_SYSTEMS
                AppButton(
                    text = stringResource(Formats.plural(more, Res.string.scale_more_systems_one, Res.string.scale_more_systems_few, Res.string.scale_more_systems_many), more),
                    onClick = { expanded = true },
                    style = AppButtonStyle.Text,
                )
            }
        }
    }
}
