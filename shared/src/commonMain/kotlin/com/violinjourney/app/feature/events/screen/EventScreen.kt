package com.violinjourney.app.feature.events.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.BarMoreButton
import com.violinjourney.app.core.ui.components.DeleteDialog
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.ElementTopBar
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.MenuDanger
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.repertoire.components.RecordingBar
import com.violinjourney.app.feature.repertoire.piece.ImportWords
import com.violinjourney.app.feature.repertoire.piece.MediaImportSheet
import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.piece.TakeState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.event_add_record
import com.violinjourney.app.shared.resources.event_delete_text_played
import com.violinjourney.app.shared.resources.event_delete_text_program
import com.violinjourney.app.shared.resources.event_not_found
import com.violinjourney.app.shared.resources.event_recording_description
import com.violinjourney.app.shared.resources.piece_delete_title
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * The screen of an event (spec 3.35, 3.36.9; 5.29 R9): above the tabs, without the bottom bar. Upright — the bar with «назад», «Изменить»
 * and «⋯», the head, the parts in the order of the kind and the time, and at the bottom, pinned, «Добавить запись» of a performance from
 * its day on, or the bar of a recording that runs, of any kind. Lying — the bar of 48 across the window; at the left, min(372, 45 % of the
 * window), what is read — the head and the notes; at the right, beyond a rule, the rest in the same order, the bottom zone as wide as that
 * column and under the records it adds to (the exception of 3.36.1 rule 2). Each column scrolls by itself. Stateless; [take] comes apart
 * from [state] because it changes twenty times a second while a sound is recorded: only the bar of the recording reads it whole.
 * «Изменить», «Добавить заметку» and «Заметку» of «Можно добавить» open the form of the event.
 */
@Composable
fun EventScreen(
    state: EventState,
    take: State<TakeState>,
    onIntent: (EventIntent) -> Unit,
    modifier: Modifier = Modifier,
    // asked once: a new zone on every recomposition is a new object, and every card of a record would recompose with it
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    import: EventImport = EventImport(MediaImport.Idle, ImportWords.VIDEO_RECORD),
) {
    val colors = MaterialTheme.colorScheme
    val recording by remember(take) { derivedStateOf { take.value.recording } }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface)) {
        val landscape = maxWidth > maxHeight
        val barHeight = if (landscape) EventsDimens.BarHeightLying else EventsDimens.BarHeight
        when (state) {
            EventState.Loading -> Column(Modifier.fillMaxSize()) {
                ElementTopBar(title = "", titleVisible = false, height = barHeight, onBack = { onIntent(EventIntent.BackClicked) }, onEdit = null, loaded = false)
            }
            EventState.NotFound -> Column(Modifier.fillMaxSize()) {
                ElementTopBar(title = "", titleVisible = false, height = barHeight, onBack = { onIntent(EventIntent.BackClicked) }, onEdit = null, loaded = false)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(Res.string.event_not_found), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
            is EventState.Loaded -> {
                val blocks = EventBlockScope(state, onIntent, recording, zone)
                if (landscape) LandscapeLayout(blocks, take, maxWidth) else PortraitLayout(blocks, take)
                EventSheetHost(state, onIntent)
                if (state.dialog == EventDialog.DeleteOne) {
                    DeleteDialog(
                        title = stringResource(Res.string.piece_delete_title, eventNameOf(state.header.name)),
                        text = stringResource(if (state.performance) Res.string.event_delete_text_program else Res.string.event_delete_text_played),
                        onConfirm = { onIntent(EventIntent.DeleteConfirmed(scope = null)) },
                        onDismiss = { onIntent(EventIntent.DeleteDismissed) },
                    )
                }
            }
        }
    }
    MediaImportSheet(import.import, import.words) { onIntent(EventIntent.Import(it)) }
}

@Composable
private fun PortraitLayout(blocks: EventBlockScope, take: State<TakeState>) {
    val scroll = rememberScrollState()
    val state = blocks.state
    Column(Modifier.fillMaxSize()) {
        TopBar(blocks, titleVisible = scroll.isPast(EventsDimens.TitleAppearsAfter), height = EventsDimens.BarHeight)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AppDock(
                dock = { EventDock(blocks, take) },
                modifier = Modifier.widthIn(max = EventsDimens.ColumnMax).fillMaxSize(),
                metrics = currentDockMetrics().copy(side = EventsDimens.ScreenSide),
                pinned = state.pinnedAddRecord || blocks.recording,
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = EventsDimens.ScreenSide)) {
                    EventHead(state.header)
                    with(blocks) { Sections(state.order) }
                    Spacer(Modifier.height(LocalDockInset.current + EventsDimens.ScrollEndGap))
                }
            }
        }
    }
}

/**
 * Lying (spec 3.36.9, events-views.html 6): the bar across the window; the left column — min(372, 45 % of the window): 372 at 892 × 412,
 * 288 at 640 × 360 — holds what is read, the right one the rest and the bottom zone. The name stands large at the top of the left column,
 * so the bar is without it until that column has scrolled it away, as upright (review of stage 98a: it stood there twice).
 */
@Composable
private fun LandscapeLayout(blocks: EventBlockScope, take: State<TakeState>, windowWidth: Dp) {
    val state = blocks.state
    val left = EventLayout.leftColumn(windowWidth)
    val leftScroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        TopBar(blocks, titleVisible = leftScroll.isPast(EventsDimens.TitleAppearsAfter), height = EventsDimens.BarHeightLying)
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(left)
                    .fillMaxHeight()
                    .verticalScroll(leftScroll)
                    .padding(start = EventsDimens.ScreenSide, end = EventsDimens.ScreenSide, bottom = EventsDimens.ScrollEndGap),
            ) {
                EventHead(state.header, lying = true)
                with(blocks) { Sections(state.order.filter { it == EventSection.NOTES }) }
            }
            Box(Modifier.width(EventsDimens.Rule).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            AppDock(
                dock = { EventDock(blocks, take) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                metrics = currentDockMetrics().copy(side = EventsDimens.ScreenSide),
                pinned = state.pinnedAddRecord || blocks.recording,
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = EventsDimens.ScreenSide)) {
                    with(blocks) { Sections(state.order.filter { it != EventSection.NOTES }) }
                    Spacer(Modifier.height(LocalDockInset.current + EventsDimens.ScrollEndGap))
                }
            }
        }
    }
}

/** The arithmetic of the layout lying (5.29 R9): pure, with a test. */
object EventLayout {
    /** The left column: 372, but no wider than 45 % of the window — 288 at 640 × 360, 271 in the window of 603 × 308. */
    fun leftColumn(windowWidth: Dp): Dp = minOf(EventsDimens.LeftColumnMax, windowWidth * EventsDimens.LEFT_COLUMN_SHARE)
}

/**
 * The bar of the event ([ElementTopBar]): «назад», the name once the large one has scrolled away, «Изменить» — the form of the event — and
 * «⋯» with «Удалить…» coral with the bin — the one item of its menu. «Изменить» and «⋯» sleep while a recording runs.
 */
@Composable
private fun TopBar(blocks: EventBlockScope, titleVisible: Boolean, height: Dp) {
    val onIntent = blocks.onIntent
    var menu by remember { mutableStateOf(false) }
    ElementTopBar(
        title = eventNameOf(blocks.state.header.name),
        titleVisible = titleVisible,
        height = height,
        onBack = { onIntent(EventIntent.BackClicked) },
        onEdit = { onIntent(EventIntent.EditClicked) },
        editable = !blocks.recording,
        more = {
            Box {
                BarMoreButton(stringResource(Res.string.card_menu), onClick = { menu = true }, enabled = !blocks.recording)
                AppMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    danger = MenuDanger(stringResource(Res.string.card_menu_delete), onClick = { menu = false; onIntent(EventIntent.DeleteClicked) }, divider = false),
                ) {}
            }
        },
    )
}

/**
 * The bottom zone of the screen of an event (spec 3.36.9): while a sound is recorded — the bar of a take of R4 without the backing,
 * «Идёт запись, 1:12» for TalkBack, and «Слишком шумно» under it; otherwise «Добавить запись» with its plus, the main button — asleep
 * with a spinner while a file is on its way in (D37). 48 in a window no higher than 360.
 */
@Composable
private fun DockScope.EventDock(blocks: EventBlockScope, take: State<TakeState>) {
    val compact = compact
    if (blocks.recording) {
        val now = take.value
        RecordingBar(
            elapsedSeconds = now.elapsedSeconds,
            noisy = now.problem == TakeProblem.TOO_NOISY,
            onStop = { blocks.onIntent(EventIntent.RecordStopClicked) },
            levels = now.levels,
            compact = compact,
            spoken = Res.string.event_recording_description,
        )
    } else {
        val busy = blocks.state.busyImport
        AppButton(
            text = stringResource(Res.string.event_add_record),
            onClick = { blocks.onIntent(EventIntent.AddRecordClicked) },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.Plus,
            enabled = !busy,
            compact = compact,
            // in the colour of the words of the button, as the spinner of a backing being prepared
            leading = if (busy) ({ CircularProgressIndicator(Modifier.size(EventsDimens.Spinner), color = LocalContentColor.current, strokeWidth = EventsDimens.SpinnerStroke) }) else null,
        )
    }
}

/** Derived: the scroll offset changes on every frame of a fling, "past the title or not" changes twice. */
@Composable
private fun ScrollState.isPast(distance: Dp): Boolean {
    val threshold = with(LocalDensity.current) { distance.toPx() }
    val past by remember(this, threshold) { derivedStateOf { value > threshold } }
    return past
}
