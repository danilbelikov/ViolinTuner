package com.violinjourney.app.feature.journey

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.ButtonLine
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.FaceArrival
import com.violinjourney.app.core.ui.components.GlassPlate
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.OneLineText
import com.violinjourney.app.core.ui.components.SettleAfterDoubleTap
import com.violinjourney.app.core.ui.components.ShortfallPlate
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.WordsAndNumber
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.home.HomeTexts
import com.violinjourney.app.feature.journey.art.Postcard
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.journey_back
import com.violinjourney.app.shared.resources.journey_card_description
import com.violinjourney.app.shared.resources.journey_depart
import com.violinjourney.app.shared.resources.journey_depart_spend
import com.violinjourney.app.shared.resources.journey_done
import com.violinjourney.app.shared.resources.journey_enter_home
import com.violinjourney.app.shared.resources.journey_enter_home_at
import com.violinjourney.app.shared.resources.journey_have
import com.violinjourney.app.shared.resources.journey_have_description
import com.violinjourney.app.shared.resources.journey_intro_first
import com.violinjourney.app.shared.resources.journey_intro_start
import com.violinjourney.app.shared.resources.journey_intro_takts
import com.violinjourney.app.shared.resources.journey_intro_text
import com.violinjourney.app.shared.resources.journey_intro_title
import com.violinjourney.app.shared.resources.journey_map
import com.violinjourney.app.shared.resources.journey_missing
import com.violinjourney.app.shared.resources.journey_next
import com.violinjourney.app.shared.resources.journey_no_cards
import com.violinjourney.app.shared.resources.journey_passed
import com.violinjourney.app.shared.resources.journey_passport
import com.violinjourney.app.shared.resources.journey_road
import com.violinjourney.app.shared.resources.journey_since
import com.violinjourney.app.shared.resources.journey_stamp
import com.violinjourney.app.shared.resources.journey_stamp_last
import com.violinjourney.app.shared.resources.journey_stamp_leg
import com.violinjourney.app.shared.resources.journey_stamp_number
import com.violinjourney.app.shared.resources.journey_stamp_page
import com.violinjourney.app.shared.resources.journey_stop_home
import com.violinjourney.app.shared.resources.journey_stop_of
import com.violinjourney.app.shared.resources.journey_title
import com.violinjourney.app.shared.resources.journey_tour_done
import com.violinjourney.app.shared.resources.journey_tour_more
import com.violinjourney.app.shared.resources.venue_play_here
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

// The journey of the redesign (spec 3.36.7, 5.29 R7; journey-home.html 1–2, landscape.html «Путешествие»).
private val TopBarHeight = 56.dp
private val TopBarHeightLandscape = 48.dp
private val Target = 48.dp
private val MaxContentWidth = 560.dp
private val ScreenSide = 16.dp
private val ScrollTop = 4.dp
private val ScrollEnd = 24.dp
private val PostcardShape = RoundedCornerShape(20.dp)
private val LandscapeLeft = 360.dp
private val LandscapePostcard = 180.dp
private val ColumnGap = 16.dp

// The postcard and its words.
private val StopPillInset = 10.dp
private val StopPillHeight = 28.dp
private val StopPillSide = 11.dp
private val CityTop = 14.dp
private val PlaceTop = 2.dp
private val FactTop = 8.dp
private val DoorTop = 12.dp

// The door home.
private val DoorShape = RoundedCornerShape(16.dp)
private val DoorMinHeight = 64.dp
private val DoorPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
private val DoorThumbWidth = 64.dp
private val DoorThumbHeight = 48.dp
private val DoorThumbShape = RoundedCornerShape(10.dp)
private val DoorGap = 12.dp
private val DoorIcon = 24.dp

// «Пройдено» and the ribbon of the stops behind.
private val PassedTop = 22.dp
private val StripTop = 10.dp
private val StripGap = 10.dp
private val StripWidth = 112.dp
private val StripHeight = 76.dp
private val StripShape = RoundedCornerShape(12.dp)
private val StripCityTop = 5.dp

// The card of the path and «Мировое турне пройдено» in the bottom zone.
private val CardPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
private val BarTop = 8.dp
private val TourRowMin = 48.dp
private val PathSign = 14.dp
private val TourSign = 16.dp
private val PlateSign = 18.dp

// The moments of the road: the intro, the arrival and the page of the stamp; lying, a picture and its words beside it.
private val IntroGap = 16.dp
private val ArrivalGap = 16.dp
private val BesideGap = 24.dp

// The page of the stamp: the frame — 220, giving way — is StampFit's.
private val StampFrameShape = RoundedCornerShape(16.dp)
private val StampFrameTop = 16.dp
private val StampNumberTop = 20.dp
private val StampLegTop = 4.dp
private val StampBesideGap = 24.dp
private val StampBesideNumberTop = 8.dp
private val StampButtonsGap = 4.dp

// Sizes of the words (5.29 R7).
private val TitleSize = 18.sp
private const val TITLE_LEAST_SP = 15f
private val CitySize = 26.sp
private const val CITY_LEAST_SP = 20f
private val PillSize = 12.sp
private val WordsSize = 14.sp
private const val FACT_LINE_HEIGHT = 1.45f
private val DoorTitleSize = 15.sp
private val CaptionSize = 13.sp
private val PassedSize = 16.sp
private val StripCitySize = 13.sp
private val StripDaySize = 12.sp
private const val STRIP_LEAST_SP = 11f
private val TourSize = 16.sp
private val StampNumberSize = 22.sp
private val StampLegSize = 15.sp

/**
 * The journey (spec 3.23, 3.36.7; handoff 26a–26f): where the player is, how far the next city, and the road when it is taken. Its
 * moments — the intro, the calm screen, the road, the arrival, the page of the stamp — take each other's place by a crossfade, their
 * buttons in the same bottom zone.
 */
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
            // A moment that came in the place of another holds the buttons of its zone for the time of a double tap (5.29 R7), as a
            // face of a sheet holds its main one (R3): «В путь» stands where «Собрать футляр» stood, «Играть здесь» of the stamp
            // where «Поставить штамп» stood — under the finger that pressed that one, and the second tap would spend the takts or
            // skip the page unseen. An entering moment starts before it is seen (PreEnter); the one the screen opened on does not.
            // Counted in frames from its first one: with the animations off the swap is instant, and it is held all the same.
            val arrival = remember { FaceArrival(inPlace = transition.currentState == EnterExitState.PreEnter) }
            SettleAfterDoubleTap(arrival)
            val onZone: (JourneyIntent) -> Unit = { intent -> if (!arrival.holds) onIntent(intent) }
            when (phase) {
                JourneyPhase.Idle -> {
                    // The leg is paid the moment the road starts, so while this content fades out the state
                    // already stands in the next city: keep showing what was there when the screen was calm.
                    val calm = remember { arrayOf(state) }
                    if (state.phase == JourneyPhase.Idle) calm[0] = state
                    IdleContent(calm[0], onIntent, onZone)
                }
                JourneyPhase.Intro -> IntroContent(onIntent, onZone)
                is JourneyPhase.Road -> RoadContent(phase)
                is JourneyPhase.Arrival -> ArrivalContent(phase, onZone)
                is JourneyPhase.Stamp -> StampContent(phase, state, onZone)
            }
        }
    }
}

/**
 * The bar of the journey and the home (spec 5.29 R7, «Общее»): «назад», the [title] — 18 sp / 800 on one line, stepping down to
 * 15 sp before it is cut, a heading for a reader — and [trailing] at the end. 56 high, 48 in a window wider than high.
 */
@Composable
fun JourneyTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val window = LocalWindowInfo.current.containerSize
    Row(
        modifier = modifier.fillMaxWidth().height(if (window.width > window.height) TopBarHeightLandscape else TopBarHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(Target).clip(CircleShape).clickable(onClickLabel = stringResource(Res.string.journey_back), role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
        OneLineText(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = TitleSize, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em),
            minSp = TITLE_LEAST_SP,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            color = colors.onSurface,
        )
        Box(Modifier.padding(end = 12.dp)) { trailing() }
    }
}

/**
 * The calm screen (spec 3.36.7): the postcard, the city and the door home scroll over the bottom zone, which holds the card of the
 * path and «В путь» — or the plate of what is missing, or «Мировое турне пройдено». Upright the zone is the width of the column (560
 * at most); lying it is the width of the left column of 360, and on the right only «Пройдено» and the ribbon scroll to the bottom.
 * [onZone] — what the button of the zone says, held for a double tap after the moment before ([JourneyScreen]).
 */
@Composable
private fun IdleContent(state: JourneyState, onIntent: (JourneyIntent) -> Unit, onZone: (JourneyIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        JourneyTopBar(stringResource(Res.string.journey_title), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            // the two that are about the road stay in the bar, as icons; the purse is not here — it is at «В путь», where spending
            // is decided (3.36.7)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarIcon(AppIcons.Map, stringResource(Res.string.journey_map)) { onIntent(JourneyIntent.MapClicked) }
                BarIcon(AppIcons.Passport, stringResource(Res.string.journey_passport)) { onIntent(JourneyIntent.PassportClicked) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) LandscapeIdle(state, onIntent, onZone) else PortraitIdle(state, onIntent, onZone, available = maxHeight)
        }
    }
}

@Composable
private fun PortraitIdle(state: JourneyState, onIntent: (JourneyIntent) -> Unit, onZone: (JourneyIntent) -> Unit, available: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AppDock(
            dock = { WayDock(state, onZone) },
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
            metrics = currentDockMetrics().copy(side = ScreenSide),
        ) {
            val dock = LocalDockInset.current
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenSide, top = ScrollTop, end = ScreenSide, bottom = dock + ScrollEnd),
            ) {
                // the picture gives way first: the zone never shrinks (5.29 R7)
                Place(state, PictureFit.height(available, dock, PictureFit.PostcardMin, PictureFit.PostcardMax), onIntent)
                Passed(state, onIntent, Modifier.padding(top = PassedTop))
            }
        }
    }
}

@Composable
private fun LandscapeIdle(state: JourneyState, onIntent: (JourneyIntent) -> Unit, onZone: (JourneyIntent) -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = ScreenSide), horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
        AppDock(
            dock = { WayDock(state, onZone) },
            modifier = Modifier.width(LandscapeLeft).fillMaxHeight(),
            padSides = false,
            metrics = currentDockMetrics().copy(side = ScreenSide),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = ScrollTop, bottom = LocalDockInset.current + ScrollEnd)) {
                Place(state, LandscapePostcard, onIntent)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = ScrollTop, bottom = ScrollEnd)) {
            Passed(state, onIntent)
        }
    }
}

/**
 * The postcard of the current stop and what is said about it (spec 3.36.7): «остановка 4 из 16» on the glass of its top corner, the
 * city, «место · страна · с 20 сентября», the fact and the door home. A tap on the postcard opens the stop (at home — «Дом»); for a
 * reader it is «Путешествие: Вена, остановка 4 из 16», a button.
 */
@Composable
private fun Place(state: JourneyState, postcardHeight: Dp, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val index = state.currentIndex
    val city = cityOf(index)
    val stop = if (index == 0) stringResource(Res.string.journey_stop_home, state.totalStops) else stringResource(Res.string.journey_stop_of, index, state.totalStops)
    val said = stringResource(Res.string.journey_card_description, city) + ", " + stop
    Box(
        Modifier
            .fillMaxWidth()
            .height(postcardHeight)
            .clip(PostcardShape)
            .clickable(role = Role.Button) { onIntent(JourneyIntent.StopClicked(state.current.id)) }
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            },
    ) {
        StopPostcard(state.current, description = said, modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(), homeOutside = true)
        GlassPlate(
            modifier = Modifier.align(Alignment.TopStart).padding(StopPillInset).height(StopPillHeight),
            contentPadding = PaddingValues(horizontal = StopPillSide),
        ) {
            Text(
                text = stop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = PillSize, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
    CityName(city, Modifier.padding(top = CityTop))
    val since = state.arrivedAtEpochMs?.takeIf { index > 0 }?.let { stringResource(Res.string.journey_since, Formats.dayAndMonth(it, TimeZone.currentSystemDefault())) }
    Text(
        text = listOfNotNull(placeOf(index).takeIf { it.isNotEmpty() }, countryOf(index).takeIf { it.isNotEmpty() }, since)
            .joinToString(stringResource(Res.string.dot_separator)),
        modifier = Modifier.padding(top = PlaceTop),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize),
    )
    Text(
        text = factOf(index),
        modifier = Modifier.padding(top = FactTop),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize, lineHeight = WordsSize * FACT_LINE_HEIGHT),
    )
    HomeDoor(atHome = index == 0, onClick = { onIntent(JourneyIntent.StopClicked(JourneyRoute.HOME)) }, modifier = Modifier.padding(top = DoorTop))
}

/**
 * The name of a city, 26 sp / 800: on as many lines as it needs, but never broken inside a word — smaller, down to 20 sp, where a
 * word would not fit its line («Saint-Pétersbourg» in a column of 328 at a large font).
 */
@Composable
private fun CityName(city: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = city,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        autoSize = remember { WholeWordsFit(CitySize.value, CITY_LEAST_SP) },
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = CitySize, lineHeight = 1.15.em, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
    )
}

/**
 * The door home (spec 3.36.7, handoff 28c): a card with the room in it — the lamp and the cat are seen — named by what it does. It is
 * never filled, at the stop «Дом» either (there it says «вы дома»): the main action of the screen is one, in the bottom zone. For a
 * reader — «Войти в дом, Маленький деревянный домик», a button.
 */
@Composable
private fun HomeDoor(atHome: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val home = LocalHomeLook.current
    val door = stringResource(Res.string.journey_enter_home)
    val caption = if (atHome) {
        stringResource(Res.string.journey_enter_home_at)
    } else {
        home?.let { HomeTexts.houseNames[HomeRules.house(it)] }?.let { stringResource(it) }.orEmpty()
    }
    val said = listOf(door, caption).filter { it.isNotEmpty() }.joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DoorMinHeight)
            .clip(DoorShape)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            }
            .padding(DoorPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoorGap),
    ) {
        Box(Modifier.size(DoorThumbWidth, DoorThumbHeight).clip(DoorThumbShape)) {
            StopPostcard(JourneyRoute.stops.first(), description = "", modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(door, color = colors.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontSize = DoorTitleSize, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
            if (caption.isNotEmpty()) {
                Text(caption, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = CaptionSize, lineHeight = 18.sp))
            }
        }
        AppIcon(AppIcons.Door, contentDescription = null, size = DoorIcon, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(Target).clip(CircleShape).clickable(onClickLabel = label, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        AppIcon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * «Пройдено 4 из 16» — a heading — and the ribbon of the stops behind, the freshest first (spec 3.36.7): still thumbnails of 112 × 76
 * with the city and the day of the arrival under each, «Зальцбург, 18 сентября» for a reader. The oldest is home — its tap is the door
 * home, as it always was. Nothing behind yet — «Первая открытка появится в Кремоне».
 */
@Composable
private fun Passed(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(Res.string.journey_passed, state.currentIndex, state.totalStops),
        modifier = modifier.semantics { heading() },
        color = colors.onSurface,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = PassedSize, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
    )
    val behind = state.visited.dropLast(1)
    if (behind.isEmpty()) {
        Text(
            text = stringResource(Res.string.journey_no_cards),
            modifier = Modifier.padding(top = StripTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize),
        )
    } else {
        LazyRow(Modifier.padding(top = StripTop), horizontalArrangement = Arrangement.spacedBy(StripGap)) {
            items(behind.asReversed(), key = { it.stop.id }) { visited -> PassedStop(visited, onIntent) }
        }
    }
}

@Composable
private fun PassedStop(visited: VisitedStop, onIntent: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val city = cityOf(visited.index)
    val day = Formats.dayAndMonth(visited.arrivedAtEpochMs, TimeZone.currentSystemDefault())
    val said = "$city, $day"
    Column(
        Modifier
            .width(StripWidth)
            .clip(StripShape)
            .clickable(role = Role.Button) { onIntent(JourneyIntent.StopClicked(visited.stop.id)) }
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            },
    ) {
        StopPostcard(visited.stop, description = said, modifier = Modifier.fillMaxWidth().height(StripHeight).clip(StripShape))
        OneLineText(
            text = city,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = StripCitySize, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            minSp = STRIP_LEAST_SP,
            modifier = Modifier.padding(top = StripCityTop),
            color = colors.onSurface,
        )
        OneLineText(
            text = day,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = StripDaySize, lineHeight = 16.sp),
            minSp = STRIP_LEAST_SP,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * The bottom zone of the calm screen (spec 3.36.7): the card of the path and under it «В путь · Прага» with «спишется 1 600 из
 * 47 884» — the number never cut — while the takts are enough; the plate «не хватает 1 128» with «примерно 4 занятия» while they are
 * not: it tells, it does not forbid. At the end of what is drawn — only the card «Мировое турне пройдено» with the purse. [onZone] —
 * «В путь», held for a double tap after the moment before.
 */
@Composable
private fun DockScope.WayDock(state: JourneyState, onZone: (JourneyIntent) -> Unit) {
    val next = state.next
    if (next == null) {
        TourDone(state.balance)
        return
    }
    val nextIndex = state.currentIndex + 1
    PathCard(state, next, nextIndex)
    if (state.canDepart) {
        val spent = Formats.takts(next.price.toLong())
        val purse = Formats.takts(state.balance)
        val caption = stringResource(Res.string.journey_depart_spend, spent, purse)
        AppButton(
            text = stringResource(Res.string.journey_depart, cityOf(nextIndex)),
            onClick = { onZone(JourneyIntent.DepartClicked) },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.Travel,
            caption = caption,
            compact = compact,
            oneLine = true,
            // the two numbers and what stands between them, in whatever order the language says them
            keep = ButtonLine.span(caption, listOf(spent, purse)),
        )
    } else {
        val missing = Formats.takts(state.missing)
        val sessions = sessionsInWords(state.sessionsLeft)
        ShortfallPlate(
            text = stringResource(Res.string.journey_missing, missing),
            modifier = Modifier.fillMaxWidth(),
            caption = sessions,
            description = stringResource(Res.string.journey_missing, taktsInWords(state.missing)) + ", " + sessions,
            compact = compact,
            keep = missing,
            leading = { TaktIcon(size = PlateSign) },
        )
    }
}

/**
 * «до Праги · поездом · 4 часа» and the price at the end — «472 / 1 600» and the bar to the price while the takts are short (a full bar
 * says nothing). For a reader one text: «до Праги, поездом, 4 часа, 472 из 1 600 тактов».
 */
@Composable
private fun PathCard(state: JourneyState, next: JourneyStop, nextIndex: Int) {
    val colors = MaterialTheme.colorScheme
    val price = next.price.toLong()
    val line = stringResource(Res.string.journey_next, cityToOf(nextIndex), roadOf(nextIndex))
    val number = if (state.canDepart) Formats.takts(price) else stringResource(Res.string.journey_have, Formats.takts(state.balance), Formats.takts(price))
    val amount = if (state.canDepart) taktsInWords(price) else stringResource(Res.string.journey_have_description, Formats.takts(state.balance), taktsInWords(price))
    val said = line.replace(stringResource(Res.string.dot_separator), ", ") + ", " + amount
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clearAndSetSemantics { contentDescription = said }
            .padding(CardPadding),
    ) {
        val numberStyle = MaterialTheme.typography.labelLarge.copy(fontSize = WordsSize, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
        WordsAndNumber(
            text = line,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize, lineHeight = 20.sp),
            color = colors.onSurfaceVariant,
        ) { TaktAmount(number, style = numberStyle, color = colors.onSurface, icon = PathSign, merge = false) }
        if (!state.canDepart) PriceBar(fraction = (state.balance.toFloat() / next.price).coerceIn(0f, 1f), modifier = Modifier.padding(top = BarTop))
    }
}

/** After the last city drawn: «Мировое турне пройдено» with the purse, and «новые города придут с обновлениями» — the takts wait (3.23). */
@Composable
private fun TourDone(balance: Long) {
    val colors = MaterialTheme.colorScheme
    val done = stringResource(Res.string.journey_tour_done)
    val more = stringResource(Res.string.journey_tour_more)
    val said = listOf(done, taktsInWords(balance), more).joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShapes.M)
            .background(colors.surfaceContainer)
            .clearAndSetSemantics { contentDescription = said }
            .padding(CardPadding),
    ) {
        val style = MaterialTheme.typography.titleMedium.copy(fontSize = TourSize, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
        WordsAndNumber(text = done, style = style, color = colors.onSurface, modifier = Modifier.heightIn(min = TourRowMin)) {
            TaktAmount(Formats.takts(balance), style = style, color = colors.primary, icon = TourSign, merge = false)
        }
        Text(more, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = CaptionSize, lineHeight = 18.sp))
    }
}

@Composable
fun PriceBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().height(6.dp).drawBehind {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surfaceContainerHighest, cornerRadius = radius)
            if (fraction > 0f) drawRoundRect(colors.primary, size = Size((size.width * fraction).coerceAtLeast(size.height), size.height), cornerRadius = radius)
        },
    )
}

/**
 * A moment of the journey with its buttons in the bottom zone (spec 3.36.7): the intro, the arrival and the page of the stamp — the
 * zone 560 wide at most in the middle, faded in and out with its moment by the crossfade of the phases. [content] gets the height it
 * stands in, for its picture to give way first ([PictureFit], [StampFit]), and whether it lies — wider than high: then its picture
 * stands at the start of the column and its words beside it ([Beside], 5.29 R7), since under the picture they would wait under the
 * zone (640 × 360 on the emulator is 603 × 308 under its bars).
 */
@Composable
private fun PhaseDock(dock: @Composable DockScope.() -> Unit, content: @Composable (available: Dp, lying: Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val available = maxHeight
        val lying = maxWidth > maxHeight
        AppDock(
            dock = dock,
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
            metrics = currentDockMetrics().copy(side = ScreenSide),
        ) { content(available, lying) }
    }
}

/**
 * A moment lying (spec 5.29 R7): its [picture] at the start of the column and its [words] beside it, [BesideGap] apart, the words
 * [wordsArrangement] among themselves — the row in the middle of the column, [verticalAlignment] between the two.
 */
@Composable
private fun Beside(
    picture: @Composable () -> Unit,
    verticalAlignment: Alignment.Vertical,
    wordsArrangement: Arrangement.Vertical = Arrangement.Top,
    words: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BesideGap, Alignment.CenterHorizontally),
        verticalAlignment = verticalAlignment,
    ) {
        picture()
        Column(Modifier.weight(1f, fill = false), verticalArrangement = wordsArrangement, content = words)
    }
}

/**
 * The intro (spec 3.25, 3.36.7): the words it always said — the road starts at home, where takts come from, the first city — over
 * «Собрать футляр» in the zone; [onZone] — the button, held for a double tap after the moment before. Upright the postcard stands
 * over the words; lying beside them, its title at its top ([Beside]).
 */
@Composable
private fun IntroContent(onIntent: (JourneyIntent) -> Unit, onZone: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val first = JourneyRoute.stops[1]
    Column(Modifier.fillMaxSize()) {
        JourneyTopBar(stringResource(Res.string.journey_title), onBack = { onIntent(JourneyIntent.BackClicked) })
        PhaseDock(
            dock = {
                AppButton(
                    text = stringResource(Res.string.journey_intro_start),
                    onClick = { onZone(JourneyIntent.IntroConfirmed) },
                    modifier = Modifier.fillMaxWidth(),
                    compact = compact,
                )
            },
        ) { available, lying ->
            val dock = LocalDockInset.current
            val postcard: @Composable (Modifier) -> Unit = { size ->
                StopPostcard(JourneyRoute.stops.first(), description = cityOf(0), modifier = size.clip(PostcardShape), seconds = rememberSceneSeconds())
            }
            val words: @Composable () -> Unit = {
                Text(stringResource(Res.string.journey_intro_title), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(stringResource(Res.string.journey_intro_text), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
                // where takts come from — without the arithmetic: the formula is our secret (spec 3.25)
                Text(stringResource(Res.string.journey_intro_takts), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.journey_intro_first, taktsInWords(first.price.toLong())), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenSide, top = ScrollTop, end = ScreenSide, bottom = dock + ScrollEnd),
                verticalArrangement = Arrangement.spacedBy(IntroGap),
            ) {
                if (lying) {
                    // the postcard over the fade of the zone, 180 at most
                    val height = PictureFit.lying(available - ScrollTop - dock - DockDefaults.Fade)
                    Beside(
                        picture = { postcard(Modifier.size(PictureFit.LyingWidth, height)) },
                        verticalAlignment = Alignment.Top,
                        wordsArrangement = Arrangement.spacedBy(IntroGap),
                    ) { words() }
                } else {
                    postcard(Modifier.fillMaxWidth().height(PictureFit.height(available, dock, PictureFit.PostcardMin, PictureFit.PostcardMax)))
                    words()
                }
            }
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
    // the train's progress is read by the map while it draws: the screen is not composed again on every frame of the road
    val onMap = remember(road) { RoadOnMap(road.fromIndex, { progress.value }, road.to.transport) }
    Box(Modifier.fillMaxSize()) {
        JourneyMapCanvas(reached = road.fromIndex, road = onMap, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).clip(RoadCardShape).background(colors.surfaceContainer.copy(alpha = 0.92f)).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.journey_road, cityOf(road.fromIndex), cityOf(road.fromIndex + 1)), color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(roadOf(road.fromIndex + 1), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val RoadCardShape = RoundedCornerShape(16.dp)

/**
 * The arrival (spec 3.36.7, 5.17): the postcard comes first, then the city, «место · страна» and the fact; «Поставить штамп» with the
 * sign of a stamp stands in the bottom zone and comes in with the words — the button alone takes their alpha, never the zone; [onZone]
 * — the button, held for a double tap after the moment before. Upright the words stand under the postcard, in the middle, over the
 * fade of the zone; lying beside it ([Beside]) — under it the city stood under the fade on 640 × 360.
 */
@Composable
private fun ArrivalContent(arrival: JourneyPhase.Arrival, onZone: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val card = remember(arrival) { Animatable(if (reduce) 1f else 0f) }
    val text = remember(arrival) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(arrival) {
        if (reduce) return@LaunchedEffect
        card.animateTo(1f, tween(JourneyMotion.ARRIVAL_FADE_MS))
        text.animateTo(1f, tween(JourneyMotion.ARRIVAL_FADE_MS, delayMillis = JourneyMotion.ARRIVAL_TEXT_DELAY_MS))
    }
    PhaseDock(
        dock = {
            AppButton(
                text = stringResource(Res.string.journey_stamp),
                onClick = { onZone(JourneyIntent.StampClicked) },
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = text.value },
                icon = AppIcons.Stamp,
                compact = compact,
            )
        },
    ) { available, lying ->
        val dock = LocalDockInset.current
        val city = cityOf(arrival.index)
        val align = if (lying) TextAlign.Start else TextAlign.Center
        val postcard: @Composable (Modifier) -> Unit = { size ->
            Postcard(
                arrival.stop, description = stringResource(Res.string.journey_card_description, city), seconds = rememberSceneSeconds(),
                modifier = size.graphicsLayer { alpha = card.value }.clip(PostcardShape),
            )
        }
        val words: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier.graphicsLayer { alpha = text.value }, horizontalAlignment = if (lying) Alignment.Start else Alignment.CenterHorizontally) {
                CityName(city, textAlign = align)
                Text(
                    text = listOf(placeOf(arrival.index), countryOf(arrival.index)).filter { it.isNotEmpty() }.joinToString(stringResource(Res.string.dot_separator)),
                    modifier = Modifier.padding(top = PlaceTop),
                    color = colors.onSurfaceVariant,
                    textAlign = align,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize),
                )
                Text(
                    text = factOf(arrival.index),
                    modifier = Modifier.padding(top = FactTop),
                    color = colors.onSurfaceVariant,
                    textAlign = align,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize, lineHeight = WordsSize * FACT_LINE_HEIGHT),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // the words end over the fade of the zone, not in it
                .padding(start = ScreenSide, top = ScreenSide, end = ScreenSide, bottom = dock + DockDefaults.Fade),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ArrivalGap, Alignment.CenterVertically),
        ) {
            if (lying) {
                val height = PictureFit.lying(available - ScreenSide - dock - DockDefaults.Fade)
                Beside(picture = { postcard(Modifier.size(PictureFit.LyingWidth, height)) }, verticalAlignment = Alignment.CenterVertically) {
                    words(Modifier)
                }
            } else {
                postcard(Modifier.fillMaxWidth().height(PictureFit.height(available, dock, PictureFit.PostcardMin, PictureFit.PostcardMax)))
                words(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The page of the passport (spec 3.36.7, 5.17): «Паспорт · страница 1», the stamp coming down onto its place, «Штамп № 4» in large
 * and, grey, the next leg and its price — «До Праги — 1 600 тактов»; after Sydney «Дальше дорога ещё рисуется.». In the zone «Готово»
 * and, quietly under it, «Играть здесь» (3.27: Live in the city just reached); [onZone] — both, held for a double tap after the
 * arrival, whose «Поставить штамп» stood where «Играть здесь» stands. The page stands over the fade of the zone ([StampPage]).
 */
@Composable
private fun StampContent(stamp: JourneyPhase.Stamp, state: JourneyState, onZone: (JourneyIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val press = remember(stamp) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(stamp) { if (!reduce) press.animateTo(1f, tween(JourneyMotion.STAMP_MS, easing = FastOutSlowInEasing)) }
    PhaseDock(
        dock = {
            // «Играть здесь» 4 under «Готово», not the 10 between the rows of a zone: one row of two buttons
            val low = compact
            Column(verticalArrangement = Arrangement.spacedBy(StampButtonsGap)) {
                AppButton(
                    text = stringResource(Res.string.journey_done),
                    onClick = { onZone(JourneyIntent.StampDone) },
                    modifier = Modifier.fillMaxWidth(),
                    compact = low,
                )
                // quietly, a text button: the arrival is about the postcard (spec 3.27, handoff 29k)
                AppButton(
                    text = stringResource(Res.string.venue_play_here),
                    onClick = { onZone(JourneyIntent.PlayHereClicked) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = AppButtonStyle.Text,
                    icon = AppIcons.Theatre,
                )
            }
        },
    ) { available, lying ->
        val dock = LocalDockInset.current
        val align = if (lying) TextAlign.Start else TextAlign.Center
        val next = state.next
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenSide, top = ScreenSide, end = ScreenSide, bottom = dock + DockDefaults.Fade),
            verticalArrangement = Arrangement.Center,
        ) {
            StampPage(
                lying = lying,
                room = available - ScreenSide - dock - DockDefaults.Fade,
                caption = {
                    Text(
                        text = stringResource(Res.string.journey_stamp_page, (stamp.index - 1) / STAMPS_PER_PAGE + 1),
                        color = colors.onSurfaceVariant,
                        textAlign = align,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = CaptionSize, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    )
                },
                frame = {
                    BoxWithConstraints(
                        Modifier.clip(StampFrameShape).background(colors.surfaceContainer).border(1.dp, colors.outlineVariant, StampFrameShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        StampView(
                            stopId = stamp.stop.id, index = stamp.index, visited = true, city = cityOf(stamp.index),
                            date = state.visited.lastOrNull { it.stop.id == stamp.stop.id }?.let { Formats.dayAndMonth(it.arrivedAtEpochMs, TimeZone.currentSystemDefault()) }.orEmpty(),
                            // its share of the frame, whatever the frame has given way to
                            size = maxWidth * StampFit.STAMP_SHARE,
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
                },
                number = {
                    Text(
                        text = stringResource(Res.string.journey_stamp_number, stamp.index),
                        color = colors.onSurface,
                        textAlign = align,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = StampNumberSize, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
                    )
                },
                leg = {
                    Text(
                        text = if (next == null) {
                            stringResource(Res.string.journey_stamp_last)
                        } else {
                            stringResource(Res.string.journey_stamp_leg, cityToOf(stamp.index + 1), taktsInWords(next.price.toLong()))
                        },
                        color = colors.onSurfaceVariant,
                        textAlign = align,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = StampLegSize, lineHeight = 22.sp),
                    )
                },
            )
        }
    }
}

/**
 * The page of the stamp laid out (spec 3.36.7, 5.29 R7) in the [room] it may take over the fade of the zone. Upright, in the middle of
 * the column: the [caption] «Паспорт · страница N», [StampFrameTop] to the [frame], [StampNumberTop] to the [number] «Штамп № 4» and
 * [StampLegTop] to the [leg] — the frame gives the words the room they need and stands in what is left ([StampFit]). Lying the frame
 * stands at the start and the three beside it, [StampBesideGap] off it, the row in the middle of the column — under the frame they
 * would stand under the zone (892 × 412 has some 230 dp over it) — and the frame takes the whole room, 220 at most. The words are
 * never cut; the frame is laid out at its own size, square.
 */
@Composable
private fun StampPage(
    lying: Boolean,
    room: Dp,
    caption: @Composable () -> Unit,
    frame: @Composable () -> Unit,
    number: @Composable () -> Unit,
    leg: @Composable () -> Unit,
) {
    Layout(contents = listOf(caption, frame, number, leg), modifier = Modifier.fillMaxWidth()) { (captionM, frameM, numberM, legM), constraints ->
        val width = constraints.maxWidth
        val legTop = StampLegTop.roundToPx()
        if (lying) {
            val side = StampFit.frame(room).roundToPx().coerceAtMost(width)
            val gap = StampBesideGap.roundToPx()
            val words = Constraints(maxWidth = (width - side - gap).coerceAtLeast(0))
            val c = captionM.single().measure(words)
            val n = numberM.single().measure(words)
            val l = legM.single().measure(words)
            val f = frameM.single().measure(Constraints.fixed(side, side))
            val numberTop = StampBesideNumberTop.roundToPx()
            val wordsHeight = c.height + numberTop + n.height + legTop + l.height
            val wordsWidth = maxOf(c.width, n.width, l.width)
            val height = maxOf(side, wordsHeight)
            val start = ((width - side - gap - wordsWidth) / 2).coerceAtLeast(0)
            layout(width, height) {
                f.place(start, (height - side) / 2)
                val x = start + side + gap
                var y = (height - wordsHeight) / 2
                c.place(x, y)
                y += c.height + numberTop
                n.place(x, y)
                y += n.height + legTop
                l.place(x, y)
            }
        } else {
            val words = Constraints(maxWidth = width)
            val c = captionM.single().measure(words)
            val n = numberM.single().measure(words)
            val l = legM.single().measure(words)
            val frameTop = StampFrameTop.roundToPx()
            val numberTop = StampNumberTop.roundToPx()
            val wordsHeight = c.height + frameTop + numberTop + n.height + legTop + l.height
            val side = StampFit.frame(room - wordsHeight.toDp()).roundToPx().coerceAtMost(width)
            val f = frameM.single().measure(Constraints.fixed(side, side))
            layout(width, wordsHeight + side) {
                var y = 0
                c.place((width - c.width) / 2, y)
                y += c.height + frameTop
                f.place((width - side) / 2, y)
                y += side + numberTop
                n.place((width - n.width) / 2, y)
                y += n.height + legTop
                l.place((width - l.width) / 2, y)
            }
        }
    }
}

private const val STAMPS_PER_PAGE = 6
