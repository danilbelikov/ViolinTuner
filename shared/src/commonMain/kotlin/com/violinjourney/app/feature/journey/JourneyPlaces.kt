package com.violinjourney.app.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.journey.JourneyExtra
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.OneLineText
import com.violinjourney.app.core.ui.components.WholeText
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.WordsAndEnd
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.TwoWay
import com.violinjourney.app.feature.home.TwoWayDefaults
import com.violinjourney.app.feature.journey.art.Postcard
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.journey_card_description
import com.violinjourney.app.shared.resources.journey_extra_bought
import com.violinjourney.app.shared.resources.journey_extra_souvenir
import com.violinjourney.app.shared.resources.journey_extra_souvenir_note
import com.violinjourney.app.shared.resources.journey_extra_time
import com.violinjourney.app.shared.resources.journey_extra_time_note
import com.violinjourney.app.shared.resources.journey_extra_view
import com.violinjourney.app.shared.resources.journey_extra_view_inside
import com.violinjourney.app.shared.resources.journey_extra_view_outside
import com.violinjourney.app.shared.resources.journey_extras
import com.violinjourney.app.shared.resources.journey_extras_hint
import com.violinjourney.app.shared.resources.journey_fullscreen
import com.violinjourney.app.shared.resources.journey_go_home
import com.violinjourney.app.shared.resources.journey_here
import com.violinjourney.app.shared.resources.journey_here_end
import com.violinjourney.app.shared.resources.journey_here_enough
import com.violinjourney.app.shared.resources.journey_map
import com.violinjourney.app.shared.resources.journey_missing
import com.violinjourney.app.shared.resources.journey_mode_day
import com.violinjourney.app.shared.resources.journey_mode_evening
import com.violinjourney.app.shared.resources.journey_mode_inside
import com.violinjourney.app.shared.resources.journey_mode_outside
import com.violinjourney.app.shared.resources.journey_next_short
import com.violinjourney.app.shared.resources.journey_passport
import com.violinjourney.app.shared.resources.journey_passport_count
import com.violinjourney.app.shared.resources.journey_passport_next
import com.violinjourney.app.shared.resources.journey_passport_soon
import com.violinjourney.app.shared.resources.journey_stamp_locked_description
import com.violinjourney.app.shared.resources.journey_stop_meta
import com.violinjourney.app.shared.resources.venue_play_here
import com.violinjourney.app.shared.resources.venue_play_here_caption
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The stop, the map and the passport of the redesign (spec 3.36.7, 5.29 R7; journey-home.html 3–5, landscape.html «Остановка»).
private val MaxContentWidth = 560.dp
private val ScreenSide = 16.dp
private val ScrollTop = 4.dp
private val ScrollEnd = 24.dp
private val LandscapeLeft = 360.dp
private val ColumnGap = 16.dp
private val PostcardShape = RoundedCornerShape(20.dp)

// The postcard of the stop and what lies on it: the plates 8 from its edges, the square «на весь экран» 8 from its corner.
private val OnPictureInset = 8.dp
private val PlatesGap = 8.dp

/** What the plates leave at the end of their row: the gap to the square «на весь экран», the square and its corner. */
private val PlatesReserve = OnPictureInset + GlassSquareDefaults.Size + OnPictureInset

// The words of the stop.
private val WordsTop = 12.dp
private val FactTop = 8.dp
private const val FACT_LINE_HEIGHT = 1.45f
private val WordsSize = 14.sp

// «Дополнения» and its rows (5.29 R7, «Остановка»).
private val ExtrasTop = 22.dp
private val ExtraGap = 8.dp
private val ExtraShape = RoundedCornerShape(16.dp)
private val ExtraMinHeight = 60.dp
private val ExtraPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
private val ExtraWordsGap = 12.dp
private val HintGap = 12.dp
private val HintUnder = 2.dp
private val OpenedHeight = 28.dp
private val OpenedSide = 10.dp
private val OpenedTick = 13.dp
private val OpenedGap = 4.dp
private val PriceSign = 14.dp

/** A price the takts are short of stands where the number of a pill stands: inside its capsule, by its side field. */
private val BarePriceEnd = 14.dp

// The card «вы здесь» of the map (5.29 R7, «Карта»).
private val HereShape = AppShapes.M
private val HereMinHeight = 64.dp
private val HerePadding = PaddingValues(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
private val HereThumbWidth = 64.dp
private val HereThumbHeight = 48.dp
private val HereThumbShape = RoundedCornerShape(10.dp)
private val HereGap = 12.dp
private val HereChevron = 24.dp
private val HereMargin = 16.dp
private val HereLyingWidth = 360.dp
private const val HERE_CITY_SP = 15f
private const val HERE_CITY_LEAST_SP = 12f

// The passport (5.29 R7, «Паспорт»): three stamps of 96 in a row, 14 between the rows, 6 between the columns.
private const val PASSPORT_COLUMNS = 3
private val PassportStamp = 96.dp
private val PassportRowGap = 14.dp
private val PassportColumnGap = 6.dp

/** Over the first row: the 4 of the scroll and the 14 of the grid of the mockup. */
private val PassportTop = 18.dp
private val CaptionTop = 4.dp
private val CellPadding = 4.dp
private val PassportCellShape = RoundedCornerShape(16.dp)
private val CaptionSize = 12.sp

/** The sticker of a souvenir stands this far out of the bottom end corner of its stamp, as it always did (spec 3.36.7: the sticker as it was). */
private val StickerOut = DpOffset(6.dp, 2.dp)

/** A caption under a stamp stays on one line — «Санкт-Петербург» in a column of 105 at a large font — smaller, down to this. */
private const val CAPTION_LEAST_SP = 10f
private val StickerPaper = Color(0xFFF1EEE6)
private val StickerInk = Color(0xFF2E1A6E)

/**
 * A stop the player has been to (spec 3.23, 3.36.7; handoff 26c): its postcard in the views that are open, with the plates of the
 * views on it, the line of the place and its fact, the extras; at the bottom the zone with «Играть здесь» — Live on the stage of this
 * city — and «Домой». Upright the zone is as wide as the column (560 at most) and the rest scrolls over it; lying it is one row under
 * the postcard in the left column of 360, and the words and the extras scroll on the right. A stop not reached has no zone.
 */
@Composable
fun StopScreen(state: StopState, onIntent: (StopIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val city = cityOf(state.index)
    if (state.fullscreen && !state.loading) {
        FullscreenPostcard(state, city, onIntent, modifier)
        return
    }
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        // the purse in the bar, where the prices of the extras are compared with it; not before it is read — no «0» for a moment
        JourneyTopBar(city, onBack = { onIntent(StopIntent.BackClicked) }) { if (!state.loading) BalancePill(state.balance) }
        if (state.loading) return@Column
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                LandscapeStop(state, city, onIntent, available = maxHeight)
            } else {
                PortraitStop(state, city, onIntent, available = maxHeight)
            }
        }
    }
}

@Composable
private fun PortraitStop(state: StopState, city: String, onIntent: (StopIntent) -> Unit, available: Dp) {
    val arrived = state.arrivedAtEpochMs != null
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AppDock(
            dock = { StopDock(state, onIntent, lying = false) },
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxHeight(),
            metrics = currentDockMetrics().copy(side = ScreenSide),
            // a stop not reached has no way to play there or to go home from: no zone, as before
            pinned = arrived,
        ) {
            val dock = LocalDockInset.current
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenSide, top = ScrollTop, end = ScreenSide, bottom = dock + ScrollEnd),
            ) {
                // the picture gives way first: the zone never shrinks (5.29 R7)
                StopCard(state, city, PictureFit.height(available, dock, PictureFit.PostcardMin, PictureFit.PostcardMax), onIntent)
                StopWords(state, Modifier.padding(top = WordsTop))
                Extras(state, onIntent, Modifier.padding(top = ExtrasTop))
            }
        }
    }
}

/**
 * Lying (spec 3.36.7, landscape.html «Остановка»): on the left the postcard and under it the zone in one row — «Играть здесь» on the
 * rest and «Домой» of its own width, 120 at least (D5: «Nach Hause» with its house does not fit 120); on the right the line of the
 * place, the fact and the extras scroll to the bottom. The postcard is 180 high, but never under the fade of the zone: in the
 * emulator's 640 × 360 (603 × 308 under its bars) its plates would stand in the fade — then it gives way, as the postcards of the
 * moments of the road do ([PictureFit.lying], 120 at least).
 */
@Composable
private fun LandscapeStop(state: StopState, city: String, onIntent: (StopIntent) -> Unit, available: Dp) {
    val arrived = state.arrivedAtEpochMs != null
    Row(Modifier.fillMaxSize().padding(horizontal = ScreenSide), horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
        AppDock(
            dock = { StopDock(state, onIntent, lying = true) },
            modifier = Modifier.width(LandscapeLeft).fillMaxHeight(),
            padSides = false,
            metrics = currentDockMetrics().copy(side = ScreenSide),
            pinned = arrived,
        ) {
            val dock = LocalDockInset.current
            val fade = if (arrived) DockDefaults.Fade else 0.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = ScrollTop, bottom = dock + ScrollEnd)) {
                StopCard(state, city, PictureFit.lying(available - ScrollTop - dock - fade), onIntent)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = ScrollTop, bottom = ScrollEnd)) {
            StopWords(state)
            Extras(state, onIntent, Modifier.padding(top = ExtrasTop))
        }
    }
}

/**
 * The postcard of the stop (spec 3.36.7), alive, in the views that are open: on it, at the bottom start, the plates «вечер | день» and
 * «снаружи | внутри» of what is bought ([StopPlates]); at the bottom end the square «на весь экран» on the glass. A tap on the postcard
 * or on the square gives it the whole screen.
 */
@Composable
private fun StopCard(state: StopState, city: String, height: Dp, onIntent: (StopIntent) -> Unit) {
    val whole = stringResource(Res.string.journey_fullscreen)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(PostcardShape)
            .clickable(onClickLabel = whole, role = Role.Button) { onIntent(StopIntent.PostcardClicked) },
    ) {
        Postcard(
            state.stop,
            description = stringResource(Res.string.journey_card_description, city),
            modifier = Modifier.fillMaxSize(),
            mode = if (state.day) SceneMode.DAY else SceneMode.EVENING,
            inside = state.inside,
            seconds = rememberSceneSeconds(),
        )
        // 8 from the edge of the picture to the seen capsule: a plate is 48 high, its capsule 40 in the middle
        StopPlates(state, onIntent, Modifier.align(Alignment.BottomStart).padding(start = OnPictureInset, bottom = OnPictureInset - TwoWayDefaults.Air))
        GlassSquare(AppIcons.Fullscreen, whole, onClick = { onIntent(StopIntent.PostcardClicked) }, modifier = Modifier.align(Alignment.BottomEnd).padding(OnPictureInset))
    }
}

/**
 * The plates of the views on the postcard (spec 3.36.7): «вечер | день» once the second time of day is bought, «снаружи | внутри» once
 * the second view is — only what is open is offered: a plate that did nothing would be a locked door. In one row, [PlatesGap] apart,
 * while the two fit beside the square «на весь экран» ([PlatesReserve]); else the second stands over the first (360 dp, and most
 * languages there). A plate is 48 high with its capsule of 40 in the middle: one over the other they are 8 apart.
 */
@Composable
private fun StopPlates(state: StopState, onIntent: (StopIntent) -> Unit, modifier: Modifier) {
    if (!state.dayUnlocked && !state.secondViewUnlocked) return
    val evening = stringResource(Res.string.journey_mode_evening)
    val day = stringResource(Res.string.journey_mode_day)
    val outside = stringResource(Res.string.journey_mode_outside)
    val inside = stringResource(Res.string.journey_mode_inside)
    Layout(
        content = {
            if (state.dayUnlocked) TwoWay(evening, day, state.day, { onIntent(StopIntent.DaySelected(it)) })
            if (state.secondViewUnlocked) TwoWay(outside, inside, state.inside, { onIntent(StopIntent.InsideSelected(it)) })
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val plates = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val gap = PlatesGap.roundToPx()
        val row = plates.sumOf { it.width } + gap * (plates.size - 1)
        if (row <= constraints.maxWidth - PlatesReserve.roundToPx()) {
            val height = plates.maxOf { it.height }
            layout(row, height) {
                var x = 0
                plates.forEach { plate ->
                    plate.place(x, height - plate.height)
                    x += plate.width + gap
                }
            }
        } else {
            // the second over the first: the first keeps its place at the bottom, where a single plate stands
            layout(plates.maxOf { it.width }, plates.sumOf { it.height }) {
                var y = 0
                plates.asReversed().forEach { plate ->
                    plate.place(0, y)
                    y += plate.height
                }
            }
        }
    }
}

/**
 * The zone of the stop (spec 3.36.7): «Играть здесь» with the theatre and «Live · Золотой зал» under it — the player goes to this city
 * and Live opens on the stage of its hall (3.27); «Домой» with the house — the player goes home, through its title card. Upright the
 * two stand one under the other, «Домой» an outline of 48; [lying] — in one row, «Домой» as high as the main one and 120 wide at least.
 * The outline is the zone's outline in either (5.29 R7, «Общее»: `compact`, fields of 16 and 15 sp) — in the row it only takes the
 * height of the main one: the fields of 24 of an outline of 56 would make «Домой» 130 in Russian and take the room of «Live · …».
 */
@Composable
private fun DockScope.StopDock(state: StopState, onIntent: (StopIntent) -> Unit, lying: Boolean) {
    val play = stringResource(Res.string.venue_play_here)
    val hall = hallOf(state.index)
    val caption = if (hall.isEmpty()) null else stringResource(Res.string.venue_play_here_caption, hall)
    val home = stringResource(Res.string.journey_go_home)
    if (lying) {
        // the zone's height class, read out of its scope: a row is a layout scope of its own
        val low = compact
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
            AppButton(
                text = play,
                onClick = { onIntent(StopIntent.PlayHereClicked) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                icon = AppIcons.Theatre,
                caption = caption,
                compact = low,
                oneLine = true,
            )
            AppButton(
                text = home,
                onClick = { onIntent(StopIntent.HomeClicked) },
                modifier = Modifier.widthIn(min = HomeLyingMin).fillMaxHeight(),
                style = AppButtonStyle.Outline,
                icon = AppIcons.House,
                compact = true,
            )
        }
    } else {
        AppButton(
            text = play,
            onClick = { onIntent(StopIntent.PlayHereClicked) },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.Theatre,
            caption = caption,
            compact = compact,
            oneLine = true,
        )
        AppButton(
            text = home,
            onClick = { onIntent(StopIntent.HomeClicked) },
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonStyle.Outline,
            icon = AppIcons.House,
            compact = true,
        )
    }
}

/** «Домой» lying, in the row of «Играть здесь»: 120 at least, wider where its words ask for it (D5). */
private val HomeLyingMin = 120.dp

/**
 * The line of the place in one — «Музикферайн, Золотой зал · Австрия · 4 из 16 · с 20 сентября» (spec 3.36.7) — going on to more lines
 * where it must, never cut; under it the fact.
 */
@Composable
private fun StopWords(state: StopState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val since = state.arrivedAtEpochMs?.takeIf { state.index > 0 }?.let {
        stringResource(Res.string.journey_stop_meta, state.index, state.totalStops, Formats.dayAndMonth(it, TimeZone.currentSystemDefault()))
    }
    val words = MaterialTheme.typography.bodyMedium.copy(fontSize = WordsSize, lineHeight = 20.sp)
    Column(modifier) {
        Text(
            text = listOfNotNull(placeOf(state.index).takeIf { it.isNotEmpty() }, countryOf(state.index).takeIf { it.isNotEmpty() }, since)
                .joinToString(stringResource(Res.string.dot_separator)),
            color = colors.onSurfaceVariant,
            style = words,
        )
        Text(
            text = factOf(state.index),
            modifier = Modifier.padding(top = FactTop),
            color = colors.onSurfaceVariant,
            style = words.copy(lineHeight = WordsSize * FACT_LINE_HEIGHT),
        )
    }
}

/**
 * «Дополнения» — a heading — and «маленькие цели в дороге» at its end (spec 3.36.7); under them a card for each extra: its name, what it
 * gives, and at the end «открыто», the price in an outlined pill that buys it (one transaction, 5.17), or — the takts short — the price
 * in words, grey: it tells, it does not sleep as a button. Nothing to offer (home, a sketch) — no section.
 */
@Composable
private fun Extras(state: StopState, onIntent: (StopIntent) -> Unit, modifier: Modifier = Modifier) {
    if (state.offers.isEmpty()) return
    Column(modifier) {
        HeadingWithHint(stringResource(Res.string.journey_extras), stringResource(Res.string.journey_extras_hint))
        state.offers.forEach { offer -> ExtraRow(state, offer, onIntent, Modifier.padding(top = ExtraGap)) }
    }
}

/**
 * A [heading] with its [hint] at the end of its line, on its baseline — or, where the two do not fit one line, the hint under it at the
 * start: a long hint at a large font goes on under the heading rather than squeeze it.
 */
@Composable
private fun HeadingWithHint(heading: String, hint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Layout(
        content = {
            Text(
                text = heading,
                modifier = Modifier.semantics { heading() },
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(hint, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        },
        modifier = modifier.fillMaxWidth(),
    ) { (headingM, hintM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val head = headingM.measure(loose)
        val gap = HintGap.roundToPx()
        val room = width - head.width - gap
        if (room > 0 && hintM.maxIntrinsicWidth(Constraints.Infinity) <= room) {
            val note = hintM.measure(loose.copy(maxWidth = room))
            val headLine = head[FirstBaseline]
            val noteLine = note[FirstBaseline]
            val noteTop = (headLine - noteLine).coerceAtLeast(0)
            val height = maxOf(head.height, noteTop + note.height)
            layout(width, height) {
                head.place(0, 0)
                note.place(width - note.width, noteTop)
            }
        } else {
            val note = hintM.measure(loose)
            val under = HintUnder.roundToPx()
            layout(width, head.height + under + note.height) {
                head.place(0, 0)
                note.place(0, head.height + under)
            }
        }
    }
}

/**
 * One extra (spec 3.36.7): a card of 60 at least — the name, under it what it gives by its kind («день вместо вечера», «вид изнутри»
 * — «вид снаружи» at Cremona and Milan, whose main view is inside —, «наклейка рядом со штампом»; no names of halls), and at its end
 * what can be done: «открыто», the price that buys, or the price the takts are short of. For a reader one phrase — «Второй вид, вид
 * изнутри, 400 тактов», a button while it can be bought; «…, открыто»; «…, 400 тактов, не хватает 160».
 *
 * No word of the name or the note is broken to make room for the end ([WordsAndEnd]): lying in the emulator's 640 × 360 the column of
 * the words is 195, and «Zweite Tageszeit» beside «✓ geöffnet» had 69 dp — the end then stands under them. Every row is 60 at the
 * font 1: the pill lends its touch over and under its capsule to the fields of the row ([touchInRowFields]).
 */
@Composable
private fun ExtraRow(state: StopState, offer: ExtraOffer, onIntent: (StopIntent) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(extraTitle(offer.extra))
    val note = stringResource(extraNote(offer.extra, state.stop))
    val price = Formats.takts(offer.price.toLong())
    val short = !offer.bought && !offer.affordable
    val said = listOfNotNull(
        title,
        note,
        if (offer.bought) stringResource(Res.string.journey_extra_bought) else taktsInWords(offer.price.toLong()),
        if (short) stringResource(Res.string.journey_missing, Formats.takts((offer.price - state.balance).coerceAtLeast(0))) else null,
    ).joinToString(", ")
    val buy = { onIntent(StopIntent.BuyClicked(offer.extra)) }
    val numberStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val noteStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ExtraMinHeight)
            .clip(ExtraShape)
            .background(colors.surfaceContainer)
            // one phrase for a reader, a button only while it buys; the pill is the finger's
            .clearAndSetSemantics {
                contentDescription = said
                if (offer.affordable) {
                    role = Role.Button
                    onClick {
                        buy()
                        true
                    }
                }
            }
            .padding(ExtraPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        WordsAndEnd(
            whole = listOf(WholeText(title, titleStyle), WholeText(note, noteStyle)),
            modifier = Modifier.fillMaxWidth(),
            gap = ExtraWordsGap,
            words = {
                Column {
                    Text(title, color = colors.onSurface, style = titleStyle)
                    Text(note, color = colors.onSurfaceVariant, style = noteStyle)
                }
            },
        ) {
            when {
                offer.bought -> OpenedChip()
                offer.affordable -> PillButton(onClick = buy, modifier = Modifier.touchInRowFields()) {
                    TaktAmount(price, style = numberStyle.copy(fontWeight = FontWeight.ExtraBold), color = colors.onSurface, icon = PriceSign, iconColor = colors.primary, merge = false)
                }
                else -> TaktAmount(
                    price,
                    modifier = Modifier.padding(end = BarePriceEnd),
                    style = numberStyle.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurfaceVariant,
                    icon = PriceSign,
                    merge = false,
                )
            }
        }
    }
}

private fun extraTitle(extra: JourneyExtra): StringResource = when (extra) {
    JourneyExtra.SECOND_TIME -> Res.string.journey_extra_time
    JourneyExtra.SECOND_VIEW -> Res.string.journey_extra_view
    JourneyExtra.SOUVENIR -> Res.string.journey_extra_souvenir
}

/** What an extra gives, by its kind; the second view is the other side of the main one — inside at most stops, outside at Cremona and Milan. */
private fun extraNote(extra: JourneyExtra, stop: JourneyStop): StringResource = when (extra) {
    JourneyExtra.SECOND_TIME -> Res.string.journey_extra_time_note
    JourneyExtra.SECOND_VIEW -> if (stop.views.firstOrNull()?.inside == true) Res.string.journey_extra_view_outside else Res.string.journey_extra_view_inside
    JourneyExtra.SOUVENIR -> Res.string.journey_extra_souvenir_note
}

/** «✓ открыто»: a chip of 28 on the soft accent — what is bought, said as a word and a tick, not by colour alone. */
@Composable
private fun OpenedChip() {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.heightIn(min = OpenedHeight).clip(CircleShape).background(ViolinTheme.accentSoft).padding(horizontal = OpenedSide),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OpenedGap),
    ) {
        AppIcon(AppIcons.Check, contentDescription = null, size = OpenedTick, tint = accent)
        Text(
            stringResource(Res.string.journey_extra_bought),
            color = accent,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/**
 * The passport (spec 3.23, 3.36.7; handoff 26g): a stamp for every stop of the route, three in a row at 96 — the column is 560 at most,
 * so three lying too. Reached ones are the coloured stamps with the sticker of a souvenir, and a tap opens the stop; ahead — a dashed
 * circle with the name under it, the next one with «следующая» in the accent, those not drawn yet «скоро». For a reader a stamp ahead
 * is «Прага — ещё впереди, следующая».
 */
@Composable
fun PassportScreen(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(Res.string.journey_passport), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            if (!state.loading) Text(stringResource(Res.string.journey_passport_count, state.currentIndex, state.totalStops), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (state.loading) return@Column
        val stops = JourneyRoute.stops.drop(1)
        val next = stringResource(Res.string.journey_passport_next)
        val captionStyle = MaterialTheme.typography.labelMedium.copy(fontSize = CaptionSize, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(PASSPORT_COLUMNS),
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenSide, top = PassportTop, end = ScreenSide, bottom = ScrollEnd),
            verticalArrangement = Arrangement.spacedBy(PassportRowGap),
            horizontalArrangement = Arrangement.spacedBy(PassportColumnGap),
        ) {
            itemsIndexed(stops, key = { _, it -> it.id }) { position, stop ->
                val index = position + 1
                val visit = state.visited.firstOrNull { it.stop.id == stop.id }
                val city = cityOf(index)
                val isNext = visit == null && index == state.currentIndex + 1 && stop.available
                val ahead = stringResource(Res.string.journey_stamp_locked_description, city)
                val presses = remember { MutableInteractionSource() }
                Box(
                    if (visit != null) {
                        Modifier.clickable(presses, indication = null, onClickLabel = city, role = Role.Button) { onIntent(JourneyIntent.StopClicked(stop.id)) }
                    } else {
                        // one phrase: the circle and the words under it
                        Modifier.clearAndSetSemantics { contentDescription = if (isNext) "$ahead, $next" else ahead }
                    },
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = CellPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            StampView(
                                stopId = stop.id, index = index, visited = visit != null, city = city,
                                date = visit?.let { Formats.dayAndMonth(it.arrivedAtEpochMs, TimeZone.currentSystemDefault()) }.orEmpty(),
                                modifier = Modifier.rotate(if (visit != null) STAMP_TILTS[index % STAMP_TILTS.size] else 0f),
                                size = PassportStamp,
                            )
                            if (visit?.souvenir == true) Sticker(Modifier.align(Alignment.BottomEnd).offset(x = StickerOut.x, y = StickerOut.y))
                        }
                        if (visit == null) {
                            OneLineText(
                                text = if (stop.available) city else stringResource(Res.string.journey_passport_soon),
                                style = captionStyle,
                                minSp = CAPTION_LEAST_SP,
                                modifier = Modifier.padding(top = CaptionTop),
                                color = colors.onSurfaceVariant,
                            )
                            if (isNext) OneLineText(text = next, style = captionStyle.copy(fontWeight = FontWeight.Bold), minSp = CAPTION_LEAST_SP, color = colors.primary)
                        }
                    }
                    // the ripple of a cell in its rounded shape and over the stamp, as before — but the cell itself is not cut: the sticker of
                    // a souvenir stands out of the stamp, and on 360 a column of 105 holds the stamp of 96 with 4.7 at its sides (5.29 R7)
                    if (visit != null) Box(Modifier.matchParentSize().clip(PassportCellShape).indication(presses, ripple()))
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

/**
 * The whole route (spec 3.23, 3.36.7; handoff 26e): drag and pinch; a tap on a city already reached opens it. Under the scheme — never
 * over it: Buenos Aires and Sydney would hide under it on 360 × 640 — the card «вы здесь» ([HereCard]); the scheme fits the room over
 * it. Lying the card stands 360 wide at the bottom start, the scheme fits the rest on its right.
 */
@Composable
fun MapScreen(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface)) {
        JourneyTopBar(stringResource(Res.string.journey_map), onBack = { onIntent(JourneyIntent.BackClicked) }) {
            if (!state.loading) Text(stringResource(Res.string.journey_passport_count, state.currentIndex, state.totalStops), color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        if (state.loading) return@Column
        val onStopTap = { index: Int -> onIntent(JourneyIntent.StopClicked(JourneyRoute.stops[index].id)) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(HereLyingWidth + HereMargin * 2).fillMaxHeight().padding(HereMargin), contentAlignment = Alignment.BottomStart) {
                        HereCard(state, onIntent, Modifier.fillMaxWidth())
                    }
                    JourneyMapCanvas(reached = state.currentIndex, interactive = true, modifier = Modifier.weight(1f).fillMaxHeight(), onStopTap = onStopTap)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    JourneyMapCanvas(reached = state.currentIndex, interactive = true, modifier = Modifier.weight(1f).fillMaxWidth(), onStopTap = onStopTap)
                    HereCard(state, onIntent, Modifier.fillMaxWidth().padding(start = HereMargin, end = HereMargin, bottom = HereMargin))
                }
            }
        }
    }
}

/**
 * «Вы здесь» (spec 3.36.7, 5.29 R7): the stop where the road stands — the same as on the postcard of the journey, not «где мы» of 3.27
 * — its still postcard, the city, and «вы здесь · до Праги хватает» / «вы здесь · до Праги 1 128» / «вы здесь · маршрут пройден». A tap
 * is a tap on its point of the map: its stop; at home — «мы дома» and the title card home. For a reader one phrase, a button.
 */
@Composable
private fun HereCard(state: JourneyState, onIntent: (JourneyIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val index = state.currentIndex
    val city = cityOf(index)
    val here = stringResource(Res.string.journey_here)
    val tail = when {
        state.next == null -> stringResource(Res.string.journey_here_end)
        state.canDepart -> stringResource(Res.string.journey_here_enough, cityToOf(index + 1))
        else -> stringResource(Res.string.journey_next_short, cityToOf(index + 1), Formats.takts(state.missing))
    }
    val dot = stringResource(Res.string.dot_separator)
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) { append(here) }
        append(dot)
        append(tail)
    }
    val said = listOf(city, here, tail).joinToString(", ")
    Row(
        modifier = modifier
            .heightIn(min = HereMinHeight)
            .clip(HereShape)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button) { onIntent(JourneyIntent.StopClicked(state.current.id)) }
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            }
            .padding(HerePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HereGap),
    ) {
        Box(Modifier.size(HereThumbWidth, HereThumbHeight).clip(HereThumbShape)) {
            // still: one picture alive on the screen is the map's business, not the card's
            StopPostcard(state.current, description = "", modifier = Modifier.fillMaxSize(), homeOutside = true)
        }
        Column(Modifier.weight(1f)) {
            // a word of the city is never broken — «Санкт-Петербург» at the largest font steps down rather than break at its hyphen
            Text(
                city,
                color = colors.onSurface,
                autoSize = remember { WholeWordsFit(HERE_CITY_SP, HERE_CITY_LEAST_SP) },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = HERE_CITY_SP.sp, lineHeight = 1.3.em, fontWeight = FontWeight.Bold),
            )
            Text(line, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = HereChevron, tint = ViolinTheme.textTertiary)
    }
}
