package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The room of the bottom zone ([AppDock]) — spec 5.29, «Нижняя зона»: the fields above, at the sides and below the buttons (the
 * system inset comes on top of [bottom]), and the height of its main button. Pure, with a test.
 *
 * - [Regular] 10 / 20 / 12, a button of 56 — a zone of 78;
 * - [Low] 8 / 16 / 10, 56 — 74: a window lower than 700 dp (360 × 640, landscape);
 * - [Tiny] 8 / 16 / 8, 48 — 64: a window no higher than 360 dp (landscape 640 × 360).
 *
 * A stage that names other sides («Занятия»: 16, as its screen, 5.29 R2) passes `copy(side = …)`.
 */
@Immutable
data class DockMetrics(val top: Dp, val side: Dp, val bottom: Dp, val button: Dp) {
    /** The zone without the system inset: its fields and one button. */
    val height: Dp get() = top + button + bottom

    /** The buttons of the zone are 48, not 56: [AppButton] with `compact`, the living «Начать занятие» at this height. */
    val compact: Boolean get() = button < TALL_BUTTON

    companion object {
        private val TALL_BUTTON = 56.dp
        private val LOW_BUTTON = 48.dp

        val Regular = DockMetrics(top = 10.dp, side = 20.dp, bottom = 12.dp, button = TALL_BUTTON)
        val Low = DockMetrics(top = 8.dp, side = 16.dp, bottom = 10.dp, button = TALL_BUTTON)
        val Tiny = DockMetrics(top = 8.dp, side = 16.dp, bottom = 8.dp, button = LOW_BUTTON)

        /** A window lower than this has the fields of [Low] (spec: «меньше 700»). */
        val LowBelow = 700.dp

        /** A window no higher than this has the button of 48 and the zone of 64. */
        val TinyUpTo = 360.dp

        fun of(windowHeight: Dp): DockMetrics = when {
            windowHeight <= TinyUpTo -> Tiny
            windowHeight < LowBelow -> Low
            else -> Regular
        }
    }
}

/** The metrics of the zone for the height of the window the app is in now. */
@Composable
fun currentDockMetrics(): DockMetrics {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return DockMetrics.of(height)
}
