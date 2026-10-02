package com.violinjourney.app.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import com.violinjourney.app.core.ui.motion.LocalReduceMotion

/** The tag of the box [TestWindow] lays a screen out in. */
const val TEST_WINDOW = "window"

private class FixedWindow(private val size: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
    override val containerSize: IntSize get() = size
}

/**
 * A screen laid out in a window of its own size, whatever the device's — the lessons of stages 101–117 in one place:
 *
 * - [size] is the room the root of the app leaves a screen under the system bars, and like the root (`MainActivity`:
 *   `padding(innerPadding).consumeWindowInsets(innerPadding)`, safeDrawing less the keyboard) the box takes those bars away: else a
 *   bottom zone pads the gesture bar of the emulator (24) inside a box that already stands for what the bars leave (stage 117);
 * - [told] is the window the screen is told it is in ([LocalWindowInfo]: the bars of the journey and the bottom zones read its size) —
 *   892 × 412 lying is some 360 high under its bars and keeps the zone of a window of 412;
 * - touch targets under 48 are not widened, so a test of a touch area can fail; [fontScale] is the system font; pictures stand still;
 * - [density] — pixels a dp, the device's unless named: a phone of another density lays its dp out in other whole pixels (the
 *   emulator and the owner's Pixel 10a are 2.625). Its pixels must still fit the screen of the device the test runs on.
 */
@Composable
fun TestWindow(size: DpSize, told: DpSize = size, fontScale: Float = 1f, density: Float? = null, content: @Composable () -> Unit) {
    val device = LocalDensity.current
    val pixels = Density(density ?: device.density, fontScale)
    val base = LocalViewConfiguration.current
    val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
    val window = with(pixels) { FixedWindow(IntSize(told.width.roundToPx(), told.height.roundToPx())) }
    CompositionLocalProvider(
        LocalWindowInfo provides window,
        LocalViewConfiguration provides noWidening,
        LocalDensity provides pixels,
        // a living picture would ask for frames all the time
        LocalReduceMotion provides true,
    ) {
        Box(
            Modifier
                .requiredSize(size.width, size.height)
                .consumeWindowInsets(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                .testTag(TEST_WINDOW),
        ) { content() }
    }
}
