package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable

/**
 * A [SystemWindowGate] kept with the screen that puts its windows up — the pickers of a copy (spec 3.36.8), the camera and the pickers
 * of «Добавить запись» of an event (spec 3.36.9): the clock of the platform that only goes forward and, on iOS, UIKit's own word that a
 * picker gone without an answer is gone.
 */
@Composable
expect fun rememberSystemWindowGate(): SystemWindowGate
