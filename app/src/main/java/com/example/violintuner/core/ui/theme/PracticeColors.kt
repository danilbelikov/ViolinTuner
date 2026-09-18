package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens of the practice calendar (handoff `Занятия.dc.html`, `tokens`): four fill tones by
 * the time of the day and the number color on each. Index 0 is level 1 of
 * [com.example.violintuner.core.domain.practice.PracticeStats.fillLevel].
 */
@Immutable
data class PracticeColors(
    val fills: List<Color>,
    val onFills: List<Color>,
) {
    /** [level] 1–4; 0 has no fill and is not asked for. */
    fun fillFor(level: Int): Color = fills[level - 1]

    fun onFillFor(level: Int): Color = onFills[level - 1]
}

internal val DarkPracticeColors = PracticeColors(
    fills = listOf(PracticeFill1, PracticeFill2, PrimaryContainer, Primary),
    onFills = listOf(OnSurface, OnSurface, OnPrimaryContainer, OnPrimary),
)

internal val LocalPracticeColors = staticCompositionLocalOf<PracticeColors> {
    error("PracticeColors not provided: wrap content in ViolinTheme")
}
