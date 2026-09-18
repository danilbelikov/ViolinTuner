package com.example.violintuner.feature.practice.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import com.example.violintuner.core.ui.theme.TrophyPalette
import com.example.violintuner.core.ui.theme.ViolinTheme

/**
 * One trophy at any size (handoff 11t: 24 and 32 dp in the header, 40 in the list, 160 on the
 * gift sheet). [locked] is the trophy not yet given: the same parts, outlined and unfilled, so
 * the difference is one of form and not only of saturation (spec 3.13). Purely decorative:
 * whoever places it says in words what it is.
 */
@Composable
fun TrophyIcon(hours: Int, locked: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val parts = TrophyArt.of(hours) ?: return
    val palette = ViolinTheme.progressColors.trophy
    Canvas(modifier = modifier.size(size)) {
        scale(scale = this.size.minDimension / TrophyArt.VIEW_BOX, pivot = Offset.Zero) {
            parts.forEach { part -> if (locked) drawLocked(part, palette) else drawGiven(part, palette) }
        }
    }
}

private fun DrawScope.drawGiven(part: ArtPart, palette: TrophyPalette) {
    val color = palette.colorOf(part.paint.material)
    when (val paint = part.paint) {
        is ArtPaint.Fill -> {
            drawShape(part.shape, color, Fill, paint.opacity)
            if (paint.material == TrophyMaterial.EBONY) drawShape(part.shape, palette.ebonyEdge, Stroke(TrophyArt.EBONY_EDGE), 1f)
        }
        is ArtPaint.Stroke -> drawShape(part.shape, color, paint.style(paint.width), paint.opacity)
    }
}

private fun DrawScope.drawLocked(part: ArtPart, palette: TrophyPalette) {
    when (val paint = part.paint) {
        is ArtPaint.Fill -> {
            drawShape(part.shape, palette.lockedFill, Fill, 1f)
            drawShape(part.shape, palette.locked, Stroke(TrophyArt.LOCKED_STROKE), 1f)
        }
        is ArtPaint.Stroke ->
            drawShape(part.shape, palette.locked, paint.style(minOf(paint.width, TrophyArt.LOCKED_STROKE_MAX)), 1f)
    }
}

private fun ArtPaint.Stroke.style(width: Float) =
    Stroke(width = width, cap = if (roundCap) StrokeCap.Round else StrokeCap.Butt, join = StrokeJoin.Round)

private fun DrawScope.drawShape(shape: ArtShape, color: Color, style: DrawStyle, alpha: Float) {
    when (shape) {
        is ArtShape.Rect -> drawRoundRect(
            color = color,
            topLeft = Offset(shape.x, shape.y),
            size = Size(shape.width, shape.height),
            cornerRadius = CornerRadius(shape.corner),
            style = style,
            alpha = alpha,
        )
        is ArtShape.Circle -> drawCircle(color, shape.r, Offset(shape.cx, shape.cy), alpha = alpha, style = style)
        is ArtShape.Ellipse -> drawOval(
            color = color,
            topLeft = Offset(shape.cx - shape.rx, shape.cy - shape.ry),
            size = Size(shape.rx * 2, shape.ry * 2),
            style = style,
            alpha = alpha,
        )
        is ArtShape.Polygon -> drawPath(pathOf(listOf(shape.points), closed = true), color, alpha = alpha, style = style)
        is ArtShape.Lines -> drawPath(pathOf(shape.segments, closed = false), color, alpha = alpha, style = style)
    }
}

private fun pathOf(segments: List<List<ArtPoint>>, closed: Boolean) = Path().apply {
    segments.forEach { points ->
        points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        if (closed) close()
    }
}

private fun TrophyPalette.colorOf(material: TrophyMaterial): Color = when (material) {
    TrophyMaterial.WOOD -> wood
    TrophyMaterial.WOOD_LIGHT -> woodLight
    TrophyMaterial.EBONY -> ebony
    TrophyMaterial.EBONY_LIGHT -> ebonyLight
    TrophyMaterial.SILVER -> silver
    TrophyMaterial.SILVER_LIGHT -> silverLight
    TrophyMaterial.GOLD -> gold
    TrophyMaterial.ROSIN -> rosin
    TrophyMaterial.ROSIN_LIGHT -> rosinLight
    TrophyMaterial.ROSIN_GLINT -> rosinGlint
    TrophyMaterial.HAIR -> hair
    TrophyMaterial.PAPER -> paper
    TrophyMaterial.INK -> ink
    TrophyMaterial.VELVET -> velvet
    TrophyMaterial.VELVET_DARK -> velvetDark
    TrophyMaterial.CASE -> case
}
