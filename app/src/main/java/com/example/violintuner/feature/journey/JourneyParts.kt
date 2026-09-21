package com.example.violintuner.feature.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.feature.journey.art.JourneySilhouettes

/** Names of the stops are words of the interface, kept in arrays in the order of the route. */
@Composable
fun cityOf(index: Int): String = stringArrayResource(R.array.journey_cities).getOrElse(index) { "" }

/** «Праги», «Вены»: the city after «до». */
@Composable
fun cityToOf(index: Int): String = stringArrayResource(R.array.journey_cities_to).getOrElse(index) { "" }

@Composable
fun placeOf(index: Int): String = stringArrayResource(R.array.journey_places).getOrElse(index) { "" }

@Composable
fun countryOf(index: Int): String = stringArrayResource(R.array.journey_countries).getOrElse(index) { "" }

@Composable
fun factOf(index: Int): String = stringArrayResource(R.array.journey_facts).getOrElse(index) { "" }

@Composable
fun roadOf(index: Int): String = stringArrayResource(R.array.journey_roads).getOrElse(index) { "" }

/** «1 640 тактов», «1 такт», «2 такта». */
@Composable
fun taktsInWords(value: Long): String =
    stringResource(Formats.plural((value % 1_000_000).toInt(), R.string.takt_one, R.string.takt_few, R.string.takt_many), Formats.takts(value))

private const val TAKT_STEM = "M15.5 15.5V9.5C15.5 6.5 18 5.5 19.5 4.2"
private const val TAKT_ARROW = "M18 3.2l2.2 1.4-1.4 2.2"

/** The sign of a takt (handoff 26i3): the head of a note whose stem leaves upwards as a road with a turn — a note that travels. */
@Composable
fun TaktIcon(modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = LocalContentColor.current) {
    val stem = remember { PathParser().parsePathString(TAKT_STEM).toPath() }
    val arrow = remember { PathParser().parsePathString(TAKT_ARROW).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(stem, tint, style = Stroke(2.4f, cap = StrokeCap.Round))
            drawPath(arrow, tint, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            rotate(-20f, pivot = Offset(10.6f, 17.4f)) { drawOval(tint, topLeft = Offset(10.6f - 5.4f, 17.4f - 3.8f), size = Size(10.8f, 7.6f)) }
        }
    }
}

/** «[takt] 1 640»: the sign and a number, in the colour and size of the text around. */
@Composable
fun TaktAmount(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = LocalContentColor.current, icon: Dp = 16.dp) {
    val label = stringResource(R.string.takt_icon)
    Row(modifier = modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TaktIcon(size = icon, tint = color, modifier = Modifier.semantics { contentDescription = label })
        Text(text, color = color, maxLines = 1, style = style.copy(fontFeatureSettings = "tnum"))
    }
}

/** Inks of the stamps (handoff `tokens`): four, in turn — none of them the green or the amber of a zone. */
private val STAMP_INKS = listOf(Color(0xFFC4ADFF), Color(0xFF6FC7C2), Color(0xFFE39AB6), Color(0xFFD9B26B))

fun stampInk(index: Int): Color = STAMP_INKS[index % STAMP_INKS.size]

/**
 * A stamp of the passport (handoff 26g1): a circle with the silhouette of the place, its name and
 * the date. A stop not reached yet is a dashed circle and an outline — told apart by shape, as the
 * trophies are, not by brightness alone.
 */
@Composable
fun StampView(stopId: String, index: Int, visited: Boolean, city: String, date: String, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val colors = MaterialTheme.colorScheme
    val silhouette = remember(stopId) { JourneySilhouettes.paths[stopId].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    val measurer = rememberTextMeasurer()
    val ink = stampInk(index)
    val locked = colors.outlineVariant
    val description = stringResource(if (visited) R.string.journey_stamp_description else R.string.journey_stamp_locked_description, city)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val k = this.size.minDimension / 84f
        scale(k, k, pivot = Offset.Zero) {
            val centre = Offset(42f, 42f)
            if (visited) {
                drawCircle(ink, radius = 39f, center = centre, style = Stroke(2.2f))
                drawCircle(ink.copy(alpha = 0.6f), radius = 34f, center = centre, style = Stroke(0.8f))
            } else {
                drawCircle(locked, radius = 39f, center = centre, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))))
            }
            translate(12f, 20f) {
                scale(0.3f, 0.3f, pivot = Offset.Zero) {
                    silhouette.forEach { path -> if (visited) drawPath(path, ink) else drawPath(path, locked, style = Stroke(4f, join = StrokeJoin.Round)) }
                }
            }
        }
        if (visited) {
            val name = measurer.measure(city.uppercase(), TextStyle(color = ink, fontSize = (8 * k / (density * fontScale)).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), softWrap = false)
            drawText(name, topLeft = Offset((this.size.width - name.size.width) / 2, 56f * k))
            if (date.isNotEmpty()) {
                val day = measurer.measure(date, TextStyle(color = ink.copy(alpha = 0.8f), fontSize = (6 * k / (density * fontScale)).sp), softWrap = false)
                drawText(day, topLeft = Offset((this.size.width - day.size.width) / 2, 68f * k))
            }
        }
    }
}
