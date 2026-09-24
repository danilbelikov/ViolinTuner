package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable

/** The screen does not go dark while this is shown; as it was before once it is gone. */
@Composable
expect fun KeepScreenOn()
