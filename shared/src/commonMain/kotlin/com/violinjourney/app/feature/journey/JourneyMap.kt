package com.violinjourney.app.feature.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.Transport
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.journey_cities
import com.violinjourney.app.shared.resources.journey_map_description
import kotlin.math.atan2
import kotlin.math.hypot
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Geometry of the route map (handoff `MAP`): a grid of 412 × 700, one Catmull-Rom curve through the stops, cut into legs. Pure. */
object JourneyMapMath {
    private const val DEGREES_PER_RADIAN = 180.0 / kotlin.math.PI

    const val WIDTH = 412f
    const val HEIGHT = 700f
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 2.5f
    const val ROAD_ZOOM = 1.8f

    /** The reach of a tap around a stop, in units of the grid. */
    const val TAP_RADIUS = 22f

    data class Point(val x: Float, val y: Float)

    /** The cubic of one leg: from stop [index] to the next. */
    data class Leg(val from: Point, val c1: Point, val c2: Point, val to: Point)

    fun points(): List<Point> = JourneyRoute.stops.map { Point(it.mapX, it.mapY) }

    fun leg(index: Int, points: List<Point> = points()): Leg {
        val p0 = points[(index - 1).coerceAtLeast(0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
        return Leg(p1, Point(p1.x + (p2.x - p0.x) / TENSION, p1.y + (p2.y - p0.y) / TENSION), Point(p2.x - (p3.x - p1.x) / TENSION, p2.y - (p3.y - p1.y) / TENSION), p2)
    }

    fun at(leg: Leg, t: Float): Point {
        val u = 1 - t
        fun axis(a: Float, b: Float, c: Float, d: Float) = u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
        return Point(axis(leg.from.x, leg.c1.x, leg.c2.x, leg.to.x), axis(leg.from.y, leg.c1.y, leg.c2.y, leg.to.y))
    }

    /** The heading on the leg at [t], in degrees: where the train looks. */
    fun headingAt(leg: Leg, t: Float): Float {
        val a = at(leg, (t - STEP).coerceAtLeast(0f))
        val b = at(leg, (t + STEP).coerceAtMost(1f))
        return (atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()) * DEGREES_PER_RADIAN).toFloat()
    }

    /** The stop under a tap, among those the player has reached; null when the tap is nowhere near. */
    fun stopAt(x: Float, y: Float, reached: Int): Int? = points().withIndex()
        .filter { it.index <= reached }
        .minByOrNull { hypot(it.value.x - x, it.value.y - y) }
        ?.takeIf { hypot(it.value.x - x, it.value.y - y) <= TAP_RADIUS }
        ?.index

    /** The map never leaves the screen by more than its own overhang: at the fitting zoom it does not move at all. */
    fun clampPan(x: Float, y: Float, k: Float, width: Float, height: Float): Point {
        val overX = ((WIDTH * k - width) / 2).coerceAtLeast(0f)
        val overY = ((HEIGHT * k - height) / 2).coerceAtLeast(0f)
        return Point(x.coerceIn(-overX, overX), y.coerceIn(-overY, overY))
    }

    private const val TENSION = 6f
    private const val STEP = 0.02f
}

private val LAND = listOf(
    "M150 160C190 130 250 128 300 140C340 150 380 170 404 200C412 240 400 280 370 300C350 330 330 360 300 392C270 410 220 412 180 398C150 386 118 360 100 330C86 300 90 260 110 230C120 200 130 180 150 160Z",
    "M96 168C110 156 130 162 138 178C144 194 138 212 124 220C110 226 94 216 92 200C90 188 90 176 96 168Z",
    "M0 420C30 410 60 430 70 460C80 500 66 540 60 580C56 620 80 640 96 660C100 680 80 700 50 700C20 700 0 690 0 680Z",
    "M404 300C412 300 412 300 412 300V520C400 520 380 500 372 470C364 440 350 420 360 390C368 360 396 330 404 300Z",
    "M320 620C340 600 370 600 394 610C410 618 412 640 412 660C412 690 390 700 366 700C340 700 316 686 312 660C310 646 310 632 320 620Z",
)

/** Where the name of a stop stands beside its dot: dx, dy, and whether the text ends there (handoff `LBL`). */
private val LABELS: Map<String, Triple<Float, Float, Boolean>> = mapOf(
    "home" to Triple(-8f, -8f, true), "cremona" to Triple(8f, 14f, false), "milan" to Triple(-8f, -8f, true), "salzburg" to Triple(4f, -10f, false),
    "vienna" to Triple(10f, -6f, false), "prague" to Triple(8f, -8f, false), "leipzig" to Triple(-8f, -8f, true), "berlin" to Triple(8f, -8f, false),
    "amsterdam" to Triple(-8f, -8f, true), "paris" to Triple(-8f, 14f, true), "london" to Triple(-8f, -8f, true), "spb" to Triple(-8f, -8f, true),
    "moscow" to Triple(-8f, 16f, true), "newyork" to Triple(8f, -8f, false), "buenosaires" to Triple(8f, -8f, false), "tokyo" to Triple(-8f, -8f, true),
    "sydney" to Triple(-8f, -8f, true),
)

/** The road being travelled: the leg from stop [fromIndex] and how far along it the train is. */
data class RoadOnMap(val fromIndex: Int, val progress: Float, val transport: Transport)

/**
 * The route map (spec 3.23, handoff 26e): a scheme, not a globe — Europe large, the far cities at
 * the edges. What is behind is a solid line over its own glow, the leg ahead is dashed in the
 * accent, the rest dashed and quiet; names appear as the cities come near. With [road] the map is
 * the road itself: zoomed onto the leg, the line drawn as the train runs along it.
 */
@Composable
fun JourneyMapCanvas(reached: Int, modifier: Modifier = Modifier, road: RoadOnMap? = null, interactive: Boolean = false, onStopTap: (Int) -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val cities = stringArrayResource(Res.array.journey_cities)
    val measurer = rememberTextMeasurer()
    val land = remember { LAND.map { PathParser().parsePathString(it).toPath() } }
    val points = remember { JourneyMapMath.points() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val description = stringResource(Res.string.journey_map_description, reached, points.lastIndex)
    // the view: grid units at the centre of the box, and how many pixels one of them takes
    fun centreOf(): JourneyMapMath.Point = when {
        road != null -> JourneyMapMath.at(JourneyMapMath.leg(road.fromIndex, points), 0.5f)
        else -> JourneyMapMath.Point(JourneyMapMath.WIDTH / 2, JourneyMapMath.HEIGHT / 2)
    }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(colors.surface)
            .semantics { contentDescription = description }
            .then(
                if (!interactive) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTransformGestures { _, drag, change, _ ->
                            zoom = (zoom * change).coerceIn(JourneyMapMath.MIN_ZOOM, JourneyMapMath.MAX_ZOOM)
                            val k = minOf(size.width / JourneyMapMath.WIDTH, size.height / JourneyMapMath.HEIGHT) * zoom
                            pan = JourneyMapMath.clampPan(pan.x + drag.x, pan.y + drag.y, k, size.width.toFloat(), size.height.toFloat()).let { Offset(it.x, it.y) }
                        }
                    }
                    .pointerInput(reached) {
                        detectTapGestures { tap ->
                            val k = minOf(size.width / JourneyMapMath.WIDTH, size.height / JourneyMapMath.HEIGHT) * zoom
                            val centre = centreOf()
                            val x = (tap.x - size.width / 2f - pan.x) / k + centre.x
                            val y = (tap.y - size.height / 2f - pan.y) / k + centre.y
                            JourneyMapMath.stopAt(x, y, reached)?.let(onStopTap)
                        }
                    },
            ),
    ) {
        // the road fills the screen with its leg; the map shows the whole route and lets the fingers come closer
        val k = if (road != null) maxOf(size.width / JourneyMapMath.WIDTH, size.height / JourneyMapMath.HEIGHT) * JourneyMapMath.ROAD_ZOOM
        else minOf(size.width / JourneyMapMath.WIDTH, size.height / JourneyMapMath.HEIGHT) * zoom
        val centre = centreOf()
        val shift = if (road != null) Offset.Zero else pan
        translate(size.width / 2f + shift.x - centre.x * k, size.height / 2f + shift.y - centre.y * k) {
            scale(k, k, pivot = Offset.Zero) {
                land.forEach { path ->
                    translate(0f, 3f) { drawPath(path, colors.surfaceContainerHigh) }
                    drawPath(path, colors.surfaceContainer)
                }
                drawRoute(points, reached, road, k, colors.primary, colors.outlineVariant)
                drawStops(points, reached, road, k, colors.primary, colors.outlineVariant, colors.surface)
            }
        }
        // names are drawn in pixels, so they stay the size of text whatever the zoom
        val origin = Offset(size.width / 2f + shift.x - centre.x * k, size.height / 2f + shift.y - centre.y * k)
        JourneyRoute.stops.forEachIndexed { index, stop ->
            val arrived = road != null && index == road.fromIndex + 1
            if (index > reached + 1 && !arrived) return@forEachIndexed
            val current = index == reached
            val (dx, dy, ends) = LABELS[stop.id] ?: Triple(8f, -8f, false)
            val text = measurer.measure(
                cities.getOrElse(index) { "" },
                TextStyle(color = if (current) colors.onSurface else colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
                softWrap = false,
            )
            val anchor = origin + Offset(points[index].x * k + dx * density, points[index].y * k + dy * density)
            drawText(text, topLeft = Offset(if (ends) anchor.x - text.size.width else anchor.x, anchor.y - text.size.height * 0.75f))
        }
    }
}

private fun DrawScope.drawRoute(points: List<JourneyMapMath.Point>, reached: Int, road: RoadOnMap?, k: Float, accent: Color, quiet: Color) {
    for (index in 0 until points.lastIndex) {
        val leg = JourneyMapMath.leg(index, points)
        val path = Path().apply {
            moveTo(leg.from.x, leg.from.y)
            cubicTo(leg.c1.x, leg.c1.y, leg.c2.x, leg.c2.y, leg.to.x, leg.to.y)
        }
        val travelling = road != null && index == road.fromIndex
        val done = index < reached && !travelling
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f / k * density, 6f / k * density))
        when {
            done -> {
                drawPath(path, accent.copy(alpha = 0.18f), style = Stroke(10f / k * density, cap = StrokeCap.Round))
                drawPath(path, accent, style = Stroke(2.4f / k * density, cap = StrokeCap.Round))
            }
            travelling -> {
                drawPath(path, accent.copy(alpha = 0.75f), style = Stroke(2f / k * density, cap = StrokeCap.Round, pathEffect = dash))
                // the line is drawn as the train runs: the part behind it is solid
                val measure = PathMeasure().apply { setPath(path, false) }
                val behind = Path().also { measure.getSegment(0f, measure.length * road!!.progress, it, true) }
                drawPath(behind, accent.copy(alpha = 0.18f), style = Stroke(10f / k * density, cap = StrokeCap.Round))
                drawPath(behind, accent, style = Stroke(2.4f / k * density, cap = StrokeCap.Round))
            }
            index == reached -> drawPath(path, accent.copy(alpha = 0.75f), style = Stroke(2f / k * density, cap = StrokeCap.Round, pathEffect = dash))
            else -> drawPath(path, quiet, style = Stroke(2f / k * density, cap = StrokeCap.Round, pathEffect = dash))
        }
    }
    if (road != null) {
        val leg = JourneyMapMath.leg(road.fromIndex, points)
        val at = JourneyMapMath.at(leg, road.progress)
        drawTransport(at, JourneyMapMath.headingAt(leg, road.progress), road.transport, k, accent)
    }
}

private fun DrawScope.drawStops(points: List<JourneyMapMath.Point>, reached: Int, road: RoadOnMap?, k: Float, accent: Color, quiet: Color, water: Color) {
    fun px(dp: Float) = dp / k * density
    points.forEachIndexed { index, point ->
        val centre = Offset(point.x, point.y)
        val current = index == reached && road == null
        val done = index < reached || (index == reached && road != null)
        val next = index == reached + 1
        when {
            current -> {
                drawCircle(accent.copy(alpha = 0.5f), radius = px(10f), center = centre, style = Stroke(px(1.5f)))
                drawCircle(accent, radius = px(6f), center = centre)
            }
            done -> drawCircle(accent, radius = px(4f), center = centre)
            next -> {
                drawCircle(water, radius = px(4f), center = centre)
                drawCircle(accent, radius = px(4f), center = centre, style = Stroke(px(1.5f)))
            }
            else -> drawCircle(quiet, radius = px(3f), center = centre)
        }
    }
}

/** Three silhouettes, 24 × 12, drawn once and turned along the road: a train across Europe, a ship and a plane over the seas. */
private fun DrawScope.drawTransport(at: JourneyMapMath.Point, heading: Float, transport: Transport, k: Float, accent: Color) {
    val dark = Color(0xFF2E1A6E)
    translate(at.x, at.y) {
        scale(density / k, density / k, pivot = Offset.Zero) {
            rotate(heading, pivot = Offset.Zero) {
                when (transport) {
                    Transport.SHIP -> {
                        drawPath(Path().apply { moveTo(-12f, 0f); lineTo(12f, 0f); lineTo(8f, 6f); lineTo(-9f, 6f); close() }, accent)
                        drawRect(accent, topLeft = Offset(-5f, -5f), size = androidx.compose.ui.geometry.Size(9f, 5f))
                        drawRect(dark, topLeft = Offset(-3f, -3.5f), size = androidx.compose.ui.geometry.Size(2f, 2f))
                    }
                    Transport.PLANE -> {
                        drawPath(Path().apply { moveTo(-12f, 0f); lineTo(8f, -2.5f); lineTo(12f, 0f); lineTo(8f, 2.5f); close() }, accent)
                        drawPath(Path().apply { moveTo(-2f, 0f); lineTo(-6f, -9f); lineTo(-2f, -9f); lineTo(4f, 0f); lineTo(-2f, 9f); lineTo(-6f, 9f); close() }, accent)
                    }
                    else -> {
                        drawRoundRect(accent, topLeft = Offset(-12f, -7f), size = androidx.compose.ui.geometry.Size(24f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f))
                        listOf(-9f, -1f, 6f).forEach { x -> drawRect(dark, topLeft = Offset(x, -4.5f), size = androidx.compose.ui.geometry.Size(4.5f, 4f)) }
                        drawCircle(dark, radius = 2f, center = Offset(-7f, 4.5f))
                        drawCircle(dark, radius = 2f, center = Offset(7f, 4.5f))
                    }
                }
            }
        }
    }
}
