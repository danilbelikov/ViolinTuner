package com.violinjourney.app.core.ui.icons

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import com.violinjourney.app.core.domain.events.KindSign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mini signs of the kinds (spec 3.36.9, 5.29 R9; events-kinds.html, «Для реализации»): sixteen silhouettes, all different, each
 * on its grid of 12, and «+» in its box of 6 × 10 — what a cell of the calendar, «ещё N» and the legend scale to their sizes.
 */
class EventMiniSignsTest {
    @Test
    fun `every sign has its own outline`() {
        assertEquals(KindSign.entries.toSet(), EventMiniSigns.OUTLINES.keys, "a mini sign for each of the sixteen signs")
        assertEquals(KindSign.entries.size, EventMiniSigns.OUTLINES.values.toSet().size, "no two signs share a silhouette")
    }

    @Test
    fun `every sign lies on its grid of 12 and fills most of it`() {
        EventMiniSigns.OUTLINES.forEach { (sign, d) ->
            val bounds = boundsOf(d)
            assertTrue(bounds.left >= -EPSILON && bounds.top >= -EPSILON, "$sign begins inside the grid: $bounds")
            assertTrue(bounds.right <= EventMiniSigns.GRID + EPSILON && bounds.bottom <= EventMiniSigns.GRID + EPSILON, "$sign ends inside the grid: $bounds")
            // a mark of 10 dp is read by its form: none of them is a speck in the corner of its square
            assertTrue(bounds.width >= MIN_SPAN && bounds.height >= MIN_SPAN, "$sign spans at least $MIN_SPAN of 12 each way: $bounds")
        }
    }

    @Test
    fun `the plus of a fourth event is a cross in its box of 6 by 10`() {
        val bounds = boundsOf(EventMiniSigns.MORE)
        assertTrue(bounds.left >= 0f && bounds.right <= EventMiniSigns.MORE_WIDTH, "across: $bounds")
        assertTrue(bounds.top >= 0f && bounds.bottom <= EventMiniSigns.MORE_HEIGHT, "down: $bounds")
        val strokes = PathParser().parsePathString(EventMiniSigns.MORE).toNodes().count { it is PathNode.MoveTo || it is PathNode.RelativeMoveTo }
        assertEquals(2, strokes, "two strokes, one across and one down")
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * The walk of [boundsOf] follows the curve Compose draws: a reflected curve (`S`, `T`) takes for its first control point the last
     * control point of the curve before it mirrored about its start (SVG 1.1, 8.3.6–8.3.7; Compose `PathNode.toPath`), the start
     * itself after anything else. Written out with that point, a path lies where it lay; and a reflected tail can reach past the end
     * of its own controls — out of the grid of 12 here, which a walk taking the start for that point would not see.
     */
    @Test
    fun `a reflected curve is walked as it is drawn`() {
        assertSameBounds(boundsOf(CHAT_LIKE), boundsOf(CHAT_LIKE_WRITTEN_OUT), "S is C with the mirrored control point")
        assertSameBounds(boundsOf("M1 6Q3 1 6 6T11 6"), boundsOf("M1 6Q3 1 6 6Q9 11 11 6"), "T is Q with the mirrored control point")
        assertSameBounds(boundsOf("M1 6L4 2S7 2 9 6"), boundsOf("M1 6L4 2C4 2 7 2 9 6"), "after a line S starts from its own start")
        assertTrue(boundsOf(PAST_THE_GRID).right > EventMiniSigns.GRID, "the tail bulges past x = 12: ${boundsOf(PAST_THE_GRID)}")
    }

    private fun assertSameBounds(expected: Bounds, actual: Bounds, message: String) {
        val apart = maxOf(abs(expected.left - actual.left), abs(expected.top - actual.top), abs(expected.right - actual.right), abs(expected.bottom - actual.bottom))
        assertTrue(apart < EPSILON, "$message: $expected and $actual")
    }

    /**
     * Where a path lies: its lines by their ends, its curves and arcs sampled along them — an arc bulges past its ends and its
     * control points do not tell how far, so an arc is turned to its centre and angles (SVG 1.1, F.6.5) and walked. A reflected
     * curve is walked with the control point Compose gives it (the test above).
     */
    private fun boundsOf(d: String): Bounds {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        // the second control point of the last cubic curve, the control point of the last quadratic one; null after anything else
        var lastCubic: Pair<Float, Float>? = null
        var lastQuad: Pair<Float, Float>? = null
        fun see(px: Float, py: Float) {
            xs += px
            ys += py
        }
        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            for (step in 0..SAMPLES) {
                val t = step.toFloat() / SAMPLES
                val u = 1 - t
                see(
                    u * u * u * x + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
                )
            }
            x = x3
            y = y3
            lastCubic = x2 to y2
        }
        fun quad(x1: Float, y1: Float, x2: Float, y2: Float) {
            for (step in 0..SAMPLES) {
                val t = step.toFloat() / SAMPLES
                val u = 1 - t
                see(u * u * x + 2 * u * t * x1 + t * t * x2, u * u * y + 2 * u * t * y1 + t * t * y2)
            }
            x = x2
            y = y2
            lastQuad = x1 to y1
        }
        // the control point a reflected curve starts with: the last one mirrored about the start, or the start itself
        fun mirrored(last: Pair<Float, Float>?): Pair<Float, Float> = last?.let { (lx, ly) -> 2 * x - lx to 2 * y - ly } ?: (x to y)
        fun arc(rx: Float, ry: Float, degrees: Float, large: Boolean, sweep: Boolean, endX: Float, endY: Float) {
            arcPoints(x, y, rx, ry, degrees, large, sweep, endX, endY).forEach { (px, py) -> see(px, py) }
            x = endX
            y = endY
            see(x, y)
        }
        for (node in PathParser().parsePathString(d).toNodes()) {
            val cubicBefore = lastCubic
            val quadBefore = lastQuad
            lastCubic = null
            lastQuad = null
            when (node) {
                is PathNode.MoveTo -> { x = node.x; y = node.y; startX = x; startY = y; see(x, y) }
                is PathNode.RelativeMoveTo -> { x += node.dx; y += node.dy; startX = x; startY = y; see(x, y) }
                is PathNode.LineTo -> { x = node.x; y = node.y; see(x, y) }
                is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy; see(x, y) }
                is PathNode.HorizontalTo -> { x = node.x; see(x, y) }
                is PathNode.RelativeHorizontalTo -> { x += node.dx; see(x, y) }
                is PathNode.VerticalTo -> { y = node.y; see(x, y) }
                is PathNode.RelativeVerticalTo -> { y += node.dy; see(x, y) }
                is PathNode.CurveTo -> cubic(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                is PathNode.RelativeCurveTo -> cubic(x + node.dx1, y + node.dy1, x + node.dx2, y + node.dy2, x + node.dx3, y + node.dy3)
                is PathNode.ReflectiveCurveTo -> mirrored(cubicBefore).let { (cx, cy) -> cubic(cx, cy, node.x1, node.y1, node.x2, node.y2) }
                is PathNode.RelativeReflectiveCurveTo -> mirrored(cubicBefore).let { (cx, cy) ->
                    cubic(cx, cy, x + node.dx1, y + node.dy1, x + node.dx2, y + node.dy2)
                }
                is PathNode.QuadTo -> quad(node.x1, node.y1, node.x2, node.y2)
                is PathNode.RelativeQuadTo -> quad(x + node.dx1, y + node.dy1, x + node.dx2, y + node.dy2)
                is PathNode.ReflectiveQuadTo -> mirrored(quadBefore).let { (cx, cy) -> quad(cx, cy, node.x, node.y) }
                is PathNode.RelativeReflectiveQuadTo -> mirrored(quadBefore).let { (cx, cy) -> quad(cx, cy, x + node.dx, y + node.dy) }
                is PathNode.ArcTo -> arc(
                    node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc,
                    node.arcStartX, node.arcStartY,
                )
                is PathNode.RelativeArcTo -> arc(
                    node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc,
                    x + node.arcStartDx, y + node.arcStartDy,
                )
                PathNode.Close -> { x = startX; y = startY }
            }
        }
        return Bounds(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /** The points of an SVG arc from ([x1], [y1]) to ([x2], [y2]), by its centre parametrisation (SVG 1.1, F.6.5–F.6.6). */
    private fun arcPoints(
        x1: Float, y1: Float, radiusX: Float, radiusY: Float, degrees: Float, large: Boolean, sweep: Boolean, x2: Float, y2: Float,
    ): List<Pair<Float, Float>> {
        if (radiusX == 0f || radiusY == 0f || (x1 == x2 && y1 == y2)) return listOf(x2 to y2)
        val phi = degrees * PI / 180
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x1 - x2) / 2.0
        val dy = (y1 - y2) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        var rx = abs(radiusX.toDouble())
        var ry = abs(radiusY.toDouble())
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coefficient = (if (large != sweep) 1 else -1) * sqrt(maxOf(0.0, numerator / denominator))
        val cxp = coefficient * rx * y1p / ry
        val cyp = -coefficient * ry * x1p / rx
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val length = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            val value = acos((dot / length).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0) -value else value
        }
        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val start = angle(1.0, 0.0, ux, uy)
        var delta = angle(ux, uy, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        return (0..SAMPLES).map { step ->
            val theta = start + delta * step / SAMPLES
            val px = cosPhi * rx * cos(theta) - sinPhi * ry * sin(theta) + cx
            val py = sinPhi * rx * cos(theta) + cosPhi * ry * sin(theta) + cy
            px.toFloat() to py.toFloat()
        }
    }

    private companion object {
        const val SAMPLES = 64
        const val EPSILON = 0.01f

        /** The first two curves of the chat bubble ([KindSign.CHAT]): `c`, then `S` — and the same with `S` written out as `C`. */
        const val CHAT_LIKE = "M6 1.3c2.9 0 5.2 2 5.2 4.4S8.9 10.1 6 10.1"
        const val CHAT_LIKE_WRITTEN_OUT = "M6 1.3C8.9 1.3 11.2 3.3 11.2 5.7C11.2 8.1 8.9 10.1 6 10.1"

        /** A curve that ends at (11.5, 5.7) coming from (9.5, 3.3), and an `S` after it: mirrored, its first control point is (13.5, 8.1). */
        const val PAST_THE_GRID = "M8 1C9 2 9.5 3.3 11.5 5.7S11.5 10 9 11"

        /** The least a mark spans each way: half its grid — the flattest of the table, the bow tie, is 6.8 high. */
        const val MIN_SPAN = 6f
    }
}
