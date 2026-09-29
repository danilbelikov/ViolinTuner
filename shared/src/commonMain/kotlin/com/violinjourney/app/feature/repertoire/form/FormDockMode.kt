package com.violinjourney.app.feature.repertoire.form

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the bottom zone of a form stands (spec 3.36.1 rule 3, 3.36.4, 5.29 R4): [COLUMN] — the reason over the main button as wide as
 * the column; [ROW] — where less than [RowBelow] is left between the bar and the keyboard (landscape, a small window): one line, the
 * reason on the left and the button of 48 on the right, so that the explanation does not go and the field in focus still has room;
 * [IN_CONTENT] — the keyboard is up and leaves less than that line and a field together (a phone lying, its keyboard of 230–262 dp):
 * the line leaves its pinned place for the end of what scrolls, under the last field, and the field in focus gets the whole room over
 * the keyboard; the keyboard gone, the zone is pinned again. Pure. Public, as [FormFrame] that hands it to its zone.
 */
enum class FormDockMode {
    COLUMN,
    ROW,
    IN_CONTENT,
    ;

    companion object {
        /** Less room than this between the bar of the form and the keyboard (or the bottom of the window) — one line. */
        val RowBelow = 200.dp

        /**
         * [room] — from the bar down to the keyboard, or to the bottom inset while there is none; unspecified — not measured yet.
         * [keyboardUp] — the keyboard stands over the form: only then does the zone leave its place — without it nothing is typed,
         * and a low window keeps its line. [line] — the zone in one line with its paddings; [field] — the least a field takes at the
         * top of the column, the air over it and its caption with it: where the room holds less than both, the line goes under the
         * fields.
         */
        fun of(room: Dp, keyboardUp: Boolean = false, line: Dp = 0.dp, field: Dp = 0.dp): FormDockMode = when {
            room == Dp.Unspecified -> COLUMN
            keyboardUp && room < line + field -> IN_CONTENT
            room < RowBelow -> ROW
            else -> COLUMN
        }
    }
}
