package com.example.violintuner.feature.journey

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
import com.example.violintuner.R
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.feature.journey.art.Postcard
import com.example.violintuner.feature.journey.art.rememberSceneSeconds

private val Shape = RoundedCornerShape(20.dp)

/**
 * The window into the journey on «Занятия» (spec 3.23, handoff 26h): the postcard of where the
 * player is, how far the next city, and the takts of a practice saved a moment ago. It never asks
 * for anything: enough takts only change the line, the road is taken on the journey's own screen.
 */
@Composable
fun JourneyWindowCard(window: JourneyWindow, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onHomeClick: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val index = JourneyRoute.indexOf(window.current.id)
    val city = cityOf(index)
    Column(modifier.fillMaxWidth().clip(Shape).background(colors.surfaceContainer).clickable(onClickLabel = stringResource(R.string.journey_title), role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(if (compact) 96.dp else 160.dp)) {
            StopPostcard(window.current, description = stringResource(R.string.journey_card_description, city), modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds())
            Text(
                city,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = colors.onSurface, style = MaterialTheme.typography.labelLarge,
            )
            // declared by the type, not by `this`: inside a Box the column's AnimatedVisibility is picked otherwise
            androidx.compose.animation.AnimatedVisibility(
                visible = window.justEarned != null, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Box(Modifier.clip(CircleShape).background(colors.primary).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    TaktAmount(stringResource(R.string.journey_earned, taktsInWords((window.justEarned ?: 0).toLong())), color = colors.onPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), icon = 14.dp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            TaktAmount(Formats.takts(window.balance), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, icon = 14.dp)
        }
        if (next(window) != null) PriceBar(next(window)!!, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
    }
}

private fun next(window: JourneyWindow): Float? = window.next?.let { (window.balance.toFloat() / it.price).coerceIn(0f, 1f) }

/**
 * On the road the window is the city — the road is the main thing, as it was — and home stands by
 * as a narrow card: the cat on the sill and the light of the lamp are enough to remember what
 * else one is saving for (handoff 27g2). At home there is one card, the room itself.
 */
@Composable
fun HomeWindowCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val home = LocalHomeLook.current?.takeIf { it.loaded } ?: return
    val colors = MaterialTheme.colorScheme
    val name = stringResource(R.string.journey_home)
    Box(modifier.fillMaxWidth().height(96.dp).clip(Shape).clickable(onClickLabel = name, role = Role.Button, onClick = onClick)) {
        com.example.violintuner.feature.home.art.HomePicture(
            home, outside = false, mode = com.example.violintuner.feature.home.art.homeModeNow(),
            description = stringResource(R.string.journey_home_card, name), modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(),
        )
        Text(
            name,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 4.dp),
            color = colors.onSurface, style = MaterialTheme.typography.labelLarge,
        )
    }
}
