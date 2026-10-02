package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.SelectionBar
import com.violinjourney.app.feature.history.components.SelectionBarHeight
import com.violinjourney.app.feature.history.components.SelectionDeleteDialog
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_block_title
import com.violinjourney.app.shared.resources.piece_field_notes
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

// The screen of an element (spec 3.36.4, 5.29 R4; repertoire.html 3, landscape.html 2).
private val MaxContentWidth = 560.dp
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val TitleAppearsAfter = 80.dp
private val LandscapeLeftColumn = 300.dp
private val ScrollEndGap = 24.dp
private val LandscapeEndGap = 16.dp
private val LandscapeBlockGap = 12.dp
private val LandscapeMetaTop = 2.dp
private val LandscapeStatusTop = 10.dp
private val LandscapeStripTop = 12.dp
private val LandscapeRightTop = 8.dp

/** Lower than this, the line «И. С. Бах · …» of the left column lying goes first: the form has it (spec 3.36.4, 5.29 R4). */
private val MetaFromWindowHeight = 380.dp

/** Lying, the notes beside the backing fold after two lines (spec 3.36.4). */
private const val LANDSCAPE_NOTES_LINES = 2

/**
 * One element of the repertoire (spec 3.15, 3.22, 3.36.4): upright — its name, the line of what is set and the status switch, then
 * the music, the backing, the notes and «Дубли», and at the bottom, pinned, the zone of recording ([RecordDock]); lying — the name in
 * the bar, the line, the status and the strip on the left over the zone, and the rest scrolling on the right. Stateless. [take]
 * comes apart from [state] because it changes twenty times a second while a take is recorded: it is handed down as a state and read
 * by the bar of the zone, so the rest of the screen hears only that a take began or ended. [backing] null — it is still being read:
 * until both are, the screen is its bar and an empty zone of the height of its buttons, so neither the key nor the switch blinks.
 */
@Composable
fun PieceScreen(
    state: PieceState,
    take: State<TakeState>,
    onIntent: (PieceIntent) -> Unit,
    addPhoto: AddPhotoActions,
    modifier: Modifier = Modifier,
    // asked once: a new zone on every recomposition is a new object, and every take card would recompose with it
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    takeActions: CardActions? = null,
    videoImport: MediaImport = MediaImport.Idle,
    onPickVideo: () -> Unit = {},
    backing: BackingUi? = null,
) {
    val colors = MaterialTheme.colorScheme
    val recording by remember(take) { derivedStateOf { take.value.recording } }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        val landscape = maxWidth > maxHeight
        val header = state.header
        if (header == null || backing == null) {
            Loading(landscape, onIntent)
        } else {
            // «Выучено» for a scale, an étude, a stroke; «В репертуаре» for a piece (spec 3.22)
            CompositionLocalProvider(LocalExerciseWords provides state.exercise) {
                val screen = ElementScreen(state, take, recording, header, backing, onIntent, addPhoto, zone, takeActions, videoImport, onPickVideo)
                if (landscape) LandscapeLayout(screen) else PortraitLayout(screen)
            }
        }
    }
    MediaImportSheet(videoImport, ImportWords.VIDEO_TAKE) { action ->
        onIntent(
            when (action) {
                ImportAction.Cancel -> PieceIntent.VideoImportCancelClicked
                ImportAction.Continue -> PieceIntent.VideoImportContinueClicked
                ImportAction.Dismiss -> PieceIntent.VideoImportDismissed
                ImportAction.Send -> PieceIntent.VideoImportSendClicked
            },
        )
    }
}

/** What both layouts are made of — one bag, not eleven parameters twice. */
private class ElementScreen(
    val state: PieceState,
    val take: State<TakeState>,
    val recording: Boolean,
    val header: PieceHeader,
    val backing: BackingUi,
    val onIntent: (PieceIntent) -> Unit,
    val addPhoto: AddPhotoActions,
    val zone: TimeZone,
    val takeActions: CardActions?,
    val videoImport: MediaImport,
    val onPickVideo: () -> Unit,
) {
    val selecting: Boolean get() = state.selection.active
}

/** Until the element and its backing are read: the bar, and an empty zone as high as its buttons (spec 3.36.4). */
@Composable
private fun Loading(landscape: Boolean, onIntent: (PieceIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PieceTopBar(title = "", titleVisible = false, height = if (landscape) TopBarHeightLandscape else TopBarHeight, onIntent = onIntent, loaded = false)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AppDock(
                dock = { Spacer(Modifier.height(buttonHeight)) },
                modifier = if (landscape) Modifier.fillMaxHeight().width(LandscapeLeftColumn).align(Alignment.TopStart) else Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                metrics = currentDockMetrics().copy(side = ElementSide),
            ) { Box(Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun PortraitLayout(screen: ElementScreen) {
    val scroll = rememberScrollState()
    val state = screen.state
    Column(Modifier.fillMaxSize()) {
        Bars(state, SelectionBarHeight.Portrait, screen.onIntent) {
            PieceTopBar(screen.header.title, titleVisible = scroll.isPast(TitleAppearsAfter), height = TopBarHeight, onIntent = screen.onIntent, editable = !screen.recording)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AppDock(
                dock = { RecordDock(screen.take, screen.backing, screen.videoImport, screen.selecting, screen.onIntent, screen.onPickVideo) },
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
                metrics = currentDockMetrics().copy(side = ElementSide),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    // While takes are being picked everything that is not the list steps aside (spec 3.18, handoff 19f1).
                    Column(Modifier.dimmedWhen(screen.selecting)) {
                        HeaderBlock(screen.header, state.scale, screen.onIntent, Modifier.padding(horizontal = ElementSide))
                        val scale = state.scale
                        if (scale != null) {
                            ScaleNotesBlock(scale, state, screen.onIntent, screen.addPhoto, adding = !screen.recording, side = ElementSide, landscape = false)
                        } else {
                            SheetsBlock(state, screen.onIntent, screen.addPhoto, SheetMetrics.Portrait, adding = !screen.recording, side = ElementSide)
                        }
                        BackingAndNotes(screen, Modifier.padding(horizontal = ElementSide))
                    }
                    TakesSection(
                        state.takes, state.progress, screen.zone, screen.onIntent, screen.recording, Modifier.padding(horizontal = ElementSide),
                        actions = screen.takeActions, selection = state.selection,
                    )
                    Spacer(Modifier.height(LocalDockInset.current + ScrollEndGap))
                }
            }
        }
    }
}

/**
 * «Минусовка» and «Заметки» upright, in this order (spec 3.36.4): each a card under its title, or — not there yet — a quiet row;
 * two quiet rows stand together, one line between them. While a take runs the card of the backing and the quiet rows sleep; the
 * notes and their «ещё» do not.
 */
@Composable
private fun BackingAndNotes(screen: ElementScreen, modifier: Modifier) {
    val backing = screen.backing
    val onIntent = screen.onIntent
    val backingQuiet = backing.title == null
    val notesQuiet = screen.state.notes.isEmpty()
    Column(modifier) {
        if (backingQuiet) {
            BackingAddRow(backing, onIntent, bottomLine = !notesQuiet, modifier = Modifier.padding(top = QuietGroupTop).dimmedWhen(screen.recording))
        } else {
            BlockTitle(stringResource(Res.string.backing_block_title))
            BackingCard(backing, onIntent, Modifier.dimmedWhen(screen.recording))
        }
        backing.problem?.let { BackingProblemLine(it, onIntent) }
        if (notesQuiet) {
            NotesAddRow(onIntent, bottomLine = true, modifier = Modifier.padding(top = if (backingQuiet) 0.dp else QuietGroupTop).dimmedWhen(screen.recording))
        } else {
            BlockTitle(stringResource(Res.string.piece_field_notes))
            NotesCard(screen.state.notes, screen.state.notesCollapsedLines)
        }
    }
}

/**
 * Lying (spec 3.36.4, landscape.html 2): the name in the bar of 48; on the left, 300 wide, the line of what is set (gone in a window
 * lower than 380 dp), the status and the strip — or, of a scale, «Ноты · G3 – G6» — over the zone of recording, as wide as the column
 * and faded only over it; on the right, scrolling to the bottom of the screen, the drawn notes of a scale, the backing and the notes
 * side by side 4 : 3 (each whole-width when alone), the quiet rows, and «Дубли».
 */
@Composable
private fun LandscapeLayout(screen: ElementScreen) {
    val state = screen.state
    val scale = state.scale
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val withMeta = windowHeight >= MetaFromWindowHeight
    Column(Modifier.fillMaxSize()) {
        Bars(state, SelectionBarHeight.Landscape, screen.onIntent) {
            PieceTopBar(screen.header.title, titleVisible = true, height = TopBarHeightLandscape, onIntent = screen.onIntent, editable = !screen.recording)
        }
        Row(Modifier.fillMaxSize()) {
            AppDock(
                dock = { RecordDock(screen.take, screen.backing, screen.videoImport, screen.selecting, screen.onIntent, screen.onPickVideo) },
                modifier = Modifier.width(LandscapeLeftColumn).fillMaxHeight(),
                metrics = currentDockMetrics().copy(side = ElementSide),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).dimmedWhen(screen.selecting)) {
                    if (withMeta) PieceMetaLine(screen.header, scale, Modifier.padding(start = ElementSide, end = ElementSide, top = LandscapeMetaTop))
                    StatusSwitch(
                        screen.header.status, screen.onIntent,
                        Modifier.padding(start = ElementSide, end = ElementSide, top = if (withMeta) LandscapeStatusTop else LandscapeMetaTop),
                        byWords = true,
                    )
                    Spacer(Modifier.height(LandscapeStripTop))
                    if (scale != null) {
                        ScaleNotesRow(scale, onClick = { screen.onIntent(PieceIntent.PageClicked(0)) }, modifier = Modifier.padding(horizontal = ElementSide))
                    } else {
                        SheetsBlock(
                            state, screen.onIntent, screen.addPhoto, SheetMetrics.Landscape, adding = !screen.recording, side = ElementSide,
                            titled = false, emptyTilesFill = true,
                        )
                    }
                    Spacer(Modifier.height(LocalDockInset.current + LandscapeEndGap))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = ElementSide, top = LandscapeRightTop, bottom = LandscapeEndGap),
            ) {
                Column(Modifier.dimmedWhen(screen.selecting), verticalArrangement = Arrangement.spacedBy(LandscapeBlockGap)) {
                    if (scale != null) {
                        ScaleNotesBlock(scale, state, screen.onIntent, screen.addPhoto, adding = !screen.recording, side = 0.dp, landscape = true, titled = false)
                    }
                    LandscapeBackingAndNotes(screen)
                }
                TakesSection(
                    state.takes, state.progress, screen.zone, screen.onIntent, screen.recording,
                    actions = screen.takeActions, selection = state.selection,
                )
            }
        }
    }
}

/**
 * The backing and the notes lying: beside each other 4 : 3 when both are there and the column of the name keeps its widest word
 * ([BackingFit]) — the name of the backing whole, the notes two lines; otherwise, as when one of them is alone, each as wide as the
 * right column, the backing first (640 × 360).
 */
@Composable
private fun LandscapeBackingAndNotes(screen: ElementScreen) {
    val backing = screen.backing
    val onIntent = screen.onIntent
    val card = backing.title != null
    val notes = screen.state.notes
    Column {
        if (card && notes.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widest = rememberBackingWidestWord(backing)
                if (BackingFit.besideNotes(maxWidth, widest)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BackingFit.BesideGap)) {
                        BackingCard(backing, onIntent, Modifier.weight(BackingFit.BACKING_SHARE).fillMaxHeight().dimmedWhen(screen.recording), landscape = true)
                        NotesCard(notes, LANDSCAPE_NOTES_LINES, Modifier.weight(BackingFit.NOTES_SHARE).fillMaxHeight(), label = true)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(LandscapeBlockGap)) {
                        BackingCard(backing, onIntent, Modifier.dimmedWhen(screen.recording), landscape = true)
                        NotesCard(notes, LANDSCAPE_NOTES_LINES, label = true)
                    }
                }
            }
        } else if (card) {
            BackingCard(backing, onIntent, Modifier.dimmedWhen(screen.recording), landscape = true)
        } else if (notes.isNotEmpty()) {
            NotesCard(notes, LANDSCAPE_NOTES_LINES, label = true)
        }
        backing.problem?.let { BackingProblemLine(it, onIntent) }
        if (!card || notes.isEmpty()) {
            Column(Modifier.padding(top = if (card || notes.isNotEmpty()) LandscapeBlockGap else 0.dp).dimmedWhen(screen.recording)) {
                if (!card) BackingAddRow(backing, onIntent, bottomLine = notes.isNotEmpty())
                if (notes.isEmpty()) NotesAddRow(onIntent, bottomLine = true)
            }
        }
    }
}

/** The widest word of the name of the backing and of its second line lying («минусовка 3:40»), each in its style, in dp. */
@Composable
private fun rememberBackingWidestWord(backing: BackingUi): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val name = backing.title.orEmpty()
    val length = backingLengthLine(backing, landscape = true)
    val nameStyle = backingNameStyle()
    val lengthStyle = backingLengthStyle()
    return remember(name, length, nameStyle, lengthStyle, measurer, density) {
        fun widest(text: String, style: TextStyle): Int = text.split(' ', '\n', '\t').filter { it.isNotEmpty() }
            .maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
        with(density) { maxOf(widest(name, nameStyle), widest(length, lengthStyle)).toDp() }
    }
}

/**
 * The top of the screen: its own bar, or — while takes are being picked — the selection bar in
 * its place (spec 3.18). Below them, once, the question before the takes go — the picked ones, or
 * the one of «Удалить…».
 */
@Composable
private fun Bars(state: PieceState, selectionHeight: Dp, onIntent: (PieceIntent) -> Unit, topBar: @Composable () -> Unit) {
    val selection = state.selection
    Crossfade(targetState = selection.active, animationSpec = tween(BAR_FADE_MS), label = "pieceBars") { selecting ->
        if (selecting) {
            SelectionBar(selection, state.allSelected, onIntent = { onIntent(PieceIntent.Select(it)) }, height = selectionHeight)
        } else {
            topBar()
        }
    }
    // «Удалить 2 дубля?» of the picked ones, or «Удалить запись?» of one take's «⋯» (spec 3.36.5)
    SelectionDeleteDialog(selection, state.takes.map { it.card }, takes = true, onIntent = { onIntent(PieceIntent.Select(it)) })
}

private const val BAR_FADE_MS = 200

/** Derived: the scroll offset changes on every frame of a fling, "past the title or not" changes twice. */
@Composable
private fun ScrollState.isPast(distance: Dp): Boolean {
    val threshold = with(LocalDensity.current) { distance.toPx() }
    val past by remember(this, threshold) { derivedStateOf { value > threshold } }
    return past
}
