package com.violinjourney.app.core.ui.components

import androidx.compose.ui.text.style.LineHeightStyle

/**
 * Lines no taller than their line height, the first and the last as well — as a line of CSS: for a label whose box must stand in a
 * room made for one line of it (the pills of 24 of the compact switch, spec 5.29 R5). Each platform says it its own way.
 *
 * On Android material3's own ([LineHeightStyle.Mode.Fixed], nothing trimmed) sets the line height but pads the top of a first line and
 * the bottom of a last one back to the font's own ascent and descent where they are taller: Manrope's are 1.066 and 0.3 em — a box of
 * 1.37 em around a line of 1.15. [LineHeightStyle.Mode.Tight] with both ends trimmed keeps the line height asked (the font's own where
 * that is lower). Skia (iOS) lays every line out at its line height when nothing is trimmed and reads no mode; trimming there would give
 * the font's ascent and descent back. A glyph taller than its line is drawn past the box, not cut: the box only places the line.
 */
internal expect val ExactLines: LineHeightStyle
