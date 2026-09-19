package com.example.violintuner.feature.repertoire.piece

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconSizes
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.repertoire.components.SheetThumb
import com.example.violintuner.feature.repertoire.components.StatusChip
import com.example.violintuner.feature.repertoire.components.THUMB_DIM
import com.example.violintuner.feature.repertoire.components.THUMB_DIM_FIRST
import com.example.violintuner.feature.repertoire.components.dashedBorder
import com.example.violintuner.feature.repertoire.components.statusLabel
import com.example.violintuner.feature.repertoire.keyAndTempo
import java.time.ZoneId

private val ScreenPadding = 16.dp
private val MaxContentWidth = 560.dp
private val BlockGap = 24.dp
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val TopBarButton = 48.dp
private val TitleAppearsAfter = 80.dp
private val LandscapeLeftColumn = 300.dp
private val LandscapeRecordButton = 64.dp
private val CardCorner = 16.dp
private val TileCorner = 8.dp
private val TileGap = 10.dp
private val NumberCapsule = 18.dp
private const val TABULAR_FIGURES = "tnum"
private const val TILE_APPEAR_MS = 200

/** Sizes that differ between the layouts (handoff `sizes`). */
private class Metrics(val titleSize: Int, val tileWidth: Dp, val tileHeight: Dp) {
    companion object {
        val Portrait = Metrics(titleSize = 28, tileWidth = 84.dp, tileHeight = 112.dp)
        val Landscape = Metrics(titleSize = 24, tileWidth = 66.dp, tileHeight = 88.dp)
    }
}

/** Callbacks of the two ways to add a page: they open system screens, which is the route's business. */
class AddPhotoActions(val onCamera: () -> Unit, val onGallery: () -> Unit)

/**
 * One piece of the repertoire (spec 3.15, handoff 13c, 13d, 13h). Stateless. [take] comes apart
 * from [state] because it changes twenty times a second while a take is recorded.
 */
@Composable
fun PieceScreen(
    state: PieceState,
    take: TakeState,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        val header = state.header
        if (header == null) {
            TopBar(title = "", titleVisible = false, height = TopBarHeight, onIntent = onIntent)
        } else if (maxWidth > maxHeight) {
            LandscapeLayout(state, take, header, onIntent, addPhoto, zone)
        } else {
            PortraitLayout(state, take, header, onIntent, addPhoto, zone)
        }
    }
}

@Composable
private fun PortraitLayout(
    state: PieceState,
    take: TakeState,
    header: PieceHeader,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    zone: ZoneId,
) {
    val scroll = rememberScrollState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(header.title, titleVisible = scroll.isPast(TitleAppearsAfter), height = TopBarHeight, onIntent = onIntent)
        Column(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            HeaderBlock(header, state.statusMenuOpen, onIntent, Metrics.Portrait, Modifier.padding(horizontal = ScreenPadding))
            SheetsBlock(state, onIntent, addPhoto, Metrics.Portrait)
            Column(modifier = Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
                // Recording stands above the notes: the notes are read once before playing, a take is recorded every time.
                RecordTakeRow(take, onIntent)
                NotesBlock(state.notes, state.notesCollapsedLines, onIntent)
                state.progress?.let { TakeProgressCard(it) }
                TakesBlock(state.takes, zone, onIntent)
            }
        }
    }
}

/** Handoff 13h: the name and what one does with the piece stay put on the left; the music, the notes and the takes scroll on the right. */
@Composable
private fun LandscapeLayout(
    state: PieceState,
    take: TakeState,
    header: PieceHeader,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    zone: ZoneId,
) {
    Column {
        TopBar(header.title, titleVisible = false, height = TopBarHeightLandscape, onIntent = onIntent)
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(LandscapeLeftColumn)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(ScreenPadding),
            ) {
                HeaderBlock(header, state.statusMenuOpen, onIntent, Metrics.Landscape)
                RecordTakeRow(take, onIntent, buttonSize = LandscapeRecordButton)
                state.progress?.let { TakeProgressCard(it) }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(ScreenPadding),
            ) {
                SheetsBlock(state, onIntent, addPhoto, Metrics.Landscape)
                Column(modifier = Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(ScreenPadding)) {
                    NotesBlock(state.notes, state.notesCollapsedLines, onIntent)
                    TakesBlock(state.takes, zone, onIntent)
                }
            }
        }
    }
}

/** Derived: the scroll offset changes on every frame of a fling, "past the title or not" changes twice. */
@Composable
private fun ScrollState.isPast(distance: Dp): Boolean {
    val threshold = with(LocalDensity.current) { distance.toPx() }
    val past by remember(this, threshold) { derivedStateOf { value > threshold } }
    return past
}

@Composable
private fun TopBar(title: String, titleVisible: Boolean, height: Dp, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = stringResource(R.string.session_back)
        Box(
            modifier = Modifier
                .size(TopBarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onIntent(PieceIntent.BackClicked) }
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface)
        }
        // The large title below says it first; this one takes over once that has scrolled away.
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.animation.AnimatedVisibility(visible = titleVisible, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        if (title.isNotEmpty()) {
            TextButton(onClick = { onIntent(PieceIntent.EditClicked) }) {
                Text(stringResource(R.string.piece_edit), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun HeaderBlock(header: PieceHeader, menuOpen: Boolean, onIntent: (PieceIntent) -> Unit, metrics: Metrics, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = header.title,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = metrics.titleSize.sp, lineHeight = (metrics.titleSize * 1.15f).sp, fontWeight = FontWeight.Bold,
            ),
        )
        if (header.composer.isNotEmpty()) {
            Text(header.composer, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusMenu(header.status, menuOpen, onIntent)
            keyAndTempo(header.keyName, header.tempoBpm)?.let {
                Text(it, color = colors.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES))
            }
        }
    }
}

/** The status chip is also how the status is changed: a small menu of the three (handoff 13c3). */
@Composable
private fun StatusMenu(status: PieceStatus, open: Boolean, onIntent: (PieceIntent) -> Unit) {
    val change = stringResource(R.string.piece_status_change, statusLabel(status))
    Box {
        StatusChip(
            status = status,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { onIntent(PieceIntent.StatusChipClicked) }
                .semantics { contentDescription = change },
            height = 32.dp,
            corner = 8.dp,
            fontSize = 13,
            trailing = { tint -> Chevron(tint) },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { onIntent(PieceIntent.StatusMenuDismissed) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            PieceStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(statusLabel(option), fontWeight = if (option == status) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onIntent(PieceIntent.StatusSelected(option)) },
                )
            }
        }
    }
}

@Composable
private fun Chevron(tint: Color) {
    AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = tint, size = IconSizes.InText)
}

/**
 * The music: a strip of page tiles that scrolls sideways and never grows downwards, be it one
 * page or thirty (spec 3.15). The caption says the count even when three tiles are in sight.
 */
@Composable
private fun SheetsBlock(state: PieceState, onIntent: (PieceIntent) -> Unit, addPhoto: AddPhotoActions, metrics: Metrics) {
    val colors = MaterialTheme.colorScheme
    val empty = state.pages.isEmpty() && state.importing == 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (state.pages.isEmpty()) stringResource(R.string.piece_sheets_none) else stringResource(R.string.piece_sheets_count, state.pages.size),
            modifier = Modifier.padding(horizontal = ScreenPadding),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
        if (empty) {
            Column(
                modifier = Modifier
                    .padding(horizontal = ScreenPadding)
                    .fillMaxWidth()
                    .dashedBorder(colors.outlineVariant, CardCorner)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.piece_sheets_add), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = addPhoto.onCamera) { Text(stringResource(R.string.piece_sheets_camera)) }
                    TextButton(onClick = addPhoto.onGallery) { Text(stringResource(R.string.piece_sheets_gallery)) }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                itemsIndexed(state.pages, key = { _, tile -> tile.pageId }) { index, tile ->
                    PageTile(tile, first = index == 0, metrics = metrics) { onIntent(PieceIntent.PageClicked(index)) }
                }
                items(state.importing, key = { "importing-$it" }) { ImportingTile(metrics) }
                item(key = "add") { AddTile(metrics, addPhoto) }
            }
        }
    }
}

@Composable
private fun PageTile(tile: SheetTile, first: Boolean, metrics: Metrics, onClick: () -> Unit) {
    val description = stringResource(R.string.piece_sheet_page, tile.number)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(TileCorner))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        SheetThumb(tile.thumbPath, metrics.tileWidth, metrics.tileHeight, TileCorner, dim = if (first) THUMB_DIM_FIRST else THUMB_DIM)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .height(NumberCapsule)
                .widthIn(min = NumberCapsule)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(5.dp))
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tile.number.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/** A photo on its way in holds its place in the strip from the moment it was picked (handoff 13g). */
@Composable
private fun ImportingTile(metrics: Metrics) {
    val colors = ViolinTheme.repertoireColors
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible, enter = expandHorizontally(tween(TILE_APPEAR_MS)) + fadeIn(tween(TILE_APPEAR_MS))) {
        Box(
            modifier = Modifier
                .size(metrics.tileWidth, metrics.tileHeight)
                .clip(RoundedCornerShape(TileCorner))
                .background(colors.paper.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun AddTile(metrics: Metrics, addPhoto: AddPhotoActions) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .size(metrics.tileWidth, metrics.tileHeight)
                .clip(RoundedCornerShape(TileCorner))
                .dashedBorder(colors.outlineVariant, TileCorner)
                .clickable(role = Role.Button) { menuOpen = true },
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(AppIcons.Plus, contentDescription = null, tint = colors.primary)
            Text(stringResource(R.string.piece_sheets_add_short), color = colors.primary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surfaceContainerHigh) {
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_sheets_camera)) }, onClick = { menuOpen = false; addPhoto.onCamera() })
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_sheets_gallery)) }, onClick = { menuOpen = false; addPhoto.onGallery() })
        }
    }
}

/** A teacher's pencil marks: meant to be read, so they are text on a card, folded only when long. */
@Composable
private fun NotesBlock(notes: String, collapsedLines: Int, onIntent: (PieceIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (notes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(CardCorner))
                .dashedBorder(colors.outlineVariant, CardCorner)
                .clickable(role = Role.Button) { onIntent(PieceIntent.AddNotesClicked) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.piece_notes_add), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        }
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(CardCorner))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = notes,
            color = colors.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        )
        if (overflows || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.piece_notes_less else R.string.piece_notes_more),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { expanded = !expanded },
                color = colors.primary,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            )
        }
    }
}
