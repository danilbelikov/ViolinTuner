package com.violinjourney.app.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * What a modal sheet shows while it goes away. The owner closes a sheet by dropping its value — a button of the sheet
 * tells the model, the model says null — and a `ModalBottomSheet` that simply leaves the composition vanishes in one
 * frame, scrim and all, while a swipe takes it down. Here the last value is held until [sheetState] has slid the
 * sheet down, and only then does null come back: a sheet closed by its own button goes as a swiped one does.
 *
 * - A sheet still rising is on screen too, and slides away from where it is: M3 counts it visible only once the rise
 *   has settled, about half a second after it opens.
 * - A value that comes back while the sheet slides brings it up again.
 * - A tap on the scrim or a finger that takes the sliding sheet still ends with the sheet gone — never a hidden modal
 *   window left behind that takes the next touch.
 * - [slideAway] false — another sheet comes in this one's place — and reduced motion let it go at once, as before. Its
 *   state is then taken down out of sight — from the top or from wherever a cut rise left it — so that the next
 *   opening comes up from below again.
 *
 * Call it above the `?: return` of the sheet, with the same [sheetState] the `ModalBottomSheet` gets — the state lives
 * on between openings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> rememberHeldSheet(value: T?, sheetState: SheetState, slideAway: Boolean = true): T? {
    val slides = slideAway && !LocalReduceMotion.current
    val hold = remember { SheetHold<T>() }
    // the last value shown, as rememberUpdatedState keeps it: written before anything below reads it
    if (value != null) hold.shown.value = value
    LaunchedEffect(value == null) {
        if (value != null) {
            // back before the sheet was gone: up it comes
            if (hold.sliding) {
                hold.sliding = false
                sheetState.show()
            }
            return@LaunchedEffect
        }
        if (hold.shown.value == null) return@LaunchedEffect
        // isVisible turns true only once the rise has settled; a sheet heading up is on screen already
        val onScreen = sheetState.isVisible || sheetState.targetValue != SheetValue.Hidden
        val slide = slides && onScreen
        try {
            if (slide) {
                hold.sliding = true
                sheetState.hide()
            }
        } finally {
            // Cancelled only by the value coming back, which keeps the sheet. Anything else — the slide done, a
            // finger that took the sheet (its refusal ends this effect here) — lets the sheet go.
            if (currentCoroutineContext().isActive) {
                hold.sliding = false
                hold.shown.value = null
            }
        }
        // Gone at once: take the state down out of sight — the hoisted state keeps its offset, and a cut rise leaves it
        // near the top with isVisible still false. Down already, it is over in a frame.
        if (!slide) sheetState.hide()
    }
    return value ?: hold.shown.value.takeIf { slides }
}

private class SheetHold<T : Any> {
    val shown: MutableState<T?> = mutableStateOf(null)

    /** A slide started here and not finished: a value that comes back brings the sheet up. */
    var sliding = false
}
