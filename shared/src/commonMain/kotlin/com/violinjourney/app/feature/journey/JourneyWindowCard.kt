package com.violinjourney.app.feature.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.domain.venue.stopIndex
import com.violinjourney.app.core.ui.components.GlassPlate
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.HomeTexts
import com.violinjourney.app.feature.journey.art.Postcard
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.feature.practice.WindowFit
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.home_card_away
import com.violinjourney.app.shared.resources.home_card_first
import com.violinjourney.app.shared.resources.home_card_home
import com.violinjourney.app.shared.resources.home_card_title
import com.violinjourney.app.shared.resources.journey_card_description
import com.violinjourney.app.shared.resources.journey_earned
import com.violinjourney.app.shared.resources.journey_enough
import com.violinjourney.app.shared.resources.journey_first_takts
import com.violinjourney.app.shared.resources.journey_next_short
import com.violinjourney.app.shared.resources.journey_soon_short
import org.jetbrains.compose.resources.stringResource

/** How the window stands on «Занятия» (spec 3.36.2, 5.29 R2): the screen chooses it by the room it has ([WindowFit]). */
sealed interface WindowLook {
    /**
     * Portrait: the picture [height] high — 148 at most, what is left of the first screen — over the line (56 at least) and, while
     * the takts are short, the bar to the price.
     */
    data class Picture(val height: Dp) : WindowLook

    /**
     * What stands under the picture of [Picture] alone — the line and, while the takts are short, the bar: never shown; the screen
     * measures it to know how high the picture may be, so that the whole window fits the first screen ([WindowFit.picture]).
     */
    data object UnderPicture : WindowLook

    /** Portrait with less than 72 left for a picture: a line of 56 with a still thumbnail of what the window shows. */
    data object Line : WindowLook

    /** Landscape: a row of 96, the picture [pictureWidth] wide on its left, the line on its right. */
    data class Beside(val pictureWidth: Dp) : WindowLook
}

// The window of the home (spec 5.29 R2).
private val Shape = RoundedCornerShape(20.dp)
private val LinePadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
private val CallGap = 10.dp
private val BarPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp)
private val MarkInset = 10.dp
private val MarkHeight = 26.dp
private val MarkSide = 10.dp
private val MarkIcon = 13.dp
private val MarkGap = 5.dp
private val PillHeight = 30.dp
private val PillSide = 11.dp
private val PillIcon = 15.dp
private val Thumb = 40.dp
private val ThumbShape = RoundedCornerShape(10.dp)
private val ThumbLinePadding = PaddingValues(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
private val ThumbGap = 12.dp
private val BesideHeight = 96.dp
private val BesideSide = 16.dp
private val BesideGap = 12.dp
private val BesideWrapGap = 6.dp
private val BesideBarTop = 10.dp
private val CallSize = 15.sp
private val ThumbCallSize = 14.sp
private val CallLineHeight = 20.sp
private const val TABULAR_FIGURES = "tnum"

/**
 * The call and the pill side by side in landscape: the call at the start, the pill at the end of the row as in the portrait line —
 * at least 12 apart, which is what decides that they no longer fit and the pill goes under the call, at the start.
 */
private val BesideRow = object : Arrangement.Horizontal {
    override val spacing: Dp get() = BesideGap

    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
        val density = this
        with(Arrangement.SpaceBetween) { density.arrange(totalSize, sizes, layoutDirection, outPositions) }
    }
}

/**
 * The window on «Занятия» (spec 3.25, 3.27, 3.36.2): where the player is. At home — the room with everything bought, alive; in a
 * city — the city's own postcard, alive (on Live the same city is its hall, seen from the stage). On it, a mark of glass: «⌂ Дома»,
 * or «в пути · Вена · остановка 4 из 16», in one line cut by the width of the picture; «+340» over its end for a few seconds after
 * a practice. Under it one line, 56 at least: how far the next city with the purse in a pill and the bar to the price while the takts are
 * short; «Хватает до Праги — в путь» in accent and an accent pill, without the bar, once they are enough; «Скоро новые города» at
 * the end of what is drawn. Before the first takt ever earned the room lies under the glass: «⌂ Ваша комната», «Первые такты — за
 * первое занятие», no pill and no bar. A tap opens the home, or the journey from a city.
 *
 * [look] — as the screen has room for it: the picture over the line, the line alone with a still thumbnail (no mark there; «+340»
 * takes the place of the pill), or the picture beside the line in landscape. The call takes as many lines as its words need — «в
 * путь» is never cut (3.36.2) — and the line grows with it. For a reader the window is one button: what the picture is, the mark,
 * the call and the takts.
 */
@Composable
fun JourneyWindowCard(window: JourneyWindow, look: WindowLook, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = windowTitle(window)
    val card = modifier
        .fillMaxWidth()
        .clip(Shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
    when (look) {
        is WindowLook.Picture -> Column(card) {
            Box(Modifier.fillMaxWidth().height(look.height)) { PictureLayers(window, title) }
            LineAndBar(window)
        }
        WindowLook.UnderPicture -> Column(card) { LineAndBar(window) }
        WindowLook.Line -> Row(
            modifier = card.heightIn(min = WindowFit.Line).padding(ThumbLinePadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThumbGap),
        ) {
            // what the window shows, standing still: the room at home, the city's postcard on the road
            Box(Modifier.size(Thumb).clip(ThumbShape)) {
                WindowPicture(window, title, Modifier.fillMaxSize(), alive = false)
                if (window.neverEarned) Veil()
            }
            Call(window, ThumbCallSize, Modifier.weight(1f))
            // «+340» stands in the place of the purse for its seconds
            if (!window.neverEarned) {
                AnimatedContent(
                    targetState = window.justEarned,
                    contentAlignment = Alignment.CenterEnd,
                    transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
                    label = "windowPill",
                ) { earned -> if (earned != null) EarnedPill(earned) else BalancePill(window) }
            }
        }
        is WindowLook.Beside -> Beside(window, look.pictureWidth, title, card)
    }
}

/** Under the picture: the line of the call and the purse, and the bar to the price while the takts are short. */
@Composable
private fun LineAndBar(window: JourneyWindow) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WindowFit.Line).padding(LinePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CallGap),
    ) {
        Call(window, CallSize, Modifier.weight(1f))
        if (!window.neverEarned) BalancePill(window)
    }
    if (window.showsBar) PriceBar(window.fraction, Modifier.padding(BarPadding))
}

/**
 * Landscape: the picture on the left, as high as the row — 96, more only when the words need it (a large font) — and on its right
 * the call with the pill beside it at the end of the row, or under it when they do not fit side by side, and the bar under them.
 */
@Composable
private fun Beside(window: JourneyWindow, pictureWidth: Dp, title: String, modifier: Modifier) {
    Layout(
        content = {
            Box { PictureLayers(window, title) }
            Column(
                modifier = Modifier.padding(horizontal = BesideSide),
                verticalArrangement = Arrangement.spacedBy(BesideBarTop, Alignment.CenterVertically),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = BesideRow,
                    verticalArrangement = Arrangement.spacedBy(BesideWrapGap),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Call(window, CallSize, Modifier)
                    if (!window.neverEarned) BalancePill(window)
                }
                if (window.showsBar) PriceBar(window.fraction)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val picture = pictureWidth.roundToPx().coerceAtMost(width)
        val least = BesideHeight.roundToPx()
        val wordsWidth = width - picture
        val words = measurables[1].measure(Constraints(minWidth = wordsWidth, maxWidth = wordsWidth, minHeight = least))
        val height = maxOf(least, words.height)
        val image = measurables[0].measure(Constraints.fixed(picture, height))
        layout(width, height) {
            image.place(0, 0)
            words.place(picture, 0)
        }
    }
}

/** The living picture filling its box, with the glass of the first run, the mark and «+340». */
@Composable
private fun BoxScope.PictureLayers(window: JourneyWindow, title: String) {
    WindowPicture(window, title, Modifier.fillMaxSize(), alive = true)
    if (window.neverEarned) Veil()
    PlaceMark(window, Modifier.align(Alignment.TopStart).padding(MarkInset))
    // over the end of the mark, for its seconds; the number stays on the pill while it fades out
    AnimatedContent(
        targetState = window.justEarned,
        modifier = Modifier.align(Alignment.TopEnd).padding(MarkInset),
        contentAlignment = Alignment.TopEnd,
        transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = false)) },
        label = "windowEarned",
    ) { earned -> if (earned != null) EarnedPill(earned) }
}

/** The room at home, the city's postcard on the road (spec 3.27); [alive] — moving, as a picture on the screen is. */
@Composable
private fun WindowPicture(window: JourneyWindow, description: String, modifier: Modifier, alive: Boolean) {
    val seconds = rememberSceneSeconds(enabled = alive)
    val here = window.here
    if (here is Venue.Hall) {
        // the city as the journey shows it: its main view, outside for most, the workshop and La Scala inside
        val stop = JourneyRoute.stops[here.stopIndex]
        Postcard(stop, description = description, modifier = modifier, inside = stop.views.firstOrNull()?.inside ?: false, seconds = seconds)
    } else {
        StopPostcard(JourneyRoute.stops.first(), description = description, modifier = modifier, seconds = seconds)
    }
}

/**
 * Before the first takt the room is dimmed by the glass of the plates, not greyed: a filter of colour would be a layer over the
 * living picture on every frame (on iOS a whole saveLayer).
 */
@Composable
private fun BoxScope.Veil() {
    Box(Modifier.matchParentSize().background(ViolinTheme.glass))
}

/** «⌂ Дома», «⌂ Ваша комната», «в пути · Вена · остановка 4 из 16»: light words on glass, one line cut by the picture's width. */
@Composable
private fun PlaceMark(window: JourneyWindow, modifier: Modifier) {
    val here = window.here
    val text = when {
        window.neverEarned -> stringResource(Res.string.home_card_first)
        here is Venue.Hall -> stringResource(Res.string.home_card_away, cityOf(here.stopIndex), here.stopIndex, JourneyRoute.stops.lastIndex)
        else -> stringResource(Res.string.home_card_home)
    }
    GlassPlate(modifier.heightIn(min = MarkHeight), contentPadding = PaddingValues(horizontal = MarkSide)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MarkGap)) {
            if (here !is Venue.Hall) AppIcon(AppIcons.House, contentDescription = null, size = MarkIcon)
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/**
 * The call of the line: how far the next city, «Хватает до Праги — в путь», the end of the road, the first run. As many lines as its
 * words need, never cut: at 360 dp and a font of 1,3 «Хватает до Кремоны — в путь» takes three beside the pill, and a cut would take
 * «в путь» away (3.36.2: it is never broken).
 */
@Composable
private fun Call(window: JourneyWindow, size: TextUnit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val next = window.next
    val nextCity = JourneyRoute.indexOf(window.current.id) + 1
    val text = when {
        window.neverEarned -> stringResource(Res.string.journey_first_takts)
        next == null -> stringResource(Res.string.journey_soon_short)
        window.enough -> stringResource(Res.string.journey_enough, cityToOf(nextCity))
        else -> stringResource(Res.string.journey_next_short, cityToOf(nextCity), Formats.takts(window.missing))
    }
    val color = when {
        window.neverEarned -> colors.onSurfaceVariant
        window.enough -> colors.primary
        else -> colors.onSurface
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = size,
            lineHeight = CallLineHeight,
            fontWeight = if (window.enough && !window.neverEarned) FontWeight.Bold else FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
}

/** The purse: on the second surface, the sign in accent; enough for the next city — the accent itself. */
@Composable
private fun BalancePill(window: JourneyWindow) {
    val colors = MaterialTheme.colorScheme
    if (window.enough) {
        TaktPill(Formats.takts(window.balance), ground = colors.primary, color = colors.onPrimary, sign = colors.onPrimary)
    } else {
        TaktPill(Formats.takts(window.balance), ground = colors.surfaceContainerHigh, color = colors.onSurface, sign = colors.primary)
    }
}

/** «+340» — the takts of the practice just saved (spec 3.31), on the accent, of the purse's size. */
@Composable
private fun EarnedPill(earned: Int) {
    val colors = MaterialTheme.colorScheme
    TaktPill(stringResource(Res.string.journey_earned, Formats.takts(earned.toLong())), ground = colors.primary, color = colors.onPrimary, sign = colors.onPrimary)
}

@Composable
private fun TaktPill(text: String, ground: Color, color: Color, sign: Color) {
    Box(
        modifier = Modifier.heightIn(min = PillHeight).clip(CircleShape).background(ground).padding(horizontal = PillSide),
        contentAlignment = Alignment.Center,
    ) {
        TaktAmount(
            text = text,
            color = color,
            iconColor = sign,
            icon = PillIcon,
            // the window is one button: its words take the takts in, a pill merging its own would be a stop apart
            merge = false,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** «Дом · Изба» or «Путешествие: Вена»: what the picture is, and where a tap leads. */
@Composable
private fun windowTitle(window: JourneyWindow): String {
    val here = window.here
    if (here is Venue.Hall) return stringResource(Res.string.journey_card_description, cityOf(here.stopIndex))
    val home = LocalHomeLook.current
    val house = home?.let { HomeTexts.houseNames[HomeRules.house(it)] }?.let { stringResource(it) }.orEmpty()
    return stringResource(Res.string.home_card_title, house)
}

/** The bar to the price — only while the takts are short: a full bar says nothing the call does not (spec 3.36.2). */
private val JourneyWindow.showsBar: Boolean get() = !neverEarned && next != null && !enough

private val JourneyWindow.fraction: Float get() = next?.let { (balance.toFloat() / it.price).coerceIn(0f, 1f) } ?: 0f
