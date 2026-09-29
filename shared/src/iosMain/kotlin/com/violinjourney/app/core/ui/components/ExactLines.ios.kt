package com.violinjourney.app.core.ui.components

import androidx.compose.ui.text.style.LineHeightStyle

// Nothing trimmed is Skia's HeightMode.ALL: every line, the first and the last too, at its line height (the mode is not read there)
internal actual val ExactLines: LineHeightStyle =
    LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
