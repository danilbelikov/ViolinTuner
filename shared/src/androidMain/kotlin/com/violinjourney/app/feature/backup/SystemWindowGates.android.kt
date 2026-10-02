package com.violinjourney.app.feature.backup

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** The activity of a picker answers a cancel too, with null: nothing more is needed to know it is gone. */
@Composable
actual fun rememberSystemWindowGate(): SystemWindowGate = remember { SystemWindowGate(now = SystemClock::elapsedRealtime) }
