package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The signs of the notation as vector paths (handoff `Упражнения`, `NOTATION`), in staff spaces
 * with y downwards, each around its own anchor: the clef around the G line and the centre of its
 * big loop, the accidentals and the head around the centre of the note. **Taken from the handoff
 * as they are** — a sign is changed there and copied here, not tuned by hand. The clef is an
 * outline to be stroked (0.18 of a space, round ends); the rest are filled.
 */
internal object NotationGlyphs {
    const val CLEF_STROKE = 0.18f

    private const val CLEF =
        "M -0.7 2.6 C -0.7 3.15 0.25 3.2 0.35 2.5 C 0.5 1.4 0.25 -3.6 0.12 -5.2 C 0.02 -6.3 -0.75 -6.3 -1.05 -5.1 " +
            "C -1.3 -4.0 -0.7 -2.9 0.4 -1.9 C 1.5 -1.0 1.85 0.2 1.1 1.0 C 0.35 1.75 -1.15 1.55 -1.35 0.35 " +
            "C -1.5 -0.6 -0.75 -1.35 0.15 -1.15 C 0.95 -0.95 1.0 0.2 0.15 0.35 C -0.45 0.45 -0.8 -0.05 -0.6 -0.5"
    private const val SHARP =
        "M -0.32 -0.95 h 0.1 v 2.1 h -0.1 z M 0.22 -1.15 h 0.1 v 2.1 h -0.1 z " +
            "M -0.55 -0.12 L 0.55 -0.45 L 0.55 -0.15 L -0.55 0.18 z M -0.55 0.58 L 0.55 0.25 L 0.55 0.55 L -0.55 0.88 z"
    private const val FLAT =
        "M -0.4 -1.75 h 0.1 v 2.6 h -0.1 z M -0.3 0.85 C 0.35 0.4 0.6 -0.15 0.35 -0.5 C 0.1 -0.85 -0.3 -0.6 -0.3 -0.3 " +
            "L -0.3 -0.02 C -0.12 -0.38 0.22 -0.3 0.22 0.0 C 0.22 0.3 -0.02 0.55 -0.3 0.85 z"
    private const val NATURAL =
        "M -0.3 -1.1 h 0.1 v 1.65 h -0.1 z M 0.2 -0.55 h 0.1 v 1.65 h -0.1 z " +
            "M -0.3 -0.3 L 0.3 -0.55 L 0.3 -0.25 L -0.3 0.0 z M -0.3 0.25 L 0.3 0.0 L 0.3 0.3 L -0.3 0.55 z"
    private const val DOUBLE_SHARP =
        "M 0 -0.14 L 0.32 -0.5 L 0.5 -0.5 L 0.5 -0.32 L 0.14 0 L 0.5 0.32 L 0.5 0.5 L 0.32 0.5 L 0 0.14 " +
            "L -0.32 0.5 L -0.5 0.5 L -0.5 0.32 L -0.14 0 L -0.5 -0.32 L -0.5 -0.5 L -0.32 -0.5 z"

    /** The head is an ellipse 0.62 × 0.43 leaning by −20°, built the way the handoff builds it. */
    const val HEAD_A = 0.62f
    const val HEAD_B = 0.43f
    const val HEAD_TILT_DEGREES = -20f

    val clef: Path by lazy { parse(CLEF) }
    val sharp: Path by lazy { parse(SHARP) }
    val flat: Path by lazy { parse(FLAT) }
    val natural: Path by lazy { parse(NATURAL) }
    val doubleSharp: Path by lazy { parse(DOUBLE_SHARP) }

    /** −1 flat, 0 natural, 1 sharp, 2 double sharp. */
    fun accidental(alter: Int): Path = when (alter) {
        -1 -> flat
        0 -> natural
        1 -> sharp
        else -> doubleSharp
    }

    private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()
}
