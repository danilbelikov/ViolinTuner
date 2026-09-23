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
    /** The minute arc around the dot of a running practice. */
    val timerRing: Color,
    /**
     * The streak flame (spec 3.18): an orange between the amber of "near" and the red of "off" —
     * it falls into neither zone and is used nowhere else; the core, and the brighter core of a long streak.
     */
    val flameOuter: Color,
    val flameCore: Color,
    val flameHot: Color,
    /** The lights inside «Начать занятие» (spec 3.16), back to front, and the glow under it. */
    val startLights: List<Color>,
    val startGlow: Color,
) {
    /** [level] 1–4; 0 has no fill and is not asked for. */
    fun fillFor(level: Int): Color = fills[level - 1]

    fun onFillFor(level: Int): Color = onFills[level - 1]
}

internal val DarkPracticeColors = PracticeColors(
    fills = listOf(PracticeFill1, PracticeFill2, PrimaryContainer, Primary),
    onFills = listOf(OnSurface, OnSurface, OnPrimaryContainer, OnPrimary),
    timerRing = TimerRing,
    flameOuter = FlameOuter,
    flameCore = FlameCore,
    flameHot = FlameHot,
    startLights = listOf(StartLightPeriwinkle, StartLightRose, StartLightPearl),
    startGlow = StartGlow,
)

internal val LocalPracticeColors = staticCompositionLocalOf<PracticeColors> {
    error("PracticeColors not provided: wrap content in ViolinTheme")
}
