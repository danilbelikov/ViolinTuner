package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.rememberImagePicker
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.feature.practice.SheetFace
import com.violinjourney.app.feature.practice.SheetFaces
import kotlinx.datetime.TimeZone

/**
 * «Занятие не закончено» — the sheet of the app over any screen — is shown (spec 3.36.3): the root that shows it says so here, and the
 * gift of «Занятия» waits under it until it has gone, whatever order the windows are made in.
 */
val LocalAppPromptShown = compositionLocalOf { false }

/**
 * The one frame of the sheets of «Занятия» (spec 3.36.3): its face follows the state ([SheetFaces.of]) — one sheet in the place of
 * another changes what the frame shows, without sliding away and without a blink of the scrim: «Время за день» over the sheet of the
 * day, «Трофеи» and «Имя и фото» over «Мой путь» — «назад» on them gives the parent back in place — the recap in the place of
 * «Закончить занятие», the gift in the place of the recap. The frame slides away when the last face goes. Never higher than the window
 * under the status bar and 16 more. [photo] — the photo of the profile the screen has decoded: «Мой путь» and «Имя и фото» show it.
 *
 * A face that has just come in the place of another does not take the second tap of a double tap on its main button ([AppSheet],
 * [SheetFaces.keyOf]: each gift of a row is a face of its own) — «Готово» of the recap does not accept the gift under the finger, nor
 * «Спасибо» the next gift unseen. A swipe, a tap beside the frame and «назад» mean what the face showed when the frame began to go.
 */
@Composable
fun PracticeSheetHost(state: PracticeState, onIntent: (PracticeIntent) -> Unit, zone: TimeZone, photo: ImageBitmap?) {
    val face = SheetFaces.of(state, appPromptShown = LocalAppPromptShown.current)
    // The system photo picker, out of the sheet's window: no permission is involved, the app gets one picture and no more.
    val pickPhoto = rememberImagePicker { picked -> onIntent(PracticeIntent.ProfilePhotoPicked(picked)) }
    // a phone on its side: the recap goes in two columns with «Готово» at the bottom of the right one, the gift gives up its card
    val low = lowSheetWindow()
    AppSheet(
        value = face,
        // the face the frame showed when it began to go down, not one that came while it slid
        onHide = { hidden -> onIntent(SheetFaces.hideIntent(hidden)) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        scroll = { shown -> !(shown is SheetFace.Recap && low) },
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
                is SheetFace.Profile -> {
                    { NamePhotoButtons(onIntent) }
                }
                // in a low window «Готово» stands at the bottom of the right column, with the columns scrolling over it
                is SheetFace.Recap -> if (low) null else ({ RecapButtons(onDone = { onIntent(PracticeIntent.RecapClosed) }) })
                is SheetFace.Gift -> {
                    { GiftButtons(onThanks = { onIntent(PracticeIntent.GiftAccepted(shown.gift.hours)) }) }
                }
                // a day to come without events (spec 3.36.9): making one is all there is to do — the main button at the bottom
                is SheetFace.Day -> if (shown.day.isFuture && shown.day.events.isEmpty()) ({ AddEventButtons { onIntent(PracticeIntent.NewEventClicked) } }) else null
                is SheetFace.Path, is SheetFace.Trophies -> null
            }
        },
        // «Имя и фото» on its side with the keyboard up: «Готово» goes under the field, the keyboard's own «Готово» answers (5.29 R3)
        buttonsInContentOverKeyboard = { shown -> SheetFaces.buttonsInContentOverKeyboard(shown, low) },
        faceOf = SheetFaces::keyOf,
    ) { shown ->
        when (shown) {
            is SheetFace.Summary -> SummarySheetContent(shown.sheet, state.stepMinutes, zone, onStep = { onIntent(PracticeIntent.SummaryStepped(it)) })
            is SheetFace.Day -> DaySheetContent(shown.day, onIntent, zone, onAddEvent = { onIntent(PracticeIntent.NewEventClicked) })
            is SheetFace.EditTime -> EditTimeSheetContent(shown.sheet, state.stepMinutes, onIntent)
            is SheetFace.Path -> PathSheetContent(shown.header, photo, onIntent)
            is SheetFace.Profile -> NamePhotoSheetContent(shown.sheet, hasPhoto = shown.header.avatarPath != null, photo, onIntent, onPickPhoto = pickPhoto)
            is SheetFace.Trophies -> TrophiesSheetContent(shown.lines, shown.totalMs)
            is SheetFace.Recap -> RecapSheetContent(
                recap = shown.recap,
                onTravel = { onIntent(PracticeIntent.RecapTravelClicked) },
                onDone = { onIntent(PracticeIntent.RecapClosed) },
                low = low,
            )
            is SheetFace.Gift -> GiftSheetContent(shown.gift, low = low)
        }
    }
}
