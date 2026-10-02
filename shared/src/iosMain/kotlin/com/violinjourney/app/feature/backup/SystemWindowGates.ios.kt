package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.violinjourney.app.core.ui.components.SystemScreens
import platform.Foundation.NSProcessInfo

/**
 * One window per press, as on Android, though UIKit would refuse a second one while the first is coming up: the delegate of a picker
 * answers a pick and a cancel; a picker gone without a word is seen in UIKit's own state (SystemScreens.nothingUp).
 */
@Composable
actual fun rememberSystemWindowGate(): SystemWindowGate = remember { SystemWindowGate(now = ::uptimeMs, pickerGone = SystemScreens::nothingUp) }

/** Milliseconds since the start of the phone, which only go forward. */
private fun uptimeMs(): Long = (NSProcessInfo.processInfo.systemUptime * MS_PER_SECOND).toLong()

private const val MS_PER_SECOND = 1_000
