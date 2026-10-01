package com.violinjourney.app.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeHouse
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.DockDefaults
import com.violinjourney.app.core.ui.components.DockScope
import com.violinjourney.app.core.ui.components.KeepScreenOn
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.ShiftedInteractionSource
import com.violinjourney.app.core.ui.components.ShortfallPlate
import com.violinjourney.app.core.ui.components.WholeText
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.components.WordsAndEnd
import com.violinjourney.app.core.ui.components.appButtonsSharedSize
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.art.HomePicture
import com.violinjourney.app.feature.home.art.HomeSilhouettes
import com.violinjourney.app.feature.home.art.ItemThumb
import com.violinjourney.app.feature.journey.BalancePill
import com.violinjourney.app.feature.journey.GlassSquare
import com.violinjourney.app.feature.journey.GlassSquareDefaults
import com.violinjourney.app.feature.journey.JourneyMotion
import com.violinjourney.app.feature.journey.JourneyTopBar
import com.violinjourney.app.feature.journey.PictureFit
import com.violinjourney.app.feature.journey.PillButton
import com.violinjourney.app.feature.journey.PriceBar
import com.violinjourney.app.feature.journey.TaktIcon
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.rememberSceneCamera
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.feature.journey.art.sceneCamera
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.journey.cityToOf
import com.violinjourney.app.feature.journey.sessionsInWords
import com.violinjourney.app.feature.journey.taktsInWords
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.home_all_houses
import com.violinjourney.app.shared.resources.home_arrange
import com.violinjourney.app.shared.resources.home_fullscreen
import com.violinjourney.app.shared.resources.home_fullscreen_close
import com.violinjourney.app.shared.resources.home_fullscreen_hint
import com.violinjourney.app.shared.resources.home_gift_take
import com.violinjourney.app.shared.resources.home_gift_text
import com.violinjourney.app.shared.resources.home_gift_title
import com.violinjourney.app.shared.resources.home_have
import com.violinjourney.app.shared.resources.home_house_soon
import com.violinjourney.app.shared.resources.home_next_house
import com.violinjourney.app.shared.resources.home_outside
import com.violinjourney.app.shared.resources.home_picture_outside
import com.violinjourney.app.shared.resources.home_picture_room
import com.violinjourney.app.shared.resources.home_room
import com.violinjourney.app.shared.resources.home_shop
import com.violinjourney.app.shared.resources.home_things_brought
import com.violinjourney.app.shared.resources.home_things_few
import com.violinjourney.app.shared.resources.home_things_many
import com.violinjourney.app.shared.resources.home_things_none
import com.violinjourney.app.shared.resources.home_things_one
import com.violinjourney.app.shared.resources.home_title
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.home_travel_end
import com.violinjourney.app.shared.resources.home_travel_enough
import com.violinjourney.app.shared.resources.home_travel_line
import com.violinjourney.app.shared.resources.houses_enough
import com.violinjourney.app.shared.resources.houses_live
import com.violinjourney.app.shared.resources.houses_live_here
import com.violinjourney.app.shared.resources.houses_move
import com.violinjourney.app.shared.resources.houses_moved_cat
import com.violinjourney.app.shared.resources.houses_moved_things
import com.violinjourney.app.shared.resources.houses_moving
import com.violinjourney.app.shared.resources.houses_moving_text
import com.violinjourney.app.shared.resources.houses_news
import com.violinjourney.app.shared.resources.houses_of
import com.violinjourney.app.shared.resources.houses_title
import com.violinjourney.app.shared.resources.journey_have_description
import com.violinjourney.app.shared.resources.shop_missing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

val HomeMaxWidth = 560.dp
val HomeCard = RoundedCornerShape(16.dp)
private val PictureShape = RoundedCornerShape(20.dp)

/** The house of the film of moving in (handoff 27e3): the height it had on the screen of the home before R7. */
private val MovingPicture = 260.dp

// The home of the redesign (spec 3.36.7, 5.29 R7; journey-home.html 6 and 10, landscape.html 4).
private val ScreenSide = 16.dp
private val ScrollTop = 4.dp
private val ScrollEnd = 24.dp
private val ColumnGap = 16.dp
private val AboutGap = 14.dp
private val AboutWidth = 320.dp

/** Lying, the left column of the room is this wide at least for «В дорогу» · «Лавка» · «Обставить» in one row (640 × 360 is narrower). */
private val RowOfThreeFrom = 440.dp

/** Where the words of «Лавка» and «Обставить» do not fit their halves at 15 sp, both step down together to this, then lose their icons. */
private const val HALVES_LEAST_SP = 13f

// What lies on the room: «Комната | Снаружи» and the square «на весь экран», 8 from the edges of the picture.
private val OnPictureInset = 8.dp

// The name of the home and the line of its things.
private val NameSize = 26.sp
private const val NAME_LEAST_SP = 20f
private val ThingsTop = 2.dp

// The gift (5.29 R7, «Дом»): a 135° gradient from the soft accent to the card, an inner outline of the accent at 35 %.
private val GiftShape = RoundedCornerShape(16.dp)
private val GiftPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
private val GiftOutline = 1.dp
private const val GIFT_OUTLINE_ALPHA = 0.35f
private val GiftThumb = 64.dp
private val GiftThumbShape = RoundedCornerShape(12.dp)
private const val GIFT_THUMB_GROUND_ALPHA = 0.5f
private val GiftGap = 12.dp

/** The grid the silhouettes of the homes are drawn on (`HomeSilhouettes`). */
private const val SILHOUETTE_GRID_WIDTH = 200f
private const val SILHOUETTE_GRID_HEIGHT = 120f

/** The line of a home not drawn yet, in the row of the next home and in «Дома»: the `stroke-width` 2 of the mockups' silhouettes. */
private val SilhouetteOutline = 2.dp

// The row of the next home (5.29 R7, «Дом»).
private val NextShape = RoundedCornerShape(16.dp)
private val NextMinHeight = 64.dp
private val NextPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
private val NextSilhouetteWidth = 52.dp
private val NextSilhouetteHeight = 44.dp
private val NextGap = 12.dp
private val BarTop = 8.dp
private val Chevron = 24.dp

// The rows of «Дома» (5.29 R7, «Дома»).
private val HouseShape = RoundedCornerShape(18.dp)
private val HousePadding = 14.dp
private val HouseGap = 14.dp
private val HousesGap = 12.dp
private val HouseSilhouetteWidth = 84.dp
private val HouseSilhouetteHeight = 52.dp
private val HereBorder = 1.5.dp
private val HereChipHeight = 22.dp
private val HereChipSide = 8.dp
private val HereChipUnder = 4.dp
private const val HOUSE_NAME_LINES = 2
private const val HOUSE_NAME_SP = 16f
private const val HOUSE_NAME_LEAST_SP = 13f

// The sheet of a home (5.29 R7, «Дома»): the house outside by day, 200 high; the buttons pinned at its bottom.
private val SheetPicture = 200.dp
private val SheetGap = 12.dp
private val SheetButtonsTop = 18.dp
private val SessionsTop = 6.dp

/** The sign of a takt in the plate of what is missing — as in the plate of the journey; the card of a thing measures its plate with it. */
internal val PlateSign = 18.dp

@Composable
fun itemName(id: String): String = HomeTexts.itemNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
fun itemNote(id: String): String = HomeTexts.itemNotes[id]?.let { stringResource(it) }.orEmpty()

@Composable
fun slotName(id: String): String = HomeTexts.slotNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
fun houseName(id: String): String = HomeTexts.houseNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun houseNote(id: String): String = HomeTexts.houseNotes[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun thingsInWords(count: Int): String = stringResource(Formats.plural(count, Res.string.home_things_one, Res.string.home_things_few, Res.string.home_things_many), count)

/** The sizes of [TwoWay] (spec 5.29 R7, «Стекло на картинах»). */
object TwoWayDefaults {
    /** It is pressed over this height. */
    val Height = 48.dp

    /** The capsule seen: a field of 3 round a segment of 34. */
    val Capsule = 40.dp

    /** Over and under the seen capsule, inside the 48: whoever stands it 8 from the edge of a picture pads it by 8 less this. */
    val Air = (Height - Capsule) / 2
}

private val TwoWayField = 3.dp
private val TwoWaySegment = 34.dp
private val TwoWaySide = 13.dp
private val TwoWayWords = 13.sp

/** The solid ground of [TwoWay] in the bar of «Обставить», where there is no picture under it: as it always was. */
private const val TWO_WAY_SOLID_ALPHA = 0.78f

/**
 * Two words on one track (spec 3.36.7, 5.29 R7): «Комната | Снаружи», «вечер | день», «снаружи | внутри». A capsule of 40 — a field
 * of 3 round a segment of 34 — on the smoked glass of 0.72 over a picture ([onPicture]; on the solid ground of the screen at 0.78 in
 * the bar of «Обставить»), the chosen word on the accent pill; 13 sp / 700. It is laid out 48 high — pressed over the whole of it —
 * and draws its capsule and its pill in the middle of that, so the touch of 48 does not make the plate seen any larger: whoever
 * stands it 8 from an edge pads it by 8 less [TwoWayDefaults.Air] on that side. Two tabs of one group: TalkBack and VoiceOver say
 * which side is chosen, not only what each is called.
 */
@Composable
fun TwoWay(first: String, second: String, secondChosen: Boolean, onChoose: (second: Boolean) -> Unit, modifier: Modifier = Modifier, onPicture: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val ground = if (onPicture) ViolinTheme.glass else colors.surface.copy(alpha = TWO_WAY_SOLID_ALPHA)
    Row(
        modifier = modifier
            .height(TwoWayDefaults.Height)
            .drawBehind {
                val capsule = TwoWayDefaults.Capsule.toPx()
                drawRoundRect(ground, topLeft = Offset(0f, (size.height - capsule) / 2), size = Size(size.width, capsule), cornerRadius = CornerRadius(capsule / 2))
            }
            .padding(horizontal = TwoWayField)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(first to false, second to true).forEach { (word, isSecond) ->
            val chosen = isSecond == secondChosen
            val interaction = remember { MutableInteractionSource() }
            // the press comes in the coordinates of the whole segment of 48, the ripple is drawn in the pill of 34
            val pillPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .selectable(selected = chosen, interactionSource = interaction, indication = null, role = Role.Tab) { onChoose(isSecond) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(TwoWaySegment)
                        .onPlaced { pillPresses.shift = it.positionInParent() }
                        .clip(CircleShape)
                        .background(if (chosen) colors.primary else Color.Transparent)
                        .indication(pillPresses, ripple())
                        .padding(horizontal = TwoWaySide),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = word,
                        color = if (chosen) colors.onPrimary else colors.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = TwoWayWords, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/**
 * The home as its own section (spec 3.24, 3.36.7; handoff 27a): the room alive and what it is, the gift while it waits, the next home;
 * the three doors — «В дорогу», «Лавка», «Обставить» — in the bottom zone, under the thumb at any scroll. Upright the room gives way
 * first ([PictureFit], 200…290) and scrolls with the words over the zone; lying ([HomeRoomFit.lying]: a window wider than high where
 * the room keeps 200 beside the words) the room fills the left column over the zone and does not scroll — no fade over the zone — while
 * the words scroll in the right column of 320. Loading — the bar alone.
 */
@Composable
fun HomeScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (ui.fullscreen && !ui.loading) {
        FullscreenHome(ui, onIntent, modifier)
        return
    }
    val window = LocalWindowInfo.current.containerSize
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        // the purse not before it is read: no «0» for a moment
        JourneyTopBar(stringResource(Res.string.home_title), onBack = { onIntent(HomeIntent.BackClicked) }) { if (!ui.loading) BalancePill(ui.balance) }
        if (ui.loading) return@Column
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (HomeRoomFit.lying(window.width > window.height, maxWidth)) LandscapeHome(ui, onIntent) else PortraitHome(ui, onIntent, available = maxHeight)
        }
    }
}

@Composable
private fun PortraitHome(ui: HomeUi, onIntent: (HomeIntent) -> Unit, available: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AppDock(
            dock = { HomeDock(ui, onIntent) },
            modifier = Modifier.widthIn(max = HomeMaxWidth).fillMaxHeight(),
            fade = HomeRoomFit.fade(lying = false),
            metrics = currentDockMetrics().copy(side = ScreenSide),
        ) {
            val dock = LocalDockInset.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenSide, top = ScrollTop, end = ScreenSide, bottom = dock + ScrollEnd),
                verticalArrangement = Arrangement.spacedBy(AboutGap),
            ) {
                // the room gives way first: the zone never shrinks (5.29 R7)
                Picture(ui, onIntent, Modifier.fillMaxWidth().height(PictureFit.height(available, dock, PictureFit.RoomMin, PictureFit.RoomMax)))
                About(ui, onIntent)
            }
        }
    }
}

/**
 * Lying (landscape.html 4): on the left the room on the whole height of its column and under it the zone — «В дорогу» on the rest,
 * «Лавка» and «Обставить» by their words in one row where the column is [RowOfThreeFrom] wide, else two rows as upright; the room does
 * not scroll, so there is no fade over the zone. On the right the name, the things, the gift and the next home, to the bottom.
 */
@Composable
private fun LandscapeHome(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = ScreenSide), horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val inRow = maxWidth >= RowOfThreeFrom
            AppDock(
                dock = { if (inRow) HomeDockRow(ui, onIntent) else HomeDock(ui, onIntent) },
                modifier = Modifier.fillMaxSize(),
                fade = HomeRoomFit.fade(lying = true),
                padSides = false,
                metrics = currentDockMetrics().copy(side = ScreenSide),
            ) {
                Picture(ui, onIntent, Modifier.fillMaxSize().padding(top = ScrollTop, bottom = LocalDockInset.current))
            }
        }
        Column(
            modifier = Modifier.width(AboutWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = ScrollTop, bottom = ScrollEnd),
            verticalArrangement = Arrangement.spacedBy(AboutGap),
        ) { About(ui, onIntent) }
    }
}

/**
 * The zone of the home (spec 3.36.7): «В дорогу» on the whole width with the case and its line — «Вена → до Праги 1 128», «Вена →
 * хватает до Праги», «Сидней · маршрут пройден» — and under it «Лавка» and «Обставить», outlines of 48 by halves ([HomeHalves]).
 */
@Composable
private fun DockScope.HomeDock(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    TravelButton(ui, onIntent, Modifier.fillMaxWidth(), compact)
    HomeHalves(onIntent)
}

/**
 * Lying, in a column of [RowOfThreeFrom] at least: «В дорогу» on the rest and «Лавка» · «Обставить» by their words, of one height. The
 * two are the zone's outlines (5.29 R7: `compact` — fields of 16, 15 sp), taking only the height of the main one: the fields of 24 of
 * an outline of 56 took some 40 dp from «В дорогу», and its line «Вена → хватает до Праги» was cut on the emulator lying (a column
 * of 494 by its cutout).
 */
@Composable
private fun DockScope.HomeDockRow(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    // the zone's height class, read out of its scope: a row is a layout scope of its own
    val low = compact
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
        TravelButton(ui, onIntent, Modifier.weight(1f).fillMaxHeight(), low)
        AppButton(
            text = stringResource(Res.string.home_shop),
            onClick = { onIntent(HomeIntent.ShopClicked) },
            modifier = Modifier.fillMaxHeight(),
            style = AppButtonStyle.Outline,
            icon = AppIcons.Shop,
            compact = true,
        )
        AppButton(
            text = stringResource(Res.string.home_arrange),
            onClick = { onIntent(HomeIntent.ArrangeClicked) },
            modifier = Modifier.fillMaxHeight(),
            style = AppButtonStyle.Outline,
            icon = AppIcons.Arrange,
            compact = true,
        )
    }
}

/**
 * «Лавка» and «Обставить» by halves, 10 apart and of one height (spec 3.36.7, 5.29 R7: outlines of 48). No word breaks: where «Обставить»
 * does not fit its half beside its icon — the emulator's 640 × 360 lying is a left column of 235, a large font — both words step down
 * together to [HALVES_LEAST_SP] ([appButtonsSharedSize]); where not even that holds them, the halves keep their words without the icons;
 * and where not even so, they stand one under the other on the whole width.
 */
@Composable
private fun HomeHalves(onIntent: (HomeIntent) -> Unit) {
    val shop = stringResource(Res.string.home_shop)
    val arrange = stringResource(Res.string.home_arrange)
    val onShop = { onIntent(HomeIntent.ShopClicked) }
    val onArrange = { onIntent(HomeIntent.ArrangeClicked) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val half = (maxWidth - DockDefaults.RowGap) / 2
        val buttons = listOf(shop to AppButtonStyle.Outline, arrange to AppButtonStyle.Outline)
        val withIcons = appButtonsSharedSize(buttons, half, compact = true, minSp = HALVES_LEAST_SP, icons = true)
        val bare = appButtonsSharedSize(buttons, half, compact = true, minSp = HALVES_LEAST_SP)
        val size = withIcons ?: bare
        if (size != null) {
            val icons = withIcons != null
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
                AppButton(
                    shop, onShop, Modifier.weight(1f).fillMaxHeight(), style = AppButtonStyle.Outline,
                    icon = if (icons) AppIcons.Shop else null, compact = true, fontSize = size,
                )
                AppButton(
                    arrange, onArrange, Modifier.weight(1f).fillMaxHeight(), style = AppButtonStyle.Outline,
                    icon = if (icons) AppIcons.Arrange else null, compact = true, fontSize = size,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DockDefaults.RowGap)) {
                AppButton(shop, onShop, Modifier.fillMaxWidth(), style = AppButtonStyle.Outline, icon = AppIcons.Shop, compact = true)
                AppButton(arrange, onArrange, Modifier.fillMaxWidth(), style = AppButtonStyle.Outline, icon = AppIcons.Arrange, compact = true)
            }
        }
    }
}

/**
 * «В дорогу» (spec 3.25, 3.36.7): the case with its arrow and the line of the road under the words, each on one line — the number of
 * what is missing never cut, the words around it give way ([AppButton]'s `keep`). The player leaves home for where the road stands.
 */
@Composable
private fun TravelButton(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier, compact: Boolean) {
    val at = JourneyRules.currentIndex(ui.progress)
    val next = JourneyRules.next(ui.progress)
    val short = next != null && !JourneyRules.enough(ui.progress)
    val missing = Formats.takts(JourneyRules.missing(ui.progress))
    val line = when {
        next == null -> stringResource(Res.string.home_travel_end, cityOf(at))
        !short -> stringResource(Res.string.home_travel_enough, cityOf(at), cityToOf(at + 1))
        else -> stringResource(Res.string.home_travel_line, cityOf(at), cityToOf(at + 1), missing)
    }
    AppButton(
        text = stringResource(Res.string.home_travel),
        onClick = { onIntent(HomeIntent.TravelClicked) },
        modifier = modifier,
        icon = AppIcons.Travel,
        caption = line,
        compact = compact,
        oneLine = true,
        keep = if (short) missing else null,
    )
}

/**
 * The room alive (spec 3.24, 3.36.7), or the home from outside: «Комната | Снаружи» on the glass in its top end corner — at the start
 * it hid the portrait on the wall — and the square «на весь экран» at its bottom end, where the room holds both ([HomeRoomFit]). A
 * tap on the room or on the square gives it the whole screen.
 */
@Composable
private fun Picture(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier) {
    val name = houseName(ui.house)
    val whole = stringResource(Res.string.home_fullscreen)
    BoxWithConstraints(modifier.clip(PictureShape).clickable(onClickLabel = whole, role = Role.Button) { onIntent(HomeIntent.FullscreenClicked) }) {
        HomePicture(
            ui.home, ui.outside,
            description = stringResource(if (ui.outside) Res.string.home_picture_outside else Res.string.home_picture_room, name),
            modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(),
        )
        TwoWay(
            stringResource(Res.string.home_room), stringResource(Res.string.home_outside), ui.outside, { onIntent(HomeIntent.SideSelected(it)) },
            Modifier.align(Alignment.TopEnd).padding(top = OnPictureInset - TwoWayDefaults.Air, end = OnPictureInset),
        )
        if (HomeRoomFit.squareFits(maxHeight)) {
            GlassSquare(AppIcons.Fullscreen, whole, onClick = { onIntent(HomeIntent.FullscreenClicked) }, modifier = Modifier.align(Alignment.BottomEnd).padding(OnPictureInset))
        }
    }
}

/**
 * What lies on the room of the home and over its zone (spec 3.36.7, 5.29 R7). Pure, with a test.
 *
 * - [fade] — upright the words scroll under the zone and fade into it (28, as every zone); lying the room fills its column to the top
 *   of the zone and does not scroll, so nothing runs under the zone and there is no fade — it would only dim the floor of the room.
 * - [squareFits] — the square «на весь экран» stands at the bottom end of the room only where it clears «Комната | Снаружи» at the
 *   top end: the 48 of the switch 4 under the top of the room, 8, the square of 48 and its 8 — [SquareFrom], 116. Lying in a low
 *   window at a large font the halves of the zone stand one under the other and the room is some 66 to 71 high (603 × 308 at 1.15
 *   and 1.3): the square would lie on «Снаружи» and take its touches. The room itself opens the whole screen all the same, and a
 *   reader hears that as its action.
 * - [lying] — the home and «Обставить» stand lying (the room on the left, a column of [ColumnBeside] on the right) in a window wider
 *   than high, and only where the room keeps [RoomBesideLeast] beside that column ([roomBeside]): the box under the bar of a window
 *   higher than wide can be wider than high (the half of a split screen, 412 × 450: 412 × 346 under the bar of 56), and a window wider
 *   than high can be narrow (the half of a phone lying, 456 × 411) — beside 320 the room was 44 and 88 dp there, a strip. Such
 *   windows stand upright.
 */
object HomeRoomFit {
    val SquareFrom: Dp
        get() = (OnPictureInset - TwoWayDefaults.Air) + TwoWayDefaults.Height + OnPictureInset + GlassSquareDefaults.Size + OnPictureInset

    /** Lying, the words of the home and the places of «Обставить» stand in a column this wide on the right. */
    val ColumnBeside: Dp get() = AboutWidth

    /** The fields of the screen at its sides, and between the room and the column lying. */
    val Side: Dp get() = ScreenSide
    val Gap: Dp get() = ColumnGap

    /** The least the room is beside the column: no narrower than it is ever high upright ([PictureFit.RoomMin]). */
    val RoomBesideLeast: Dp get() = PictureFit.RoomMin

    fun fade(lying: Boolean): Dp = if (lying) 0.dp else DockDefaults.Fade

    fun squareFits(room: Dp): Boolean = room >= SquareFrom

    /** What is left to the room beside the column in a box [width] wide: its fields and the gap taken. */
    fun roomBeside(width: Dp): Dp = width - Side * 2 - Gap - ColumnBeside

    /** Lying: a window wider than high ([windowWide]) and the room keeping [RoomBesideLeast] beside the column in [width]. */
    fun lying(windowWide: Boolean, width: Dp): Boolean = windowWide && roomBeside(width) >= RoomBesideLeast
}

/**
 * The home on the whole screen (spec 3.25, 3.36.7, handoff 28g). Unlike a city it opens seen whole, by its width: the rooms have a
 * ceiling above and a floor towards the viewer for that. A pinch comes closer, a double tap walks round the zooms; the panel leaves by
 * itself; the screen stays on. The way out and «Комната | Снаружи» stand on the glass.
 */
@Composable
private fun FullscreenHome(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val camera = rememberSceneCamera()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var panel by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(size) { if (size != IntSize.Zero) camera.whole(size.width.toFloat(), size.height.toFloat()) }
    LaunchedEffect(panel, touches) {
        if (!panel) return@LaunchedEffect
        delay(JourneyMotion.FULLSCREEN_PANEL_HIDE_MS)
        panel = false
    }
    KeepScreenOn()
    val name = houseName(ui.house)
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }.sceneCamera(camera) { panel = !panel }) {
        HomePicture(
            ui.home, ui.outside, description = stringResource(if (ui.outside) Res.string.home_picture_outside else Res.string.home_picture_room, name),
            modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(), camera = camera::read,
        )
        val fade = if (reduce) 0 else JourneyMotion.FULLSCREEN_PANEL_FADE_MS
        androidx.compose.animation.AnimatedVisibility(visible = panel, enter = fadeIn(tween(fade)), exit = fadeOut(tween(fade)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                        .padding(start = OnPictureInset, end = OnPictureInset, top = 8.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassSquare(AppIcons.FullscreenExit, stringResource(Res.string.home_fullscreen_close), onClick = { onIntent(HomeIntent.FullscreenClosed) })
                    Text(name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TwoWay(stringResource(Res.string.home_room), stringResource(Res.string.home_outside), ui.outside, { touches++; onIntent(HomeIntent.SideSelected(it)) })
                }
                Text(
                    stringResource(Res.string.home_fullscreen_hint),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))).padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 20.dp),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * What the home is (spec 3.36.7): its name, 26 sp / 800 — never broken inside a word, smaller down to 20 sp where a word does not fit
 * its line —, the line of its things; the gift while it waits ([GiftCard]); the next home ([NextHouseRow]). The buttons are the zone's.
 */
@Composable
private fun About(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (things, brought) = HomeRules.counts(ui.home)
    Column {
        Text(
            text = houseName(ui.house),
            color = colors.onSurface,
            autoSize = remember { WholeWordsFit(NameSize.value, NAME_LEAST_SP) },
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = NameSize, lineHeight = 1.15.em, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        )
        Text(
            text = when {
                things == 0 -> stringResource(Res.string.home_things_none)
                brought == 0 -> thingsInWords(things)
                else -> stringResource(Res.string.home_things_brought, thingsInWords(things), brought)
            },
            modifier = Modifier.padding(top = ThingsTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
    if (HomeRules.giftWaiting(ui.home)) GiftCard(onIntent)
    NextHouseRow(HomeRules.nextHouse(ui.home), ui.balance, onClick = { onIntent(HomeIntent.HousesClicked) })
}

/**
 * The first gift, while it waits (spec 3.36.7, handoff 27a3): the shop begins with a joy, and it is taken right here — no going to the
 * shelf for it. The stand, «Первый подарок ждёт в лавке», what it is, and «Забрать» — the main one of 48, beside the words while they
 * keep their words whole there, else under them at the end ([WordsAndEnd]: lying, in the column of 320 at the font 1.3, «Бесплатно.»
 * and «Geschenk» beside «Забрать» broke by the letter). For a reader the title and the text are one phrase, «Забрать» a button.
 */
@Composable
private fun GiftCard(onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.home_gift_title)
    val text = stringResource(Res.string.home_gift_text)
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GiftShape)
            .background(Brush.linearGradient(listOf(ViolinTheme.accentSoft, colors.surfaceContainer)))
            .border(GiftOutline, colors.primary.copy(alpha = GIFT_OUTLINE_ALPHA), GiftShape)
            .padding(GiftPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftGap),
    ) {
        Box(Modifier.size(GiftThumb).clip(GiftThumbShape).background(colors.surface.copy(alpha = GIFT_THUMB_GROUND_ALPHA))) {
            ItemThumb(HomeCatalog.byId.getValue(HomeCatalog.GIFT))
        }
        WordsAndEnd(
            whole = listOf(WholeText(title, titleStyle), WholeText(text, textStyle)),
            modifier = Modifier.weight(1f),
            gap = GiftGap,
            words = {
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(title, color = colors.onSurface, style = titleStyle)
                    Text(text, color = colors.onSurfaceVariant, style = textStyle)
                }
            },
        ) { AppButton(stringResource(Res.string.home_gift_take), onClick = { onIntent(HomeIntent.GiftTaken) }, compact = true) }
    }
}

/**
 * The next home in one row (spec 3.36.7, 5.29 R7) instead of a card with a bar and a link «Все дома»: its silhouette (an outline while
 * it is not drawn), «Следующий дом · Квартира с эркером» — the name cut with an ellipsis — and under it what is said of it
 * ([NextHouseLine]): «скоро · 6 000»; «1 200 / 3 000» with the bar to its price under the line; «хватает · 3 000» in the accent, no
 * bar. The whole row opens «Дома». No next home ([next] null) — the row «Все дома». For a reader one phrase, a button: «Следующий дом,
 * Квартира с эркером, скоро, 6 000 тактов».
 */
@Composable
fun NextHouseRow(next: HomeHouse?, balance: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dot = stringResource(Res.string.dot_separator)
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    val stateStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    if (next == null) {
        val all = stringResource(Res.string.home_all_houses)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = NextMinHeight)
                .clip(NextShape)
                .background(colors.surfaceContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = all
                    role = Role.Button
                }
                .padding(NextPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NextGap),
        ) {
            Text(all, modifier = Modifier.weight(1f), color = colors.onSurface, style = titleStyle)
            AppIcon(AppIcons.ChevronRight, contentDescription = null, size = Chevron, tint = ViolinTheme.textTertiary)
        }
        return
    }
    val line = NextHouseLine.of(next, balance)
    val name = houseName(next.id)
    val nextHouse = stringResource(Res.string.home_next_house)
    val shown = houseLineWords(line, balance, dot)
    val said = listOf(nextHouse, name, houseLineSaid(line, balance, dot)).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = NextMinHeight)
            .clip(NextShape)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = said
                role = Role.Button
            }
            .padding(NextPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextGap),
    ) {
        HouseSilhouette(next.id, ViolinTheme.textTertiary, Modifier.size(NextSilhouetteWidth, NextSilhouetteHeight), outline = !next.drawn)
        Column(Modifier.weight(1f)) {
            Text(nextHouse + dot + name, color = colors.onSurface, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = shown,
                color = if (line is NextHouseLine.Enough) colors.primary else colors.onSurfaceVariant,
                style = stateStyle.copy(fontWeight = if (line is NextHouseLine.Enough) FontWeight.Bold else stateStyle.fontWeight, fontFeatureSettings = TABULAR_FIGURES),
            )
            if (line is NextHouseLine.Short) PriceBar(line.fraction, Modifier.padding(top = BarTop))
        }
        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = Chevron, tint = ViolinTheme.textTertiary)
    }
}

private const val TABULAR_FIGURES = "tnum"

/** «скоро · 6 000», «1 200 / 3 000», «хватает · 3 000» — what the row of a home not owned shows of it. */
@Composable
private fun houseLineWords(line: NextHouseLine, balance: Long, dot: String): String {
    val price = Formats.takts(line.price.toLong())
    return when (line) {
        is NextHouseLine.Soon -> stringResource(Res.string.home_house_soon) + dot + price
        is NextHouseLine.Short -> stringResource(Res.string.home_have, Formats.takts(balance), price)
        is NextHouseLine.Enough -> stringResource(Res.string.houses_enough, price)
    }
}

/** The same for a reader: «скоро, 6 000 тактов», «1 200 из 3 000 тактов», «хватает, 3 000 тактов». */
@Composable
private fun houseLineSaid(line: NextHouseLine, balance: Long, dot: String): String {
    val price = taktsInWords(line.price.toLong())
    return when (line) {
        is NextHouseLine.Soon -> stringResource(Res.string.home_house_soon) + ", " + price
        is NextHouseLine.Short -> stringResource(Res.string.journey_have_description, Formats.takts(balance), price)
        is NextHouseLine.Enough -> stringResource(Res.string.houses_enough, price).replace(dot, ", ")
    }
}

/**
 * The silhouette of a home on its grid of 200 × 120, fitted into the box of [modifier]; [outline] — a home not drawn yet, as a line of
 * [SilhouetteOutline] whatever the box: drawn in the units of the grid, the line is that width divided by the scale of the box (a line
 * of 3 units was 0.78 dp in the box of 52 × 44 of the next home).
 */
@Composable
internal fun HouseSilhouette(id: String, color: Color, modifier: Modifier = Modifier, outline: Boolean = false) {
    val paths = remember(id) { HomeSilhouettes.paths[id].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        val k = minOf(size.width / SILHOUETTE_GRID_WIDTH, size.height / SILHOUETTE_GRID_HEIGHT)
        // a box with no room draws nothing: the line in units of the grid would be infinitely wide
        if (k <= 0f) return@Canvas
        val line = Stroke(SilhouetteOutline.toPx() / k)
        translate((size.width - SILHOUETTE_GRID_WIDTH * k) / 2, (size.height - SILHOUETTE_GRID_HEIGHT * k) / 2) {
            scale(k, k, pivot = Offset.Zero) { paths.forEach { if (outline) drawPath(it, color, style = line) else drawPath(it, color) } }
        }
    }
}

/** Moving in: the home from outside in the evening comes out of the dark, the things are inside already (handoff 27e3). */
@Composable
private fun Moving(house: HomeHouse, ui: HomeUi) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val shown = remember(house) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(house) { if (!reduce) shown.animateTo(1f, tween(HomeMotion.MOVE_FADE_MS)) }
    val (things, _) = HomeRules.counts(ui.home)
    Column(
        Modifier.fillMaxSize().background(colors.surface).clickable(enabled = false) {}.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.houses_moving), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        HomePicture(
            ui.home, outside = true, mode = SceneMode.EVENING, description = houseName(house.id), house = house.id,
            modifier = Modifier.widthIn(max = HomeMaxWidth).fillMaxWidth().height(MovingPicture).graphicsLayer { alpha = shown.value }.clip(PictureShape),
            seconds = rememberSceneSeconds(),
        )
        Text(houseName(house.id), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        val moved = stringResource(Res.string.houses_moved_things, thingsInWords(things))
        Text(
            if (HomeRules.catOnPorch(ui.home, house.id) != null) stringResource(Res.string.houses_moved_cat, moved) else moved,
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
    }
}

/**
 * The row of homes (spec 3.24, 3.36.7; handoff 27e1), all seven by their price: where one lives — an outline of the accent and «здесь
 * живу» over the name, two signs and not only a colour; one bought and left — «Жить здесь», an outlined pill; one drawn and not bought —
 * what is saved of its price with a bar under the note while the takts are short, or «хватает · 3 000», and a chevron: the whole row
 * opens its sheet ([HouseSheetContent]); one not drawn yet — its silhouette as an outline and «скоро · 6 000» instead of the note, not
 * pressed: saving for what cannot be bought is not offered. A swipe of the sheet only hides it — it never moves the player.
 */
@Composable
fun HousesScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(Res.string.houses_title), onBack = { onIntent(HomeIntent.BackClicked) }) { if (!ui.loading) BalancePill(ui.balance) }
        if (ui.loading) return@Column
        val owned = HomeRules.ownedHouses(ui.home)
        Column(
            modifier = Modifier
                .widthIn(max = HomeMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenSide, top = ScrollTop, end = ScreenSide, bottom = ScrollEnd),
            verticalArrangement = Arrangement.spacedBy(HousesGap),
        ) {
            HomeCatalog.houses.forEach { house -> HouseRow(house, ui, house.id in owned, onIntent) }
        }
    }
    AppSheet(
        value = ui.houseCard,
        // a swipe, a tap outside, «назад»: the sheet hides, and nothing more — no move
        onHide = { onIntent(HomeIntent.HouseCardClosed) },
        // the picture, the words and the button make a tall sheet: it stops short of the status bar and scrolls
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        bottom = { house -> { HouseSheetButtons(house, ui, onIntent) } },
    ) { house -> HouseSheetContent(house, ui) }
    ui.moving?.let { Moving(it, ui) }
}

/**
 * A row of «Дома»: the silhouette, the words — «здесь живу» over the name where one lives, the name up to two lines, the note or «скоро
 * · 6 000», the bar while the takts are short — and at the end «1 200 / 3 000 ›», «хватает · 3 000 ›» or «Жить здесь». No word of the
 * name or the note breaks and the name is never cut to make room for the end ([WordsAndEnd]): beside it on 360 «Маленький деревянный
 * домик» had 64 to 79 dp — a word broke by the letter — and on 412 beside «хватает · 3 000» 116, «домик» cut; the end then stands under
 * the words, at the end of the row.
 */
@Composable
private fun HouseRow(house: HomeHouse, ui: HomeUi, mine: Boolean, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val here = house.id == ui.house
    val line = if (mine) null else NextHouseLine.of(house, ui.balance)
    val buyable = house.drawn && !mine
    val name = houseName(house.id)
    val note = houseNote(house.id)
    val dot = stringResource(Res.string.dot_separator)
    val liveHere = stringResource(Res.string.houses_live_here)
    val said = when {
        here -> listOf(name, liveHere, note)
        line == null -> listOf(name, note)
        line is NextHouseLine.Soon -> listOf(name, houseLineSaid(line, ui.balance, dot))
        else -> listOf(name, note, houseLineSaid(line, ui.balance, dot))
    }.filter { it.isNotEmpty() }.joinToString(", ")
    val smallStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val nameStyle = MaterialTheme.typography.titleMedium.copy(fontSize = HOUSE_NAME_SP.sp, lineHeight = 1.35.em, fontWeight = FontWeight.ExtraBold)
    // a home not drawn yet says «скоро · 6 000» instead of its note
    val under = if (line is NextHouseLine.Soon) houseLineWords(line, ui.balance, dot) else note
    val underStyle = smallStyle.copy(fontFeatureSettings = TABULAR_FIGURES)
    val words: @Composable () -> Unit = {
        Column(Modifier.clearAndSetSemantics {}) {
            if (here) HereChip(liveHere)
            // up to two lines, a word never broken: smaller first (5.29 R7)
            Text(
                name,
                color = if (mine || house.drawn) colors.onSurface else colors.onSurfaceVariant,
                autoSize = remember { WholeWordsFit(HOUSE_NAME_SP, HOUSE_NAME_LEAST_SP) },
                style = nameStyle,
                maxLines = HOUSE_NAME_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = under, color = colors.onSurfaceVariant, style = underStyle)
            if (line is NextHouseLine.Short) PriceBar(line.fraction, Modifier.padding(top = BarTop))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HouseShape)
            .background(colors.surfaceContainer)
            .then(if (here) Modifier.border(HereBorder, colors.primary, HouseShape) else Modifier)
            .then(if (buyable) Modifier.clickable(role = Role.Button) { onIntent(HomeIntent.HouseClicked(house.id)) } else Modifier)
            // one phrase for a reader; «Жить здесь» stays a button of its own
            .semantics(mergeDescendants = true) { contentDescription = said }
            .padding(HousePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HouseGap),
    ) {
        HouseSilhouette(house.id, if (mine) colors.primary else colors.outline, Modifier.size(HouseSilhouetteWidth, HouseSilhouetteHeight), outline = !mine && !house.drawn)
        if (here || line is NextHouseLine.Soon) {
            Box(Modifier.weight(1f)) { words() }
        } else {
            WordsAndEnd(
                whole = listOf(WholeText(name, nameStyle, HOUSE_NAME_LINES, HOUSE_NAME_LEAST_SP), WholeText(under, underStyle)),
                modifier = Modifier.weight(1f),
                gap = HouseGap,
                words = words,
            ) {
                if (line == null) {
                    PillButton(onClick = { onIntent(HomeIntent.LiveHere(house.id)) }) {
                        Text(
                            stringResource(Res.string.houses_live),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                } else {
                    Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            houseLineWords(line, ui.balance, dot),
                            color = if (line is NextHouseLine.Enough) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                            style = smallStyle.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                        )
                        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = Chevron, tint = ViolinTheme.textTertiary)
                    }
                }
            }
        }
    }
}

/** «здесь живу»: a chip of 22 on the soft accent over the name of the home lived in. */
@Composable
private fun HereChip(text: String) {
    Box(
        Modifier
            .padding(bottom = HereChipUnder)
            .heightIn(min = HereChipHeight)
            .clip(CircleShape)
            .background(ViolinTheme.accentSoft)
            .padding(horizontal = HereChipSide),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/**
 * The sheet of a home to buy (spec 3.36.7), in the frame of the sheets of R1: the home from outside by day, «дом 1 из 6», its name — a
 * heading —, its note, «Что нового» and what moving does. Its buttons are pinned at its bottom ([HouseSheetButtons]).
 */
@Composable
fun HouseSheetContent(house: HomeHouse, ui: HomeUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SheetGap)) {
        HomePicture(
            ui.home, outside = true, mode = SceneMode.DAY, description = houseName(house.id), house = house.id,
            modifier = Modifier.fillMaxWidth().height(SheetPicture).clip(PictureShape), seconds = rememberSceneSeconds(),
        )
        Text(
            stringResource(Res.string.houses_of, HomeCatalog.houses.indexOf(house), HomeCatalog.houses.size - 1),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        )
        Text(
            houseName(house.id),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(houseNote(house.id), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
        val news = HomeTexts.houseNews[house.id]?.let { stringArrayResource(it) }.orEmpty()
        if (news.isNotEmpty()) {
            Text(stringResource(Res.string.houses_news), color = colors.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
            news.forEach { Text("· $it", color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) }
        }
        Text(stringResource(Res.string.houses_moving_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
    }
}

/**
 * The bottom of the sheet of a home: «Переехать · 3 000» on the whole width, or the plate «не хватает 1 800» and under it, a line of its
 * own, «примерно 6 занятий» (5.18: [JourneyRules.sessionsLeft] by the numbers of the journey). 48 in a window no higher than 360.
 */
@Composable
fun HouseSheetButtons(house: HomeHouse, ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val missing = house.price - ui.balance
    Column(Modifier.fillMaxWidth().padding(top = SheetButtonsTop)) {
        BuyButton(
            price = house.price,
            balance = ui.balance,
            label = stringResource(Res.string.houses_move, Formats.takts(house.price.toLong())),
            onBuy = { onIntent(HomeIntent.MoveClicked) },
            modifier = Modifier.fillMaxWidth(),
            compact = currentDockMetrics().compact,
        )
        if (missing > 0) {
            Text(
                sessionsInWords(JourneyRules.sessionsLeft(missing, ui.config)),
                modifier = Modifier.fillMaxWidth().padding(top = SessionsTop),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

/**
 * Enough — the main button, its words on one line ([AppButton]'s `oneLine`); short — the plate of what is missing ([ShortfallPlate],
 * spec 3.36.7, 5.29 R7): it tells, it does not forbid — no role and no touch, never dimmed — and it is as high as the button it stands
 * for. [compact] — the 48 of a window no higher than 360. The hint of how many practices is a line of the caller's under it.
 */
@Composable
fun BuyButton(price: Int, balance: Long, label: String, onBuy: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (balance >= price) {
        AppButton(label, onBuy, modifier, compact = compact, oneLine = true)
    } else {
        val missing = price - balance
        val number = Formats.takts(missing)
        ShortfallPlate(
            text = stringResource(Res.string.shop_missing, number),
            modifier = modifier,
            description = stringResource(Res.string.shop_missing, taktsInWords(missing)),
            compact = compact,
            keep = number,
            leading = { TaktIcon(size = PlateSign) },
        )
    }
}
