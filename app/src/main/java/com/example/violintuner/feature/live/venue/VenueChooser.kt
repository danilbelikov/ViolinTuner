package com.example.violintuner.feature.live.venue

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.domain.venue.VenueAccess
import com.example.violintuner.core.domain.venue.VenueEntry
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.home.HomeTexts
import com.example.violintuner.feature.home.art.homeModeNow
import com.example.violintuner.feature.journey.art.JourneySilhouettes
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.drawPrepared
import com.example.violintuner.feature.journey.cityOf
import com.example.violintuner.feature.journey.cityToOf
import com.example.violintuner.feature.journey.countryOf
import com.example.violintuner.feature.journey.placeOf
import com.example.violintuner.feature.live.components.LiveDimens

/** «Дома», «Вена · Золотой зал»: what a place is called on Live and in the list. */
@Composable
fun venueName(venue: Venue): String = when (venue) {
    Venue.Home -> stringResource(R.string.venue_home)
    is Venue.Hall -> stringResource(R.string.venue_pair, cityOf(venue.stopIndex), hallOf(venue.stopIndex))
}

@Composable
private fun hallOf(index: Int): String = stringArrayResource(R.array.venue_halls).getOrElse(index) { "" }

/**
 * The quiet label of the place on Live (spec 3.27, handoff 29k): a smoked-glass pill with a house or
 * a hall front and the name. A tap opens «Где играть»; while a recording runs it does not answer.
 */
@Composable
fun VenueLabel(venue: Venue, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = ViolinTheme.venueColors
    val shape = RoundedCornerShape(LiveDimens.PlaceLabelCorner)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(LiveDimens.PlaceRowHeight)
            .clip(shape)
            .background(glass.labelFill, shape)
            .border(LiveDimens.PlaceLabelEdge, glass.labelEdge, shape)
            .clickable(enabled = enabled, onClickLabel = stringResource(R.string.venue_choose), role = Role.Button, onClick = onClick)
            .padding(horizontal = LiveDimens.PlaceLabelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.PlaceLabelGap),
    ) {
        AppIcon(if (venue is Venue.Hall) AppIcons.Theatre else AppIcons.House, contentDescription = null, size = LiveDimens.PlaceLabelIcon, tint = ink)
        Text(venueName(venue), color = ink, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * «Где играть» (spec 3.27, handoff 29k2): the room first, the halls of the reached stops as they look
 * on Live, the next stop in a dashed frame with its silhouette, the rest of the road still ahead.
 * Choosing a place takes the player there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueSheet(current: Venue, menu: List<VenueEntry>, home: HomeState?, onChoose: (Venue) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.venue_sheet_title), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                items(menu, key = { it.venue.stopIndex }) { entry ->
                    VenueRow(entry, selected = entry.venue == current, home = home) { onChoose(entry.venue) }
                }
            }
        }
    }
}

@Composable
private fun VenueRow(entry: VenueEntry, selected: Boolean, home: HomeState?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val open = entry.access == VenueAccess.OPEN
    val index = entry.venue.stopIndex
    val background = when {
        selected -> colors.primary.copy(alpha = SELECTED_FILL)
        open -> colors.surfaceContainer
        else -> colors.surfaceContainer.copy(alpha = AHEAD_FILL)
    }
    var row = Modifier.fillMaxWidth().clip(shape).background(background, shape)
    if (selected) row = row.border(1.5.dp, colors.primary, shape)
    if (entry.access == VenueAccess.NEXT) row = row.dashedBorder(colors.outlineVariant)
    if (open) row = row.clickable(role = Role.Button, onClick = onClick)
    Row(row.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(84.dp, 54.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHigh)) {
            if (open) VenueThumb(entry.venue, home, Modifier.size(84.dp, 54.dp)) else Silhouette(JourneyRoute.stops[index].id, Modifier.size(84.dp, 54.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(venueName(entry.venue), color = if (open) colors.onSurface else colors.onSurfaceVariant, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = when (entry.access) {
                    VenueAccess.OPEN -> if (entry.venue == Venue.Home) roomSub(home) else hallSub(index)
                    VenueAccess.NEXT -> stringResource(R.string.venue_next, cityToOf(index))
                    VenueAccess.AHEAD -> stringResource(R.string.venue_ahead)
                },
                color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Text(stringResource(R.string.venue_here), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/** The place of a hall («Музикферайн, Золотой зал»), or its country where the place is the hall's own name («Рудольфинум»). */
@Composable
private fun hallSub(index: Int): String {
    val place = placeOf(index)
    return if (place.equals(hallOf(index), ignoreCase = true)) countryOf(index) else place
}

/** «съёмная комната · вечер»: which home, and the time of day it is drawn at. */
@Composable
private fun roomSub(home: HomeState?): String {
    val house = HomeTexts.houseNames[home?.let(HomeRules::house) ?: HomeRules.house(HomeState.EMPTY)]?.let { stringResource(it) }.orEmpty()
    val time = stringResource(if (homeModeNow() == SceneMode.DAY) R.string.journey_mode_day else R.string.journey_mode_evening)
    return stringResource(R.string.venue_pair, house, time)
}

/** A place as it looks on Live, small: the room at the window and the music stand, a hall at its tiers. Lit and still. */
@Composable
fun VenueThumb(venue: Venue, home: HomeState?, modifier: Modifier = Modifier) {
    val picture = rememberVenuePicture(venue, home)
    Canvas(modifier) {
        val shown = picture ?: return@Canvas
        val (left, top, width) = if (venue is Venue.Hall) THUMB_HALL else THUMB_ROOM
        val k = size.width / width
        translate(-left * k, -top * k) { drawPrepared(shown, k) }
    }
}

@Composable
private fun Silhouette(stopId: String, modifier: Modifier) {
    val ink = ViolinTheme.venueColors.muted
    val paths = remember(stopId) { JourneySilhouettes.paths[stopId].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        // the silhouettes live on a grid of 200 × 120, their feet at 110
        val k = size.height / SILHOUETTE_HIGH
        translate((size.width - SILHOUETTE_WIDE * k) / 2, 0f) { scale(k, k, pivot = Offset.Zero) { paths.forEach { drawPath(it, ink) } } }
    }
}

/** The frame of the next stop: dashed, like the circle of a stamp not yet put in the passport. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Where the small pictures look (handoff 29k2): the room at its window and stand, a hall at its tiers — left, top, width in units of the grid. */
private val THUMB_ROOM = Triple(20f, -20f, 372f)
private val THUMB_HALL = Triple(20f, -60f, 372f)
private const val SILHOUETTE_WIDE = 200f
private const val SILHOUETTE_HIGH = 120f
private const val SELECTED_FILL = 0.12f
private const val AHEAD_FILL = 0.55f
