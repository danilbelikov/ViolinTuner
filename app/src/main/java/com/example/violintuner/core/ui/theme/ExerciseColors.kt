package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Sections of the repertoire and drawn scales (spec 3.22, handoff `Упражнения`, `tokens`): the
 * three shares of a section's bar — violets growing lighter towards «выучено», never the green of
 * a zone — and the ink of the notation, light on the dark card and dark on the paper of the stand.
 */
@Immutable
data class ExerciseColors(val reading: Color, val learning: Color, val learned: Color, val inkOnDark: Color, val inkOnPaper: Color)

internal val DarkExerciseColors =
    ExerciseColors(reading = LearnReading, learning = LearnLearning, learned = Primary, inkOnDark = InkOnDark, inkOnPaper = InkOnPaper)

internal val LocalExerciseColors = staticCompositionLocalOf<ExerciseColors> {
    error("ExerciseColors not provided: wrap content in ViolinTheme")
}
