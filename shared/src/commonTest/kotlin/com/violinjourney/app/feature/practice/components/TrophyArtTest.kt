package com.violinjourney.app.feature.practice.components

import com.violinjourney.app.core.domain.progress.ProgressConfig
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class TrophyArtTest {
    @Test
    fun `every mark of the config has a drawing and there are no spare ones`() {
        assertEquals(ProgressConfig().trophyHours.toSet(), TrophyArt.hours)
    }

    @Test
    fun `every drawing stays inside its box — stroke included`() {
        TrophyArt.hours.forEach { hours ->
            TrophyArt.of(hours)!!.forEach { part ->
                val half = (part.paint as? ArtPaint.Stroke)?.width?.div(2) ?: 0f
                val (xs, ys) = when (val shape = part.shape) {
                    is ArtShape.Rect -> listOf(shape.x, shape.x + shape.width) to listOf(shape.y, shape.y + shape.height)
                    is ArtShape.Circle -> listOf(shape.cx - shape.r, shape.cx + shape.r) to listOf(shape.cy - shape.r, shape.cy + shape.r)
                    is ArtShape.Ellipse -> listOf(shape.cx - shape.rx, shape.cx + shape.rx) to listOf(shape.cy - shape.ry, shape.cy + shape.ry)
                    is ArtShape.Polygon -> shape.points.map { it.x } to shape.points.map { it.y }
                    is ArtShape.Lines -> shape.segments.flatten().map { it.x } to shape.segments.flatten().map { it.y }
                }
                (xs + ys).forEach { value ->
                    assertTrue(value - half >= 0f && value + half <= TrophyArt.VIEW_BOX, "$hours h: $part")
                }
            }
        }
    }

    @Test
    fun `open lines are never filled`() {
        TrophyArt.hours.flatMap { TrophyArt.of(it)!! }
            .filter { it.shape is ArtShape.Lines }
            .forEach { assertTrue(it.paint is ArtPaint.Stroke) }
    }
}
