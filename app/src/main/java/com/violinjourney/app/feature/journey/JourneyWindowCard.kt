package com.violinjourney.app.feature.journey

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.domain.venue.stopIndex
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.home.HomeTexts
import com.violinjourney.app.feature.journey.art.Postcard
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds

private val Shape = RoundedCornerShape(20.dp)

/**
 * The window on «Занятия» (spec 3.25, handoff 28a; spec 3.27): where the player is. At home — the
 * room with everything bought, alive; in a city — the city's own postcard, alive, with a quiet note
 * «в пути · Вена · остановка 4 из 16» (on Live the same city is its hall, seen from the stage). Of
 * the road it keeps what matters: how far the next city and the bar, the purse in a pill, «+340»
 * after a practice. A tap opens the home, or the journey from a city.
 */
@Composable
fun JourneyWindowCard(window: JourneyWindow, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val index = JourneyRoute.indexOf(window.current.id)
    val here = window.here
    val home = LocalHomeLook.current
    val house = home?.let { HomeTexts.houseNames[HomeRules.house(it)] }?.let { stringResource(it) }.orEmpty()
    val title = if (here is Venue.Hall) stringResource(R.string.journey_card_description, cityOf(here.stopIndex)) else stringResource(R.string.home_card_title, house)
    Column(modifier.fillMaxWidth().clip(Shape).background(colors.surfaceContainer).clickable(onClickLabel = title, role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(if (compact) 96.dp else 160.dp)) {
            if (here is Venue.Hall) {
                // the city as the journey shows it: its main view, outside for most, the workshop and La Scala inside
                val stop = JourneyRoute.stops[here.stopIndex]
                Postcard(stop, description = title, modifier = Modifier.fillMaxSize(), inside = stop.views.firstOrNull()?.inside ?: false, seconds = rememberSceneSeconds())
            } else {
                StopPostcard(JourneyRoute.stops.first(), description = title, modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds())
            }
            if (here is Venue.Hall) {
                Text(
                    stringResource(R.string.home_card_away, cityOf(here.stopIndex), here.stopIndex, JourneyRoute.stops.lastIndex),
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                )
            }
            // declared by the type, not by `this`: inside a Box the column's AnimatedVisibility is picked otherwise
            androidx.compose.animation.AnimatedVisibility(
                visible = window.justEarned != null, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            ) {
                Box(Modifier.clip(CircleShape).background(colors.primary).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    TaktAmount(stringResource(R.string.journey_earned, Formats.takts((window.justEarned ?: 0).toLong())), color = colors.onPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), icon = 14.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val next = window.next
            Text(
                text = when {
                    next == null -> stringResource(R.string.journey_soon_short)
                    window.canDepart -> stringResource(R.string.journey_enough, cityToOf(index + 1))
                    else -> stringResource(R.string.journey_next_short, cityToOf(index + 1), Formats.takts(window.missing))
                },
                modifier = Modifier.weight(1f),
                color = if (window.canDepart) colors.primary else colors.onSurface,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.clip(CircleShape).background(if (window.canDepart) colors.primary else colors.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp)) {
                TaktAmount(Formats.takts(window.balance), color = if (window.canDepart) colors.onPrimary else colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, icon = 14.dp)
            }
        }
        window.next?.let { PriceBar((window.balance.toFloat() / it.price).coerceIn(0f, 1f), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) }
    }
}
