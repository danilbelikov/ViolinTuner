package com.example.violintuner.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.journey.JourneyExtra
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.feature.journey.art.Postcard
import com.example.violintuner.feature.journey.art.SceneMode
import java.time.ZoneId

private val MaxContentWidth = 560.dp
private val PostcardShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val StickerPaper = Color(0xFFF1EEE6)
private val StickerInk = Color(0xFF2E1A6E)

/** A stop the player has been to (handoff 26c): its postcard in the views that are open, its fact, and the extras. */
@Composable
fun StopScreen(state: StopState, onIntent: (StopIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val city = cityOf(state.index)
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(city, onBack = { onIntent(StopIntent.BackClicked) }) {
            TaktAmount(Formats.takts(state.balance), color = colors.primary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), icon = 18.dp)
        }
        if (state.loading) return@Column
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StopCard(state, city, 180.dp, onIntent)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StopWords(state)
                        Extras(state, onIntent)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StopCard(state, city, 240.dp, onIntent)
                    StopWords(state)
                    Extras(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun StopCard(state: StopState, city: String, height: Dp, onIntent: (StopIntent) -> Unit) {
    Postcard(
        state.stop, description = stringResource(R.string.journey_card_description, city),
        modifier = Modifier.fillMaxWidth().height(height).clip(PostcardShape),
        mode = if (state.day) SceneMode.DAY else SceneMode.EVENING, inside = state.inside,
    )
    // Only what is open is offered: a chip that does nothing would be a locked door in the player's face
    if (state.dayUnlocked || state.secondViewUnlocked) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.dayUnlocked) {
                FilterChip(selected = !state.day, onClick = { onIntent(StopIntent.DaySelected(false)) }, label = { Text(stringResource(R.string.journey_mode_evening)) })
                FilterChip(selected = state.day, onClick = { onIntent(StopIntent.DaySelected(true)) }, label = { Text(stringResource(R.string.journey_mode_day)) })
            }
            if (state.secondViewUnlocked) {
                FilterChip(selected = !state.inside, onClick = { onIntent(StopIntent.InsideSelected(false)) }, label = { Text(stringResource(R.string.journey_mode_outside)) })
                FilterChip(selected = state.inside, onClick = { onIntent(StopIntent.InsideSelected(true)) }, label = { Text(stringResource(R.string.journey_mode_inside)) })
            }
        }
    }
}

@Composable
private fun StopWords(state: StopState) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = listOf(placeOf(state.index), countryOf(state.index)).filter { it.isNotEmpty() }.joinToString(" · "),
        color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
    )
    state.arrivedAtEpochMs?.takeIf { state.index > 0 }?.let {
        Text(stringResource(R.string.journey_stop_meta, state.index, state.totalStops, Formats.dayAndMonth(it, ZoneId.systemDefault())), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    Text(factOf(state.index), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Extras(state: StopState, onIntent: (StopIntent) -> Unit) {
    if (state.offers.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Text(stringResource(R.string.journey_extras), modifier = Modifier.padding(top = 8.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    Column(Modifier.fillMaxWidth().clip(CardShape).background(colors.surfaceContainer)) {
        state.offers.forEach { offer ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when (offer.extra) {
                            JourneyExtra.SECOND_TIME -> R.string.journey_extra_time
                            JourneyExtra.SECOND_VIEW -> R.string.journey_extra_view
                            JourneyExtra.SOUVENIR -> R.string.journey_extra_souvenir
                        },
                    ),
                    modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge,
                )
                if (offer.bought) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppIcon(AppIcons.Check, contentDescription = null, size = 18.dp, tint = colors.primary)
                        Text(stringResource(R.string.journey_extra_bought), color = colors.primary, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    FilledTonalButton(onClick = { onIntent(StopIntent.BuyClicked(offer.extra)) }, enabled = offer.affordable, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        TaktAmount(Formats.takts(offer.price.toLong()), style = MaterialTheme.typography.labelLarge, icon = 14.dp)
                    }
                }
            }
        }
    }
}

/** The passport (handoff 26g): a stamp for every stop of the route, those ahead as dashed outlines. */
@Composable
fun PassportScreen(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.journey_passport), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            if (!state.loading) Text(stringResource(R.string.journey_passport_count, state.currentIndex, state.totalStops), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (state.loading) return@Column
        val stops = JourneyRoute.stops.drop(1)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(stops, key = { _, it -> it.id }) { position, stop ->
                val index = position + 1
                val visit = state.visited.firstOrNull { it.stop.id == stop.id }
                val city = cityOf(index)
                Column(
                    modifier = Modifier
                        .clip(CardShape)
                        .then(if (visit != null) Modifier.clickable(onClickLabel = city, role = Role.Button) { onIntent(JourneyIntent.StopClicked(stop.id)) } else Modifier)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        StampView(
                            stopId = stop.id, index = index, visited = visit != null, city = city,
                            date = visit?.let { Formats.dayAndMonth(it.arrivedAtEpochMs, ZoneId.systemDefault()) }.orEmpty(),
                            modifier = Modifier.rotate(if (visit != null) STAMP_TILTS[index % STAMP_TILTS.size] else 0f),
                        )
                        if (visit?.souvenir == true) Sticker(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 2.dp))
                    }
                    if (visit == null) {
                        Text(
                            text = if (stop.available) city else stringResource(R.string.journey_passport_soon),
                            modifier = Modifier.padding(top = 4.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val STAMP_TILTS = listOf(-6f, 4f, -3f, 7f, -8f, 2f)

/** The souvenir: a paper sticker beside the stamp. */
@Composable
private fun Sticker(modifier: Modifier = Modifier) {
    Box(modifier.rotate(10f).size(width = 20.dp, height = 26.dp).clip(RoundedCornerShape(2.dp)).background(StickerPaper), contentAlignment = Alignment.Center) {
        AppIcon(AppIcons.NoteOne, contentDescription = null, size = 14.dp, tint = StickerInk)
    }
}

/** The whole route (handoff 26e): drag and pinch; a tap on a city already reached opens it. */
@Composable
fun MapScreen(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface)) {
        JourneyTopBar(stringResource(R.string.journey_map), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            if (!state.loading) Text(stringResource(R.string.journey_passport_count, state.currentIndex, state.totalStops), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (state.loading) return@Column
        JourneyMapCanvas(
            reached = state.currentIndex, interactive = true, modifier = Modifier.fillMaxSize(),
            onStopTap = { index -> onIntent(JourneyIntent.StopClicked(JourneyRoute.stops[index].id)) },
        )
    }
}
