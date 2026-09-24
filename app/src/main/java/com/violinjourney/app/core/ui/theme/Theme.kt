package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Composable

/** The theme of the app on Android: the shared one, with Manrope from res/font. */
@Composable
fun ViolinTheme(content: @Composable () -> Unit) = ViolinAppTheme(fontFamily = Manrope, content = content)
