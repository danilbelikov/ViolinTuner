package com.example.violintuner.feature.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.JourneyStop
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.feature.home.HomeTexts
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.feature.journey.art.Postcard
import com.example.violintuner.feature.journey.art.rememberSceneSeconds
import java.time.ZoneId

private val TopBarHeight = 56.dp
private val Target = 48.dp
private val MaxContentWidth = 560.dp
private val PostcardHeight = 240.dp
private val PostcardShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(16.dp)
private val LandscapeLeft = 360.dp

/** The journey (spec 3.23, handoff 26a–26f): where the player is, how far the next city, and the road when it is taken. */
@Composable
fun JourneyScreen(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.surface)) {
        if (state.loading) return@Box
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn(tween(JourneyMotion.PHASE_FADE_MS)) togetherWith fadeOut(tween(JourneyMotion.PHASE_FADE_MS)) },
            contentKey = { it::class },
            label = "journeyPhase",
        ) { phase ->
            when (phase) {
                JourneyPhase.Idle -> {
                    // The leg is paid the moment the road starts, so while this content fades out the state
                    // already stands in the next city: keep showing what was there when the screen was calm.
                    val calm = remember { arrayOf(state) }
                    if (state.phase == JourneyPhase.Idle) calm[0] = state
                    IdleContent(calm[0], onIntent)
                }
                JourneyPhase.Intro -> IntroContent(onIntent)
                is JourneyPhase.Road -> RoadContent(phase)
                is JourneyPhase.Arrival -> ArrivalContent(phase, onIntent)
                is JourneyPhase.Stamp -> StampContent(phase, state, onIntent)
            }
        }
    }
}

@Composable
internal fun JourneyTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(Target).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.journey_back), role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        Text(title, modifier = Modifier.weight(1f).padding(start = 4.dp), color = colors.onSurface, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(end = 12.dp)) { trailing() }
    }
}

@Composable
private fun IdleContent(state: JourneyState, onIntent: (JourneyIntent) -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.journey_title), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaktAmount(Formats.takts(state.balance), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), icon = 18.dp)
                // the two that are about the road stay in the bar, as icons; the door home is not of their row (handoff 28c)
                BarIcon(AppIcons.Map, stringResource(R.string.journey_map)) { onIntent(JourneyIntent.MapClicked) }
                BarIcon(AppIcons.Passport, stringResource(R.string.journey_passport)) { onIntent(JourneyIntent.PassportClicked) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(LandscapeLeft).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                        Place(state, postcardHeight = 180.dp, onIntent = onIntent)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Way(state, onIntent)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column { Place(state, PostcardHeight, onIntent) }
                    Way(state, onIntent)
                }
            }
        }
    }
}

/** The postcard of the current stop and what is said about it. */
@Composable
private fun Place(state: JourneyState, postcardHeight: Dp, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val city = cityOf(state.currentIndex)
    Box(
        Modifier.fillMaxWidth().height(postcardHeight).clip(PostcardShape)
            .clickable(onClickLabel = city, role = Role.Button) { onIntent(JourneyIntent.StopClicked(state.current.id)) },
    ) {
        StopPostcard(state.current, description = stringResource(R.string.journey_card_description, city), modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(), homeOutside = true)
        Text(
            text = if (state.currentIndex == 0) stringResource(R.string.journey_stop_home, state.totalStops) else stringResource(R.string.journey_stop_of, state.currentIndex, state.totalStops),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 4.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(city, color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
    val since = state.arrivedAtEpochMs?.takeIf { state.currentIndex > 0 }?.let { stringResource(R.string.journey_since, Formats.dayAndMonth(it, ZoneId.systemDefault())) }
    Text(
        text = listOfNotNull(placeOf(state.currentIndex), countryOf(state.currentIndex).takeIf { it.isNotEmpty() }, since).joinToString(" · "),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(factOf(state.currentIndex), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(14.dp))
    HomeDoor(atHome = state.currentIndex == 0, onClick = { onIntent(JourneyIntent.StopClicked(JourneyRoute.HOME)) })
}

/**
 * The door home (handoff 28c): a card with the room in it — the lamp and the cat are seen — named
 * by what it does. At the stop «Дом» it is the main action of the screen and is filled; in a city
 * the way ahead is the main one and the door is a quiet card.
 */
@Composable
private fun HomeDoor(atHome: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val home = LocalHomeLook.current
    val content = if (atHome) colors.onPrimary else colors.onSurface
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(if (atHome) colors.primary else colors.surfaceContainer).clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(72.dp, 48.dp).clip(RoundedCornerShape(10.dp))) {
            StopPostcard(JourneyRoute.stops.first(), description = "", modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.journey_enter_home), color = content, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            val house = home?.let { HomeTexts.houseNames[HomeRules.house(it)] }?.let { stringResource(it) }
            Text(if (atHome) stringResource(R.string.journey_enter_home_at) else house.orEmpty(), color = content.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        }
        AppIcon(AppIcons.Door, contentDescription = null, tint = content)
    }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(Target).clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        AppIcon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** The way ahead, the postcards behind, and the two doors: the map and the passport. */
@Composable
private fun Way(state: JourneyState, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(CardShape).background(colors.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val next = state.next
        if (next == null) {
            Text(stringResource(R.string.journey_soon), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            TaktAmount(taktsInWords(state.balance), color = colors.onSurface)
        } else {
            val nextIndex = state.currentIndex + 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.journey_next, cityToOf(nextIndex), roadOf(nextIndex)), modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
                TaktAmount(stringResource(R.string.journey_have, Formats.takts(state.balance), Formats.takts(next.price.toLong())), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, icon = 14.dp)
            }
            PriceBar(fraction = (state.balance.toFloat() / next.price).coerceIn(0f, 1f))
            if (state.canDepart) {
                Button(onClick = { onIntent(JourneyIntent.DepartClicked) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    TaktAmount(stringResource(R.string.journey_depart, Formats.takts(next.price.toLong())), style = MaterialTheme.typography.titleMedium, icon = 18.dp)
                }
            } else {
                // Not a disabled button: it says how far, and that is the whole message (handoff 26a)
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    TaktAmount(stringResource(R.string.journey_missing, Formats.takts(state.missing)), style = MaterialTheme.typography.titleMedium, icon = 18.dp)
                }
            }
        }
    }
    val behind = state.visited.dropLast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.journey_passed, state.currentIndex, state.totalStops), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        if (behind.isEmpty()) {
            Text(stringResource(R.string.journey_no_cards), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(behind.asReversed(), key = { _, it -> it.stop.id }) { _, visited ->
                    val city = cityOf(visited.index)
                    Column(Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = city, role = Role.Button) { onIntent(JourneyIntent.StopClicked(visited.stop.id)) }) {
                        StopPostcard(visited.stop, description = city, modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)))
                        Text(city, modifier = Modifier.padding(top = 4.dp, start = 2.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PriceBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(6.dp).drawBehind {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surfaceContainerHighest, cornerRadius = radius)
            if (fraction > 0f) drawRoundRect(colors.primary, size = Size((size.width * fraction).coerceAtLeast(size.height), size.height), cornerRadius = radius)
        },
    )
}

@Composable
private fun IntroContent(onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val first = JourneyRoute.stops[1]
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.journey_title), onBack = { onIntent(JourneyIntent.BackClicked) })
        Column(
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StopPostcard(JourneyRoute.stops.first(), description = cityOf(0), modifier = Modifier.fillMaxWidth().height(PostcardHeight).clip(PostcardShape), seconds = rememberSceneSeconds())
            Text(stringResource(R.string.journey_intro_title), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(stringResource(R.string.journey_intro_text), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            // where takts come from — without the arithmetic: the formula is our secret (spec 3.25)
            Text(stringResource(R.string.journey_intro_takts), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.journey_intro_first, taktsInWords(first.price.toLong())), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onIntent(JourneyIntent.IntroConfirmed) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.journey_intro_start)) }
        }
    }
}

/** The road: the map zoomed onto the leg, the line drawn as the train runs. The one piece of cinema in the app — no touches, no way out. */
@Composable
private fun RoadContent(road: JourneyPhase.Road) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val progress = remember(road) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(road) { if (!reduce) progress.animateTo(1f, tween(road.durationMs, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize()) {
        JourneyMapCanvas(reached = road.fromIndex, road = RoadOnMap(road.fromIndex, progress.value, road.to.transport), modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).clip(CardShape).background(colors.surfaceContainer.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.journey_road, cityOf(road.fromIndex), cityOf(road.fromIndex + 1)), color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(roadOf(road.fromIndex + 1), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ArrivalContent(arrival: JourneyPhase.Arrival, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val card = remember(arrival) { Animatable(if (reduce) 1f else 0f) }
    val text = remember(arrival) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(arrival) {
        if (reduce) return@LaunchedEffect
        card.animateTo(1f, tween(JourneyMotion.ARRIVAL_FADE_MS))
        text.animateTo(1f, tween(JourneyMotion.ARRIVAL_FADE_MS, delayMillis = JourneyMotion.ARRIVAL_TEXT_DELAY_MS))
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        val city = cityOf(arrival.index)
        Postcard(
            arrival.stop, description = stringResource(R.string.journey_card_description, city), seconds = rememberSceneSeconds(),
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().height(PostcardHeight).graphicsLayer { alpha = card.value }.clip(PostcardShape),
        )
        Column(Modifier.widthIn(max = MaxContentWidth).graphicsLayer { alpha = text.value }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(city, color = colors.onSurface, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text(listOf(placeOf(arrival.index), countryOf(arrival.index)).filter { it.isNotEmpty() }.joinToString(" · "), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(factOf(arrival.index), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onIntent(JourneyIntent.StampClicked) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.journey_stamp)) }
        }
    }
}

@Composable
private fun StampContent(stamp: JourneyPhase.Stamp, state: JourneyState, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val press = remember(stamp) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(stamp) { if (!reduce) press.animateTo(1f, tween(JourneyMotion.STAMP_MS, easing = FastOutSlowInEasing)) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.journey_stamp_page, (stamp.index - 1) / STAMPS_PER_PAGE + 1), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.size(220.dp).clip(CardShape).background(colors.surfaceContainer).border(1.dp, colors.outlineVariant, CardShape), contentAlignment = Alignment.Center) {
            StampView(
                stopId = stamp.stop.id, index = stamp.index, visited = true, city = cityOf(stamp.index),
                date = state.visited.lastOrNull { it.stop.id == stamp.stop.id }?.let { Formats.dayAndMonth(it.arrivedAtEpochMs, ZoneId.systemDefault()) }.orEmpty(),
                size = 168.dp,
                // the stamp comes down onto the page: larger and faint, then in place
                modifier = Modifier.graphicsLayer {
                    val k = 1.4f - 0.4f * press.value
                    scaleX = k
                    scaleY = k
                    alpha = press.value
                    rotationZ = -8f
                },
            )
        }
        val next = state.next
        Text(
            text = if (next == null) stringResource(R.string.journey_stamp_text_last, stamp.index) else stringResource(R.string.journey_stamp_text, stamp.index, cityToOf(stamp.index + 1), taktsInWords(next.price.toLong())),
            color = colors.onSurface, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
        Button(onClick = { onIntent(JourneyIntent.StampDone) }, modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.journey_done)) }
    }
}

private const val STAMPS_PER_PAGE = 6

