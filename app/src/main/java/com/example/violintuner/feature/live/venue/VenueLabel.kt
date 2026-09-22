package com.example.violintuner.feature.live.venue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.venue.Venue
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.journey.cityOf
import com.example.violintuner.feature.live.components.LiveDimens

/** «Дома», «Вена · Золотой зал»: what a place is called on Live. */
@Composable
fun venueName(venue: Venue): String = when (venue) {
    Venue.Home -> stringResource(R.string.venue_home)
    is Venue.Hall -> stringResource(R.string.venue_pair, cityOf(venue.stopIndex), hallOf(venue.stopIndex))
}

@Composable
private fun hallOf(index: Int): String = stringArrayResource(R.array.venue_halls).getOrElse(index) { "" }

/**
 * The quiet label of the place on Live (spec 3.27, handoff 29k): smoked glass with a house or a hall
 * front and the name. It only says where the player is: a place is chosen on the journey, not here —
 * so it has no frame and does not answer a tap.
 */
@Composable
fun VenueLabel(venue: Venue, modifier: Modifier = Modifier) {
    val glass = ViolinTheme.venueColors
    val shape = RoundedCornerShape(LiveDimens.PlaceLabelCorner)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(LiveDimens.PlaceRowHeight)
            .background(glass.labelFill, shape)
            .padding(horizontal = LiveDimens.PlaceLabelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.PlaceLabelGap),
    ) {
        AppIcon(if (venue is Venue.Hall) AppIcons.Theatre else AppIcons.House, contentDescription = null, size = LiveDimens.PlaceLabelIcon, tint = ink)
        Text(venueName(venue), color = ink, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
