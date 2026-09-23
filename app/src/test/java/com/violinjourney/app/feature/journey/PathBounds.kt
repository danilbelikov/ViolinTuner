package com.violinjourney.app.feature.journey

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser

/** The ground a path of a scene covers — for tests that ask where a layer lies without drawing it. */
object PathBounds {
    /** Where a path lies, from its points and control points (a little too wide for curves, never too narrow). */
    fun of(d: String): Rect {
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        fun see(px: Float, py: Float) {
            left = minOf(left, px); right = maxOf(right, px); top = minOf(top, py); bottom = maxOf(bottom, py)
        }
        for (node in PathParser().parsePathString(d).toNodes()) {
            when (node) {
                is PathNode.MoveTo -> { x = node.x; y = node.y; startX = x; startY = y; see(x, y) }
                is PathNode.RelativeMoveTo -> { x += node.dx; y += node.dy; startX = x; startY = y; see(x, y) }
                is PathNode.LineTo -> { x = node.x; y = node.y; see(x, y) }
                is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy; see(x, y) }
                is PathNode.HorizontalTo -> { x = node.x; see(x, y) }
                is PathNode.RelativeHorizontalTo -> { x += node.dx; see(x, y) }
                is PathNode.VerticalTo -> { y = node.y; see(x, y) }
                is PathNode.RelativeVerticalTo -> { y += node.dy; see(x, y) }
                is PathNode.CurveTo -> { see(node.x1, node.y1); see(node.x2, node.y2); x = node.x3; y = node.y3; see(x, y) }
                is PathNode.RelativeCurveTo -> { see(x + node.dx1, y + node.dy1); see(x + node.dx2, y + node.dy2); x += node.dx3; y += node.dy3; see(x, y) }
                is PathNode.ReflectiveCurveTo -> { see(node.x1, node.y1); x = node.x2; y = node.y2; see(x, y) }
                is PathNode.RelativeReflectiveCurveTo -> { see(x + node.dx1, y + node.dy1); x += node.dx2; y += node.dy2; see(x, y) }
                is PathNode.QuadTo -> { see(node.x1, node.y1); x = node.x2; y = node.y2; see(x, y) }
                is PathNode.RelativeQuadTo -> { see(x + node.dx1, y + node.dy1); x += node.dx2; y += node.dy2; see(x, y) }
                is PathNode.ReflectiveQuadTo -> { x = node.x; y = node.y; see(x, y) }
                is PathNode.RelativeReflectiveQuadTo -> { x += node.dx; y += node.dy; see(x, y) }
                is PathNode.ArcTo -> { see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius); x = node.arcStartX; y = node.arcStartY; see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius) }
                is PathNode.RelativeArcTo -> { see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius); x += node.arcStartDx; y += node.arcStartDy; see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius) }
                PathNode.Close -> { x = startX; y = startY }
            }
        }
        return if (left > right) Rect.Zero else Rect(left, top, right, bottom)
    }

    /** All the ground [paths] cover together; null for none. */
    fun ofAll(paths: List<String>): Rect? = paths.map(::of).filter { it != Rect.Zero }.reduceOrNull { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
}
