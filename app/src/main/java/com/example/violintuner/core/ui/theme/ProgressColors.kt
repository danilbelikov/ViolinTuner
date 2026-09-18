package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Materials the trophy illustrations are made of (handoff `trophy.*`). */
@Immutable
data class TrophyPalette(
    val wood: Color,
    val woodLight: Color,
    val ebony: Color,
    val ebonyLight: Color,
    /** Hairline around ebony parts: without it they sink into the surface. */
    val ebonyEdge: Color,
    val silver: Color,
    val silverLight: Color,
    val gold: Color,
    val rosin: Color,
    val rosinLight: Color,
    val rosinGlint: Color,
    val hair: Color,
    val paper: Color,
    val ink: Color,
    val velvet: Color,
    val velvetDark: Color,
    val case: Color,
    /** Outline of a trophy not yet given, and what fills it. */
    val locked: Color,
    val lockedFill: Color,
)

/**
 * Tokens of the profile header and the trophies (handoff `Прогресс.dc.html`, `tokens`). Like
 * the calendar fills they are not zone colors: progress is time, not a grade.
 */
@Immutable
data class ProgressColors(
    val levelTrack: Color,
    val levelFillStart: Color,
    val levelFillEnd: Color,
    val avatarLetterBackground: Color,
    val avatarLetter: Color,
    val avatarLevelBackground: Color,
    val avatarLevelBorder: Color,
    val avatarLevel: Color,
    /** Centre of the glow behind the trophy on the gift sheet; fades to nothing at the edge. */
    val giftGlow: Color,
    val trophy: TrophyPalette,
)

internal val DarkProgressColors = ProgressColors(
    levelTrack = SurfaceContainerHigh,
    levelFillStart = PrimaryContainer,
    levelFillEnd = Primary,
    avatarLetterBackground = Primary,
    avatarLetter = OnPrimary,
    avatarLevelBackground = SurfaceContainerHigh,
    avatarLevelBorder = OutlineVariant,
    avatarLevel = OnSurface,
    giftGlow = GiftGlow,
    trophy = TrophyPalette(
        wood = TrophyWood,
        woodLight = TrophyWoodLight,
        ebony = TrophyEbony,
        ebonyLight = TrophyEbonyLight,
        ebonyEdge = TrophyEbonyEdge,
        silver = TrophySilver,
        silverLight = OnSurface,
        gold = TrophyGold,
        rosin = TrophyRosin,
        rosinLight = TrophyRosinLight,
        rosinGlint = TrophyRosinGlint,
        hair = TrophyHair,
        paper = TrophyPaper,
        ink = TrophyInk,
        velvet = PrimaryContainer,
        velvetDark = PracticeFill2,
        case = TrophyCase,
        locked = TrophyLocked,
        lockedFill = SurfaceContainer,
    ),
)

internal val LocalProgressColors = staticCompositionLocalOf<ProgressColors> {
    error("ProgressColors not provided: wrap content in ViolinTheme")
}
