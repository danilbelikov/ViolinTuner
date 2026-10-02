package com.violinjourney.app.core.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/** The check of the set ([IconPaths.CHECK]) on its grid of 24, read once for the app. */
private val CheckPath: Path by lazy { PathParser().parsePathString(IconPaths.CHECK.single()).toPath() }

/** The grid of the paths of the set. */
private const val GRID = 24f

/**
 * The check of the set as the mark of a choice (spec 5.29 R9: the mark of a row of the programme of an event, a swatch of a colour): in a
 * square of [side] px at [topLeft], its line [line] px thick — thicker than the line of the icons, so it stands out of its fill.
 */
fun DrawScope.drawCheckMark(color: Color, topLeft: Offset, side: Float, line: Float) {
    val unit = side / GRID
    translate(left = topLeft.x, top = topLeft.y) {
        scale(unit, unit, pivot = Offset.Zero) {
            drawPath(CheckPath, color, style = Stroke(width = line / unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
