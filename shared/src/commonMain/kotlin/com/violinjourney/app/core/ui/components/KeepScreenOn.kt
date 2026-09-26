package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable

/**
 * The screen does not go dark while at least one of these is shown, whatever order screens come
 * and go in: the holders are counted by [ScreenOnHolds], not remembered and restored.
 */
@Composable
expect fun KeepScreenOn()
