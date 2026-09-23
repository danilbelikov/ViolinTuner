package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.repertoire.scale.EngravedSystem
import com.violinjourney.app.core.domain.repertoire.scale.Engraving
import com.violinjourney.app.core.domain.repertoire.scale.NotationMetrics
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.domain.repertoire.scale.ScaleEngraver
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales

/** How big a staff space is where the notation stands (handoff 24h7, «sp по месту»). */
object NotationSizes {
    val Card = 7.5.dp
    val CardLandscape = 6.5.dp
    val Preview = 7.dp
    val Stand = 10.dp
    val Tile = 4.5.dp
}

/**
 * A scale as notation (spec 3.22, handoff 24h): as wide as it is given, as tall as its systems.
 * Everything that is decided — which notes, where, with what sign — comes from [ScaleEngraver];
 * this only draws. [maxSystems] folds a long scale: the first systems stand, the rest wait.
 */
@Composable
fun ScaleNotation(scale: Scale, space: Dp, ink: Color, modifier: Modifier = Modifier, maxSystems: Int? = null, name: String = "") {
    val description = stringResource(R.string.scale_notes_description, name)
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthSp = maxWidth.value / space.value
        val engraving = remember(scale, widthSp) { ScaleEngraver.engrave(scale, widthSp) }
        val shown = if (maxSystems != null) engraving.systems.take(maxSystems) else engraving.systems
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(space * engraving.heightOf(shown.size))
                .semantics { contentDescription = description },
        ) {
            val sp = space.toPx()
            val ottavaStyle = TextStyle(color = ink, fontSize = (NotationMetrics.OTTAVA_TEXT * space.value).sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            shown.forEachIndexed { index, system ->
                drawSystem(engraving, index, system, sp, ink) { x, y ->
                    val label = textMeasurer.measure("8va", ottavaStyle)
                    drawText(label, topLeft = Offset(x, y - label.size.height))
                    label.size.width.toFloat()
                }
            }
        }
    }
}

/** How many systems the scale takes at this width: what «ещё N систем» counts. */
fun systemCount(scale: Scale, widthDp: Float, space: Dp): Int = ScaleEngraver.engrave(scale, widthDp / space.value).systems.size

private fun DrawScope.drawSystem(engraving: Engraving, index: Int, system: EngravedSystem, sp: Float, ink: Color, drawOttavaLabel: (x: Float, y: Float) -> Float) {
    val m = NotationMetrics
    fun y(position: Int) = engraving.y(index, position) * sp
    val left = m.PAD * sp
    val right = (engraving.widthSp - m.PAD) * sp
    for (line in 0..m.TOP_LINE step 2) drawLine(ink, Offset(left, y(line)), Offset(right, y(line)), strokeWidth = m.LINE * sp)

    glyph(NotationGlyphs.clef, (m.PAD + m.CLEF_X) * sp, y(m.CLEF_LINE), sp, ink, stroke = NotationGlyphs.CLEF_STROKE)
    engraving.signs.forEach { sign -> glyph(if (sign.sharp) NotationGlyphs.sharp else NotationGlyphs.flat, sign.x * sp, y(sign.position), sp, ink) }

    var highest = y(m.TOP_LINE)
    system.notes.forEach { note ->
        val x = note.x * sp
        val noteY = y(note.position)
        note.accidental?.let { glyph(NotationGlyphs.accidental(it), (note.x - m.ACCIDENTAL_BEFORE) * sp, noteY, sp, ink) }
        note.ledgers.forEach { ledger ->
            val half = (m.HEAD_HALF_WIDTH + m.LEDGER_EXTENT) * sp
            drawLine(ink, Offset(x - half, y(ledger)), Offset(x + half, y(ledger)), strokeWidth = m.LEDGER * sp)
        }
        rotate(NotationGlyphs.HEAD_TILT_DEGREES, pivot = Offset(x, noteY)) {
            drawOval(ink, topLeft = Offset(x - NotationGlyphs.HEAD_A * sp, noteY - NotationGlyphs.HEAD_B * sp), size = Size(2 * NotationGlyphs.HEAD_A * sp, 2 * NotationGlyphs.HEAD_B * sp))
        }
        val stemX = x + (if (note.stemUp) m.STEM_OFFSET else -m.STEM_OFFSET) * sp
        val direction = if (note.stemUp) -1f else 1f
        val stemEnd = noteY + direction * m.STEM_LENGTH * sp
        drawLine(ink, Offset(stemX, noteY + direction * m.STEM_INSET * sp), Offset(stemX, stemEnd), strokeWidth = m.STEM * sp)
        highest = minOf(highest, if (note.stemUp) stemEnd else noteY - sp)
    }

    system.ottavas.forEach { span ->
        // above the highest stem of the system, but never out of the system's own room
        val lineY = maxOf(highest - OTTAVA_CLEARANCE * sp, (engraving.top(index) + OTTAVA_MIN_TOP) * sp)
        val from = (span.fromX - OTTAVA_LABEL_LEFT) * sp
        val labelWidth = drawOttavaLabel(from, lineY + OTTAVA_LABEL_DROP * sp)
        val lineFrom = from + labelWidth + OTTAVA_LABEL_GAP * sp
        val lineTo = maxOf(span.toX * sp, lineFrom + m.OTTAVA_DASH * sp)
        val dash = PathEffect.dashPathEffect(floatArrayOf(m.OTTAVA_DASH * sp, m.OTTAVA_DASH_GAP * sp))
        drawLine(ink, Offset(lineFrom, lineY), Offset(lineTo, lineY), strokeWidth = m.LINE * sp, pathEffect = dash)
        drawLine(ink, Offset(lineTo, lineY), Offset(lineTo, lineY + m.OTTAVA_HOOK * sp), strokeWidth = m.LINE * sp)
    }

    if (system.last) {
        // the end of the scale: a thin and a thick line against the right edge of the staff
        val thick = m.BAR_THICK * sp
        val thin = m.BAR_THIN * sp
        drawLine(ink, Offset(right - thick / 2, y(m.TOP_LINE)), Offset(right - thick / 2, y(0)), strokeWidth = thick)
        val thinX = right - thick - m.BAR_GAP * sp - thin / 2
        drawLine(ink, Offset(thinX, y(m.TOP_LINE)), Offset(thinX, y(0)), strokeWidth = thin)
    }
}

private const val OTTAVA_CLEARANCE = 0.6f
private const val OTTAVA_MIN_TOP = 1.4f
private const val OTTAVA_LABEL_LEFT = 0.3f
private const val OTTAVA_LABEL_DROP = 0.45f
private const val OTTAVA_LABEL_GAP = 0.3f

/** A sign of [NotationGlyphs] at its anchor: the path is in staff spaces, the canvas in pixels. */
private fun DrawScope.glyph(path: Path, x: Float, y: Float, sp: Float, ink: Color, stroke: Float? = null) {
    translate(x, y) {
        scale(sp, sp, pivot = Offset.Zero) {
            if (stroke != null) {
                drawPath(path, ink, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, ink)
            }
        }
    }
}

/**
 * The tile of a scale in a list (handoff 24c): the clef with the key signature on a short staff —
 * three sharps are known from afar sooner than a first bar in miniature, and it is more honest
 * than a letter, which the title already says.
 */
@Composable
fun KeySignatureTile(spec: ScaleSpec, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurfaceVariant
    val fifths = Scales.fifthsOf(spec.tonic, spec.accidental, spec.kind.mode)
    Canvas(modifier = modifier.background(colors.surfaceContainerHigh, RoundedCornerShape(corner))) {
        val m = NotationMetrics
        // the staff is as small as it takes for seven signs to fit the tile
        val neededSp = TILE_PAD + m.CLEF_WIDTH + m.CLEF_GAP + m.SIGN_FIRST + kotlin.math.abs(fifths).coerceAtLeast(1) * m.SIGN_STEP + TILE_PAD
        val sp = minOf(NotationSizes.Tile.toPx(), size.width / neededSp)
        val bottom = size.height / 2 + m.STAFF_HEIGHT / 2 * sp
        fun y(position: Int) = bottom - position / 2f * sp
        for (line in 0..m.TOP_LINE step 2) drawLine(ink, Offset(TILE_PAD * sp, y(line)), Offset(size.width - TILE_PAD * sp, y(line)), strokeWidth = m.LINE * sp)
        glyph(NotationGlyphs.clef, (TILE_PAD + m.CLEF_X) * sp, y(m.CLEF_LINE), sp, ink, stroke = NotationGlyphs.CLEF_STROKE)
        val sharp = fifths > 0
        val order = if (sharp) Scales.SHARP_ORDER else Scales.FLAT_ORDER
        val positions = if (sharp) m.SHARP_POSITIONS else m.FLAT_POSITIONS
        order.take(kotlin.math.abs(fifths)).forEachIndexed { index, letter ->
            val x = TILE_PAD + m.CLEF_WIDTH + m.CLEF_GAP + m.SIGN_FIRST + index * m.SIGN_STEP
            glyph(if (sharp) NotationGlyphs.sharp else NotationGlyphs.flat, x * sp, y(positions.getValue(letter)), sp, ink)
        }
    }
}

private const val TILE_PAD = 0.5f
