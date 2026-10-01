package com.violinjourney.app.feature.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.glass
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.art.JourneySilhouettes
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.journey_cities
import com.violinjourney.app.shared.resources.journey_cities_to
import com.violinjourney.app.shared.resources.journey_countries
import com.violinjourney.app.shared.resources.journey_facts
import com.violinjourney.app.shared.resources.journey_places
import com.violinjourney.app.shared.resources.journey_roads
import com.violinjourney.app.shared.resources.journey_stamp_description
import com.violinjourney.app.shared.resources.journey_stamp_locked_description
import com.violinjourney.app.shared.resources.takt_few
import com.violinjourney.app.shared.resources.takt_icon
import com.violinjourney.app.shared.resources.takt_many
import com.violinjourney.app.shared.resources.takt_one
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Names of the stops are words of the interface, kept in arrays in the order of the route. */
@Composable
fun cityOf(index: Int): String = stringArrayResource(Res.array.journey_cities).getOrElse(index) { "" }

/** «Праги», «Вены»: the city after «до». */
@Composable
fun cityToOf(index: Int): String = stringArrayResource(Res.array.journey_cities_to).getOrElse(index) { "" }

@Composable
fun placeOf(index: Int): String = stringArrayResource(Res.array.journey_places).getOrElse(index) { "" }

@Composable
fun countryOf(index: Int): String = stringArrayResource(Res.array.journey_countries).getOrElse(index) { "" }

@Composable
fun factOf(index: Int): String = stringArrayResource(Res.array.journey_facts).getOrElse(index) { "" }

@Composable
fun roadOf(index: Int): String = stringArrayResource(Res.array.journey_roads).getOrElse(index) { "" }

/** «1 640 тактов», «1 такт», «2 такта». */
@Composable
fun taktsInWords(value: Long): String =
    stringResource(Formats.plural((value % 1_000_000).toInt(), Res.string.takt_one, Res.string.takt_few, Res.string.takt_many), Formats.takts(value))

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

/**
 * «[takt] 1 640»: the sign and a number, in the colour and size of the text around; the sign in [iconColor] where it differs.
 * [merge] — the sign and the number are one stop for a reader. False inside something pressed that speaks for them (the window of
 * «Занятия»): a node merging its own descendants is never merged into its parent, so it would be a stop apart, out of its words.
 */
@Composable
fun TaktAmount(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = LocalContentColor.current,
    icon: Dp = 16.dp,
    iconColor: Color = color,
    merge: Boolean = true,
) {
    val label = stringResource(Res.string.takt_icon)
    Row(
        modifier = if (merge) modifier.semantics(mergeDescendants = true) {} else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TaktIcon(size = icon, tint = iconColor, modifier = Modifier.semantics { contentDescription = label })
        Text(text, color = color, maxLines = 1, style = style.copy(fontFeatureSettings = "tnum"))
    }
}

/**
 * How high a picture of the journey and of the home stands upright (spec 3.36.7, 5.29 R7, «Маленький экран и крупный шрифт»): the
 * pictures give way first — what is [available] under the bar, less the bottom zone ([dock]) and the words under the picture
 * ([WordsUnder]: the name and its line), within [min]…[max]; the zone itself never shrinks. Postcards of the journey and the stop —
 * [PostcardMin]…[PostcardMax], the room of the home — [RoomMin]…[RoomMax]: on 360 × 640 a postcard stays 240, the room is about 260
 * to 285 (gestures or three buttons). Pure, with a test.
 */
object PictureFit {
    val PostcardMin = 180.dp
    val PostcardMax = 240.dp
    val RoomMin = 200.dp
    val RoomMax = 290.dp

    /** The words under a picture that should stand in the first screen with it: the name and the line under it. */
    val WordsUnder = 120.dp

    /** The postcard of the intro and of the arrival lying, beside their words: 240 wide, [PostcardMin] high at most, [LyingMin] at least. */
    val LyingWidth = 240.dp
    val LyingMin = 120.dp

    fun height(available: Dp, dock: Dp, min: Dp, max: Dp): Dp = (available - dock - WordsUnder).coerceIn(min, max)

    /**
     * How high the postcard of the intro and of the arrival stands lying (5.29 R7): beside its words, so nothing of theirs waits under
     * it — [PostcardMin], or the [room] over the fade of the zone where that is less, down to [LyingMin]: on the emulator's 640 × 360
     * the intro has 164 under its bar, the arrival 200.
     */
    fun lying(room: Dp): Dp = room.coerceIn(LyingMin, PostcardMin)
}

/**
 * How large the frame of the stamp stands on its page (spec 3.36.7, 5.29 R7): [FrameMax] — 220 — where there is room, else the
 * [room] left to it over the fade of the zone, down to [FrameMin]; the stamp keeps its share of the frame, 168 of 220
 * ([STAMP_SHARE]). The frame is the picture of the page and gives way first, as the postcards and the room do ([PictureFit]):
 * upright the room is what the words over and under it leave, lying — beside the words — what the zone leaves. Pure, with a test.
 */
object StampFit {
    val FrameMax = 220.dp
    val FrameMin = 140.dp
    const val STAMP_SHARE = 168f / 220f

    fun frame(room: Dp): Dp = room.coerceIn(FrameMin, FrameMax)
}

// A square of glass over a picture and the purse in a pill (spec 5.29 R7, «Стекло на картинах», «Общее»).
private val GlassSquareSize = 48.dp
private val GlassSquareIcon = 24.dp
private val BalanceHeight = 34.dp
private val BalanceSide = 12.dp
private val BalanceSign = 15.dp

/**
 * A square of smoked glass over a picture (spec 5.29 R7): 48, rounded 12, glass 0.72, the [icon] of 24 in the colour of the words —
 * «на весь экран» of the stop and of the home, the way out of their whole screens. Pressed when [onClick] is given, and said by
 * [contentDescription]; the same square as the corner of a video (R5).
 */
@Composable
fun GlassSquare(icon: ImageVector, contentDescription: String?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(GlassSquareSize)
            .clip(AppShapes.S)
            .glass(AppShapes.S)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = contentDescription, size = GlassSquareIcon, tint = MaterialTheme.colorScheme.onSurface) }
}

/**
 * The purse in a pill (spec 5.29 R7, «Общее»): 34, a capsule of the soft accent, fields of 12, the sign of 15 and the number of 14 sp /
 * 800 in the accent, tabular (7.4 : 1) — in the bars of the home, the shop, the stop and «Дома», where prices are compared with it.
 */
@Composable
fun BalancePill(balance: Long, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .heightIn(min = BalanceHeight)
            .clip(CircleShape)
            .background(ViolinTheme.accentSoft)
            .padding(horizontal = BalanceSide),
        contentAlignment = Alignment.Center,
    ) {
        TaktAmount(
            text = Formats.takts(balance),
            color = accent,
            icon = BalanceSign,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
        )
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
    val description = stringResource(if (visited) Res.string.journey_stamp_description else Res.string.journey_stamp_locked_description, city)
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
