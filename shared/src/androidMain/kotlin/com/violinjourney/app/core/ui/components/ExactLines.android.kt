package com.violinjourney.app.core.ui.components

import androidx.compose.ui.text.style.LineHeightStyle

// Tight trims both ends to the line height even where the font's ascent and descent are taller, and adds no paddings back
internal actual val ExactLines: LineHeightStyle =
    LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight)
