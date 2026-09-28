package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.feature.practice.SheetFace
import com.violinjourney.app.feature.practice.SheetFaces
import kotlinx.datetime.TimeZone

/**
 * The one frame of the sheets of «Занятия» (spec 3.36.3): its face follows the state ([SheetFaces.of]) — one sheet in the place of
 * another changes what the frame shows, without sliding away and without a blink of the scrim; «назад» on «Время за день» gives the
 * sheet of the day back in place. The frame slides away when the last sheet closes; when something comes in its place — a sheet not in
 * the frame yet («Профиль», «Трофеи», the recap, until stage 107) or the gift — it goes at once. Never higher than the window under the
 * status bar and 16 more.
 */
@Composable
fun PracticeSheetHost(state: PracticeState, onIntent: (PracticeIntent) -> Unit, zone: TimeZone, photo: ImageBitmap?) {
    val face = SheetFaces.of(state)
    val current = rememberUpdatedState(face)
    // one sheet of the model not in the frame, or the gift, takes the place at once; stepped aside for a record, the sheet slides
    val shownSheet = state.sheet.takeUnless { state.sheetsAway }
    AppSheet(
        value = face,
        onHide = { current.value?.let { onIntent(SheetFaces.hideIntent(it)) } },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        slideAway = shownSheet == null && state.gift == null,
        onBack = face?.let(SheetFaces::backIntent)?.let { intent -> { onIntent(intent) } },
        bottom = { shown ->
            when (shown) {
                is SheetFace.Summary -> {
                    {
                        SummaryButtons(
                            sheet = shown.sheet,
                            onSave = { onIntent(PracticeIntent.SummarySaved) },
                            onDiscard = { onIntent(PracticeIntent.SummaryDiscarded) },
                        )
                    }
                }
                is SheetFace.EditTime -> {
                    { EditTimeButtons(shown.sheet, onIntent) }
                }
                is SheetFace.Day, is SheetFace.Path -> null
            }
        },
    ) { shown ->
        when (shown) {
            is SheetFace.Summary -> SummarySheetContent(shown.sheet, state.stepMinutes, zone, onStep = { onIntent(PracticeIntent.SummaryStepped(it)) })
            is SheetFace.Day -> DaySheetContent(shown.day, onIntent, zone)
            is SheetFace.EditTime -> EditTimeSheetContent(shown.sheet, state.stepMinutes, onIntent)
            is SheetFace.Path -> PathSheetContent(shown.header, photo, onIntent)
        }
    }
}
