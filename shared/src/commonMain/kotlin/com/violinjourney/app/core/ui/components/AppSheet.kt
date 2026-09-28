package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The frame of a sheet (spec 5.29; components.html, «Лист»).
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val HandleWidth = 36.dp
private val HandleHeight = 5.dp
private val ButtonsTop = 18.dp
private val ButtonsGap = 6.dp

/** What the sheets of the app have in common. */
object AppSheetDefaults {
    /** 20 at the sides, 16 at the bottom — the bottom system inset comes on top of it, from the sheet itself. */
    val ContentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)

    /** A sheet is never wider than this, in landscape too, the recap of a practice as well (spec 3.36.1, rule 4). */
    @OptIn(ExperimentalMaterial3Api::class)
    val MaxWidth = BottomSheetDefaults.SheetMaxWidth
}

/**
 * The frame of every sheet (spec 3.36.1, 5.29): surfaceContainer at a corner of 28 at the top, the handle 36 × 5, the scrim of
 * black at 0.55, never wider than 640 dp — in the middle in landscape. Shown while [value] is not null, and it slides away when the
 * owner drops it, as a swiped one does ([rememberHeldSheet]; [slideAway] false — another sheet takes its place and it goes at once).
 * The [content] gets the value it shows — the last one while it slides away — in a column with [contentPadding] that scrolls when
 * the sheet is higher than the window ([scroll] false for a sheet with a lazy list of its own).
 *
 * [onHide] — the sheet was swiped down, tapped outside or closed with «назад», and it is hidden; nothing more. A swipe does not
 * save, delete or cancel anything (the rule of the owner: hiding a sheet is not a cancel) — only the buttons of the sheet decide.
 * Where the spec gives a swipe a meaning — a sheet with one safe answer, «Спасибо» of the gift, «Продолжаю заниматься» of the
 * forgotten practice (R3) — the owner decides it in [onHide]. Either way the owner must drop [value].
 *
 * [dismissible] false — a swipe, the scrim and «назад» do not close it, and it has no handle to pull (the analysis of a video
 * take, R4; the preparing of «Поделиться», R5): it goes only when the owner drops the value, and then it slides away as any sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> AppSheet(
    value: T?,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    slideAway: Boolean = true,
    dismissible: Boolean = true,
    scroll: Boolean = true,
    contentPadding: PaddingValues = AppSheetDefaults.ContentPadding,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    // A sheet that may not be closed refuses to hide — until its owner lets it go: SheetState.hide() asks the same question, and
    // without the second half the sheet let go would vanish without sliding away. The question is remembered once:
    // rememberModalBottomSheetState keys its saved state on it, and a new lambda would be a new state at every recomposition.
    val canHide = rememberUpdatedState(dismissible || value == null)
    val confirm = remember { { next: SheetValue -> next != SheetValue.Hidden || canHide.value } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirm)
    val shown = rememberHeldSheet(value, sheetState, slideAway) ?: return
    ModalBottomSheet(
        onDismissRequest = onHide,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = AppSheetDefaults.MaxWidth,
        sheetGesturesEnabled = dismissible,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = ViolinTheme.sheetScrim,
        // Material makes the handle a button that closes the sheet: a sheet that cannot be closed has none
        dragHandle = if (dismissible) ({ SheetHandle() }) else null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible, shouldDismissOnClickOutside = dismissible),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding),
        ) {
            if (!dismissible) Spacer(Modifier.height(NoHandleTop))
            content(shown)
        }
    }
}

/** A sheet without a handle starts its content this far from its top edge. */
private val NoHandleTop = 20.dp

/**
 * The handle of a sheet: 36 × 5 in outlineVariant, in a touch area of 49 (22 above and below it, as Material lays it out) and with
 * Material's own words for TalkBack in every language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    BottomSheetDefaults.DragHandle(modifier = modifier, width = HandleWidth, height = HandleHeight, color = MaterialTheme.colorScheme.outlineVariant)
}

/** The frame of [AppSheet] without its window, for a preview (a window does not draw in one), as AppDialogCard is for a dialog. */
@Composable
fun AppSheetCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = AppSheetDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = AppSheetDefaults.MaxWidth)
            .fillMaxWidth()
            .clip(SheetShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/**
 * The buttons at the bottom of a sheet (components.html, «Лист»): the [main] one filled, as wide as the sheet — it says the outcome
 * («Сохранить 35 мин»); under it the refusal said quietly ([quiet]), never a red button. A [main] that cannot be pressed yet is dimmed
 * with its [mainReason] above it. 18 above them, 6 between them.
 */
@Composable
fun AppSheetButtons(
    main: String,
    onMain: () -> Unit,
    modifier: Modifier = Modifier,
    mainIcon: ImageVector? = null,
    mainEnabled: Boolean = true,
    mainReason: String? = null,
    quiet: String? = null,
    onQuiet: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(top = ButtonsTop), verticalArrangement = Arrangement.spacedBy(ButtonsGap)) {
        AppButton(main, onMain, Modifier.fillMaxWidth(), style = AppButtonStyle.Main, icon = mainIcon, enabled = mainEnabled, reason = mainReason)
        if (quiet != null) AppButton(quiet, onQuiet, Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    }
}
