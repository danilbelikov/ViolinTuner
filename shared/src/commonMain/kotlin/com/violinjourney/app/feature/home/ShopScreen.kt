package com.violinjourney.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeGroup
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeSlot
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.art.HomePicture
import com.violinjourney.app.feature.home.art.ItemThumb
import com.violinjourney.app.feature.home.art.rememberHomeTime
import com.violinjourney.app.feature.home.art.rememberHouseArt
import com.violinjourney.app.feature.journey.BalancePill
import com.violinjourney.app.feature.journey.JourneyTopBar
import com.violinjourney.app.feature.journey.TaktAmount
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.rememberSceneCamera
import com.violinjourney.app.feature.journey.art.rememberSceneSeconds
import com.violinjourney.app.feature.journey.art.sceneCamera
import com.violinjourney.app.feature.journey.cityOf
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.arrange_empty
import com.violinjourney.app.shared.resources.arrange_in_shop
import com.violinjourney.app.shared.resources.arrange_not_here
import com.violinjourney.app.shared.resources.arrange_places_few
import com.violinjourney.app.shared.resources.arrange_places_many
import com.violinjourney.app.shared.resources.arrange_places_one
import com.violinjourney.app.shared.resources.arrange_title
import com.violinjourney.app.shared.resources.arrange_waiting
import com.violinjourney.app.shared.resources.home_day
import com.violinjourney.app.shared.resources.home_evening
import com.violinjourney.app.shared.resources.home_outside
import com.violinjourney.app.shared.resources.home_room
import com.violinjourney.app.shared.resources.home_shop
import com.violinjourney.app.shared.resources.shop_all
import com.violinjourney.app.shared.resources.shop_gift
import com.violinjourney.app.shared.resources.shop_group_furniture
import com.violinjourney.app.shared.resources.shop_group_instrument
import com.violinjourney.app.shared.resources.shop_group_life
import com.violinjourney.app.shared.resources.shop_group_light
import com.violinjourney.app.shared.resources.shop_group_music
import com.violinjourney.app.shared.resources.shop_group_outside
import com.violinjourney.app.shared.resources.shop_group_pet
import com.violinjourney.app.shared.resources.shop_group_plant
import com.violinjourney.app.shared.resources.shop_group_room
import com.violinjourney.app.shared.resources.shop_owned
import com.violinjourney.app.shared.resources.shop_place
import com.violinjourney.app.shared.resources.shop_place_clear
import com.violinjourney.app.shared.resources.shop_price_takts
import com.violinjourney.app.shared.resources.shop_shelf_furniture
import com.violinjourney.app.shared.resources.shop_shelf_instrument
import com.violinjourney.app.shared.resources.shop_shelf_life
import com.violinjourney.app.shared.resources.shop_shelf_light
import com.violinjourney.app.shared.resources.shop_shelf_music
import com.violinjourney.app.shared.resources.shop_shelf_outside
import com.violinjourney.app.shared.resources.shop_shelf_pet
import com.violinjourney.app.shared.resources.shop_shelf_plant
import com.violinjourney.app.shared.resources.shop_shelf_room
import com.violinjourney.app.shared.resources.shop_shelf_yours
import com.violinjourney.app.shared.resources.shop_soon
import com.violinjourney.app.shared.resources.shop_standing
import com.violinjourney.app.shared.resources.shop_standing_outside
import com.violinjourney.app.shared.resources.shop_takts_note
import com.violinjourney.app.shared.resources.shop_try_remove
import com.violinjourney.app.shared.resources.shop_try_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val TileShape = RoundedCornerShape(14.dp)

// a shelf of the shop (spec 3.29, 5.22): tiles of 100 dp, a picture of 100 × 76 on a board of 6 dp
private val TileWidth = 100.dp
private const val PICTURE_RATIO = 0.76f
private val TileGap = 8.dp
private val RowGap = 6.dp
private const val MIN_COLUMNS = 3
internal val BoardHeight = 6.dp
private val BoardShadow = 5.dp
private val BoardCorner = 2.dp
private val StripeWidth = 22.dp

// The shop of the redesign (spec 3.36.7, 5.29 R7, «Лавка»): the chips of the rows a ribbon 16 from the sides, 10 over the shelves.
private val ScreenSide = 16.dp
private val ChipsUnder = 10.dp
private val ChipsGap = 8.dp
private val ShelvesGap = 16.dp
private val ShelvesEnd = 24.dp
private val ShopMaxWidth = 720.dp

// The city on a picture of a thing, on the dense glass (spec 5.29 R7): 9.5 sp / 700, 4 from the corner of the picture.
private val CityInset = 4.dp
private val CitySide = 6.dp
private val CityTop = 1.dp
private val CitySize = 9.5.sp
private val CityLine = 14.sp

/**
 * What a shelf is made of (spec 3.29; handoff 27b and 28e): dark wood for the maker's and for music,
 * striped cloth for the market, cloth the colour of the sea for the pets — each with a board of its
 * own that the things stand on. The card of a thing stands the thing on the same.
 */
internal enum class ShelfMaterial(val top: Color, val bottom: Color, val stripe: Color?, val board: Color, val boardLit: Color) {
    WOOD(Color(0xFF4A3428), Color(0xFF2F2119), null, Color(0xFF7E5230), Color(0xFFB07A48)),
    CLOTH(Color(0xFF3B3450), Color(0xFF3B3450), Color(0xFF342E48), Color(0xFF5B43B8), Color(0xFF7A62D8)),
    SEA(Color(0xFF2E3A40), Color(0xFF2E3A40), Color(0xFF29343A), Color(0xFF4E7A74), Color(0xFF6FA8A0)),
    ;

    fun paint(scope: DrawScope) = with(scope) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val band = StripeWidth.toPx()
        stripe?.let { tone ->
            var x = band
            while (x < size.width) {
                drawRect(tone, topLeft = Offset(x, 0f), size = Size(band, size.height))
                x += 2 * band
            }
        }
    }

    companion object {
        fun of(group: HomeGroup): ShelfMaterial = when (group) {
            HomeGroup.INSTRUMENT, HomeGroup.MUSIC -> WOOD
            HomeGroup.PET -> SEA
            else -> CLOTH
        }
    }
}

/** The board of a shelf with its lit edge and the shadow it throws on the cloth below. */
@Composable
internal fun Board(material: ShelfMaterial, modifier: Modifier) {
    Canvas(modifier) {
        val shadow = BoardShadow.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), startY = size.height, endY = size.height + shadow), topLeft = Offset(0f, size.height), size = Size(size.width, shadow))
        drawRoundRect(Brush.verticalGradient(listOf(material.boardLit, material.board)), cornerRadius = CornerRadius(BoardCorner.toPx()))
    }
}

/**
 * The things of a shelf in rows (spec 3.29, 5.22): as many tiles of 100 dp as there is room for,
 * never fewer than three, with even gaps; a row not full starts at the left. Under every row — its
 * board, the width of the shelf.
 */
@Composable
private fun ShelfRows(things: List<HomeItem>, material: ShelfMaterial, tile: @Composable (HomeItem, Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = ((maxWidth + TileGap) / (TileWidth + TileGap)).toInt()
        val columns = maxOf(MIN_COLUMNS, fits)
        val width = if (fits >= MIN_COLUMNS) TileWidth else (maxWidth - TileGap * (MIN_COLUMNS - 1)) / MIN_COLUMNS
        val gap = (maxWidth - width * columns) / (columns - 1)
        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            things.chunked(columns).forEach { row ->
                Box(Modifier.fillMaxWidth()) {
                    Board(material, Modifier.padding(top = width * PICTURE_RATIO).fillMaxWidth().height(BoardHeight))
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { tile(it, width) } }
                }
            }
        }
    }
}

private fun groupLabel(group: HomeGroup): StringResource = when (group) {
    HomeGroup.INSTRUMENT -> Res.string.shop_group_instrument
    HomeGroup.MUSIC -> Res.string.shop_group_music
    HomeGroup.ROOM -> Res.string.shop_group_room
    HomeGroup.LIGHT -> Res.string.shop_group_light
    HomeGroup.FURNITURE -> Res.string.shop_group_furniture
    HomeGroup.PLANT -> Res.string.shop_group_plant
    HomeGroup.LIFE -> Res.string.shop_group_life
    HomeGroup.PET -> Res.string.shop_group_pet
    HomeGroup.OUTSIDE -> Res.string.shop_group_outside
}

private fun shelfLabel(group: HomeGroup): StringResource = when (group) {
    HomeGroup.INSTRUMENT -> Res.string.shop_shelf_instrument
    HomeGroup.MUSIC -> Res.string.shop_shelf_music
    HomeGroup.ROOM -> Res.string.shop_shelf_room
    HomeGroup.LIGHT -> Res.string.shop_shelf_light
    HomeGroup.FURNITURE -> Res.string.shop_shelf_furniture
    HomeGroup.PLANT -> Res.string.shop_shelf_plant
    HomeGroup.LIFE -> Res.string.shop_shelf_life
    HomeGroup.PET -> Res.string.shop_shelf_pet
    HomeGroup.OUTSIDE -> Res.string.shop_shelf_outside
}

/**
 * The shop (spec 3.24, 3.25, 3.29, 3.36.7): the purse in the bar; the chips of the rows a ribbon sideways — the chip of the place first
 * when the shop is opened by place, chosen, with a cross that takes it off; the shelves with their boards; a tap on a thing — its card,
 * a sheet of R1 ([ItemCardContent], [ItemCardButtons]) that a swipe only hides. The try-on takes the whole screen ([TryOn]).
 */
@Composable
fun ShopScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    ui.tryOn?.let { TryOn(it, ui, onIntent, modifier); return }
    val today = rememberHomeTime().date
    val tags = remember(ui.home, ui.progress, ui.house, today) { ShelfTags(ui.home, ui.progress, ui.house, today) }
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(Res.string.home_shop), onBack = { onIntent(HomeIntent.BackClicked) }) { if (!ui.loading) BalancePill(ui.balance) }
        if (ui.loading) return@Column
        ShopChips(ui, onIntent)
        val shelves = remember(ui.category, ui.slot, tags) { ShopShelves.of(ui.category, ui.slot, tags::owned) }
        // a shelf is an item of a lazy list: only the shelves on the screen are composed, not the hundred tiles of the catalogue
        LazyColumn(
            Modifier.widthIn(max = ShopMaxWidth).fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenSide, end = ScreenSide, bottom = ShelvesEnd),
            verticalArrangement = Arrangement.spacedBy(ShelvesGap),
        ) {
            // where takts come from, said once — until the first purchase (handoff `dev`: «в лавке, первый вход»)
            if (ui.home.purchased.isEmpty()) {
                item(key = "note") {
                    Text(
                        stringResource(Res.string.shop_takts_note),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    )
                }
            }
            items(shelves, key = { it.group.name }) { shelf ->
                val material = ShelfMaterial.of(shelf.group)
                Column(
                    Modifier.fillMaxWidth().clip(HomeCard).drawBehind { material.paint(this) }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(shelfLabel(shelf.group)), color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleSmall)
                    ShelfRows(shelf.things, material) { item, width -> Tile(item, tags.of(item), onIntent, width) }
                    if (shelf.yours) {
                        Text(stringResource(Res.string.shop_shelf_yours), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    // The shop the card was last open in: while the card is open it follows the shop; once the card is dropped — bought, taken, put —
    // the sheet slides away with the thing it held (AppSheet) and with this, as it was: the purchase that comes from the store meanwhile
    // does not turn it into the card of a thing standing in the room, nor change its height on the way down. Written before the sheet
    // reads it.
    val cardShop = remember { mutableStateOf(ui) }
    if (ui.card != null) cardShop.value = ui
    AppSheet(
        value = ui.card,
        // a swipe, a tap outside, «назад»: the sheet hides, and nothing more — no purchase, no try-on
        onHide = { onIntent(HomeIntent.CardClosed) },
        // the picture, the words and the rows make a tall sheet: it stops short of the status bar and scrolls
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        bottom = { item ->
            val shop = cardShop.value
            if (ItemCardPlan.of(item, shop, today).hasButtons()) ({ ItemCardButtons(item, shop, onIntent, today) }) else null
        },
    ) { item -> ItemCardContent(item, cardShop.value, today = today) }
}

/**
 * The chips of the rows (spec 3.36.7, 5.29 R7): «Всё» and the nine rows, filters of R1 in one ribbon that scrolls sideways — in wrap
 * they would take three lines over the shelves. The shop by place puts the chip of its place first: chosen, with a cross of 18 inside it;
 * a press anywhere on it takes the place off, and TalkBack says «Место: На столе, слева», «выбрано», a button whose press is «Снять
 * фильтр» (a radio button chosen already would lose its press and the label with it — [AppChip]). The place is the one filter: while
 * it stands, no row is chosen.
 */
@Composable
private fun ShopChips(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = ChipsUnder)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenSide)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChipsGap),
    ) {
        ui.slot?.let { slot ->
            val place = slotName(slot)
            val said = stringResource(Res.string.shop_place, place)
            AppChip(
                text = place,
                selected = true,
                onClick = { onIntent(HomeIntent.SlotFilterCleared) },
                modifier = Modifier.semantics { contentDescription = said },
                trailing = AppIcons.Close,
                onClickLabel = stringResource(Res.string.shop_place_clear),
            )
        }
        AppChip(stringResource(Res.string.shop_all), selected = ui.slot == null && ui.category == null, onClick = { onIntent(HomeIntent.CategorySelected(null)) })
        HomeGroup.entries.forEach { group ->
            AppChip(stringResource(groupLabel(group)), selected = ui.slot == null && ui.category == group, onClick = { onIntent(HomeIntent.CategorySelected(group)) })
        }
    }
}

/**
 * A thing on a shelf (handoff 28e, spec 3.29): a tile of 100 dp that holds the worst of the texts, the
 * thing standing on the board of its row. Where the thing comes from is a pill on the picture, on the dense
 * glass (spec 3.36.7), without a preposition, read before the name like a label on the thing; under the
 * board — the name in up to two lines and one line: a price or a state. A thing of a city not reached yet
 * stands faint, «привезут».
 */
@Composable
private fun Tile(item: HomeItem, tag: ShelfTag, onIntent: (HomeIntent) -> Unit, width: Dp) {
    val name = itemName(item.id)
    val city = item.from?.let { cityOf(JourneyRoute.indexOf(it)) }
    val under = when (tag) {
        ShelfTag.PRICE -> Formats.takts(item.price.toLong())
        ShelfTag.GIFT -> stringResource(Res.string.shop_gift)
        ShelfTag.STANDING -> stringResource(if (item.outside) Res.string.shop_standing_outside else Res.string.shop_standing)
        ShelfTag.OWNED -> stringResource(Res.string.shop_owned)
        ShelfTag.LOCKED -> stringResource(Res.string.shop_soon)
    }
    val description = listOfNotNull(name, city, if (tag == ShelfTag.PRICE) stringResource(Res.string.shop_price_takts, under) else under).joinToString(", ")
    Column(
        Modifier.width(width).clip(TileShape)
            // a thing of the next city calls to the road: it has no card yet
            .then(if (tag == ShelfTag.LOCKED) Modifier else Modifier.clickable(role = Role.Button) { onIntent(HomeIntent.ItemClicked(item.id)) })
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width, width * PICTURE_RATIO)) {
            Box(Modifier.fillMaxSize().alpha(if (tag == ShelfTag.LOCKED) LOCKED_ALPHA else 1f)) { ItemThumb(item) }
            if (city != null) CityPill(city, Modifier.align(Alignment.TopStart))
        }
        Text(
            name, color = Color.White.copy(alpha = if (tag == ShelfTag.LOCKED) 0.55f else 0.92f), fontSize = 11.sp, lineHeight = 14.sp,
            textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = BoardHeight + 6.dp),
        )
        if (tag == ShelfTag.PRICE) {
            TaktAmount(under, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, icon = 12.dp)
        } else {
            Text(under, color = Color.White.copy(alpha = if (tag == ShelfTag.STANDING) 0.92f else 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Where a thing is brought from, on its picture (spec 3.36.7, 5.29 R7): a pill of the dense glass (0.82 — it lies on a busy drawing),
 * 9.5 sp / 700 in the colour of the words, 4 from the corner; a name too long for the picture ends with an ellipsis — a reader hears
 * the city whole, in the description of the thing.
 */
@Composable
private fun CityPill(city: String, modifier: Modifier = Modifier) {
    Text(
        city,
        modifier = modifier
            .padding(CityInset)
            .clip(CircleShape)
            .background(ViolinTheme.glassStrong)
            .padding(horizontal = CitySide, vertical = CityTop),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = CitySize, lineHeight = CityLine, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    )
}

private const val LOCKED_ALPHA = 0.4f

/** Trying on starts a step back from «covering»: the floor of the room, where the violin and the rug are, must not hide under the buttons. */
private const val TRY_ON_ZOOM = 0.62f

// The try-on (spec 3.36.7, 5.29 R7): a bar on the dark of the screen at 0.8 fading to nothing, «назад» 48, the title 16 / 800; at the
// bottom the veil it always had, «Убрать» by its word with a frame of white at 35 %.
private const val TRY_BAR_ALPHA = 0.8f
private val TryBarPadding = PaddingValues(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 24.dp)
private val TryBack = 48.dp
private val TryTitle = 16.sp
private const val TRY_VEIL_ALPHA = 0.75f
private val TryVeilPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 20.dp)
private const val TRY_OUTLINE_ALPHA = 0.35f

/**
 * Trying on (spec 3.25, 3.29, 3.36.7): one's own room on the whole screen, the thing in its place in its depth, a warm halo breathing
 * under it, the room 12 % darker. At the top a bar on the dark: «назад», «Примерка · Настольная лампа» and «вечер | день» on the glass —
 * «вечер · день» matters here most: a chandelier and a fire are bought for the evening. «Назад» — the arrow and the system one alike —
 * is «Убрать»: the thing leaves the room and its card is open again (a reader hears the arrow as «Убрать»). At the bottom «Убрать» by its
 * word and «Купить · 300» on the rest of the row, or the plate of what is missing: how many practices it is, the card says.
 */
@Composable
private fun TryOn(item: HomeItem, ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val camera = rememberSceneCamera()
    val byClock = rememberHomeTime().mode
    val mode = ui.tryMode ?: byClock
    val box = rememberHouseArt(ui.house, mode)?.items?.get(item.id)
    var size by remember { mutableStateOf(IntSize.Zero) }
    // upright the room is wider than the screen: the eye starts on the thing, not on the middle of the room — once for a
    // thing and a size: «вечер · день» reads the other file, and the box that goes while it is read must not undo a pan
    var looked by remember(item.id, size) { mutableStateOf(false) }
    LaunchedEffect(item.id, box != null, size) {
        if (!looked && box != null && size != IntSize.Zero) {
            camera.lookAt((box.left + box.right) / 2, size.width.toFloat(), size.height.toFloat(), atZoom = TRY_ON_ZOOM)
            looked = true
        }
    }
    val name = itemName(item.id)
    val remove = stringResource(Res.string.shop_try_remove)
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }) {
        HomePicture(
            ui.home, outside = item.outside, mode = mode, description = name, ghost = item,
            modifier = Modifier.fillMaxSize().sceneCamera(camera), seconds = rememberSceneSeconds(), camera = camera::read,
        )
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.surface.copy(alpha = TRY_BAR_ALPHA), Color.Transparent)))
                .padding(TryBarPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(TryBack)
                    .clip(CircleShape)
                    .clickable(onClickLabel = remove, role = Role.Button) { onIntent(HomeIntent.TryClosed) }
                    .semantics { contentDescription = remove },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface) }
            Text(
                stringResource(Res.string.shop_try_title, name),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).semantics { heading() },
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = TryTitle, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            TwoWay(stringResource(Res.string.home_evening), stringResource(Res.string.home_day), mode == SceneMode.DAY, { onIntent(HomeIntent.TryModeSelected(if (it) SceneMode.DAY else SceneMode.EVENING)) })
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = TRY_VEIL_ALPHA))))
                .padding(TryVeilPadding),
        ) {
            OutlineAndBuy(
                outline = remove,
                onOutline = { onIntent(HomeIntent.TryClosed) },
                price = item.price,
                balance = ui.balance,
                label = buyLabel(item),
                onBuy = { onIntent(HomeIntent.BuyClicked) },
                compact = currentDockMetrics().compact,
                byWord = true,
                outlineColor = Color.White.copy(alpha = TRY_OUTLINE_ALPHA),
            )
        }
    }
}

// «Обставить» (spec 3.36.7, 5.29 R7): the room 210 at a corner of 20 over the places ([ArrangeFit]); the places 12 / 0 with a line under
// each; the tiles 72 at a corner of 14, 8 apart.
private val ArrangeShape = RoundedCornerShape(20.dp)
private val PlacesTop = 12.dp
private val PlacesEnd = 24.dp
private val PlacesCaptionUnder = 8.dp
private val PlacesCaptionLine = 18.sp
private val PlacePadding = 12.dp
private val PlaceLine = 1.dp

// The line of a place: its name 15 / 700 and «в лавке 4 →» 13 / 700 in the accent, the arrow 16; the link is pressed over 48.
private val PlaceNameLine = 20.sp
private val LinkArrow = 16.dp
private val LinkGap = 4.dp
private val LinkSide = 8.dp

/**
 * Between the line of the name and the tiles: room for the tick of a chosen tile, which stands 6 out of it, and for the strip of the
 * link's 48 under its line — the tiles start where it ends, so a press there is the link's, not a tile's.
 */
private val TilesTop = 14.dp

// A tile of a place: 72 × 72; chosen — a frame of 2 in the accent, a ring of 3 outside it in the accent at 25 %, and a circle of 22 with
// a tick of 14 at its top end corner, standing 6 out of the tile. The row of tiles keeps room over and beside it for the ring and the tick.
private val TileSize = 72.dp
private val ChosenFrame = 2.dp
private val ChosenRing = 3.dp
private const val CHOSEN_RING_ALPHA = 0.25f
private val TickCircle = 22.dp
private val Tick = 14.dp
private val TickOut = 6.dp
private val TilesGap = 8.dp
private val TileCorner = 14.dp

/**
 * The pill of the city on a chosen tile ends this far from the start of the tile — 2 before the circle of the tick, which stands 6 out
 * of its top end corner (the inset of 4 at its end is in it): «Кремона» ends with an ellipsis there, under the tick it lost its end.
 */
private val TickClear = 2.dp
private val PillBesideTick = TileSize + TickOut - TickCircle - TickClear + CityInset

/**
 * «Обставить» (spec 3.24, 3.36.7): the room alive and answering, and the places of this home, each with what was bought for it in a row
 * of tiles — «пусто» first —; a tap replaces at once and outlines the thing that now stands there on the room ([HomeUi.arrangeFocus]).
 * «в лавке 4 →» of a place opens the shop by that place. No dragging: a thing knows its place, the room is always put together.
 *
 * Upright the room stands over the places, fixed, and the places scroll under it; the room gives way where the places would not keep
 * the caption and one whole place. Lying — a window wider than high where the room keeps 200 beside a column of 320 — the room stands on
 * the left, the whole height of its column, and the places scroll on the right: under a room of 210 the places of the emulator's
 * 640 × 360 lying (603 × 308 under its bars) had nothing left — not one of them could be reached (5.29 R7). [ArrangeFit] says where
 * each stands; both stand in one place of the tree whichever way the phone is turned, so a turn keeps where the places and the rows of
 * tiles were scrolled to. The room is seen whole by its width: beside the places it is narrower than covering would leave its sides.
 */
@Composable
fun ArrangeScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(Res.string.arrange_title), onBack = { onIntent(HomeIntent.BackClicked) }) {
            // no picture under it in the bar: the solid plate it always had (spec 3.36.7, «Обставить»)
            TwoWay(stringResource(Res.string.home_room), stringResource(Res.string.home_outside), ui.outside, { onIntent(HomeIntent.SideSelected(it)) }, onPicture = false)
        }
        if (ui.loading) return@Column
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val lying = HomeRoomFit.lying(window.width > window.height, maxWidth)
            // the caption and one whole place: the places keep this under the room upright
            val placesLeast = with(density) {
                PlacesTop + PlacesCaptionLine.toDp() + PlacesCaptionUnder + PlacePadding * 2 + PlaceNameLine.toDp() + TilesTop + TileSize + ChosenRing
            }
            val frames = ArrangeFit.frames(maxWidth, maxHeight, lying, HomeMaxWidth, placesLeast)
            ArrangeRoom(ui, Modifier.framed(frames.room))
            Places(
                ui, onIntent, Modifier.framed(frames.places),
                if (lying) PaddingValues(top = ArrangeFit.LyingTop, bottom = PlacesEnd) else PaddingValues(start = ScreenSide, end = ScreenSide, top = PlacesTop, bottom = PlacesEnd),
            )
        }
    }
}

/** At [frame] of its box: where [ArrangeFit] stands it. */
private fun Modifier.framed(frame: DpRect): Modifier = offset(frame.left, frame.top).size(frame.right - frame.left, frame.bottom - frame.top)

/** The room of «Обставить», alive and seen whole by its width, with the outline of the tile touched last. */
@Composable
private fun ArrangeRoom(ui: HomeUi, modifier: Modifier) {
    HomePicture(
        ui.home, ui.outside, description = houseName(ui.house),
        modifier = modifier.clip(ArrangeShape), seconds = rememberSceneSeconds(), outline = ui.arrangeFocus, whole = true,
    )
}

/**
 * The places of «Обставить» in a column that scrolls: «Маленький деревянный домик · 23 места, 18 занято», each place of this home with
 * a thing to choose ([Place]), and at the end, quietly, the places this home lacks and what waits for them in the wardrobe.
 */
@Composable
private fun Places(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier, padding: PaddingValues) {
    val colors = MaterialTheme.colorScheme
    val placed = HomeRules.placed(ui.home)
    val owned = HomeRules.ownedItems(ui.home)
    val slots = HomeCatalog.slots.filter { it.outside == ui.outside }
    val here = HomeRules.places(ui.house, ui.outside)
    val filled = here.filter { HomeRules.wardrobe(it.id, ui.home).isNotEmpty() }
    val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = PlacesCaptionLine)
    Column(modifier.verticalScroll(rememberScrollState()).padding(padding)) {
        Text(
            stringResource(placesWords(here.size), houseName(ui.house), here.size, here.count { placed[it.id] != null }),
            modifier = Modifier.padding(bottom = PlacesCaptionUnder),
            color = colors.onSurfaceVariant,
            style = small,
        )
        filled.forEach { slot ->
            val more = HomeCatalog.items.count { it.slot == slot.id && it.id !in owned }
            Place(slot, ui, placed[slot.id]?.id, more, onIntent)
        }
        val missing = slots.filter { !HomeRules.slotIn(it.id, ui.house) && HomeRules.wardrobe(it.id, ui.home).isNotEmpty() }
        if (missing.isNotEmpty()) {
            Text(
                stringResource(Res.string.arrange_not_here),
                modifier = Modifier.padding(top = PlacePadding),
                color = colors.onSurfaceVariant,
                style = small.copy(fontWeight = FontWeight.Bold),
            )
            missing.forEach { slot ->
                val waiting = HomeRules.wardrobe(slot.id, ui.home).map { itemName(it.id) }.joinToString(", ")
                Text(
                    stringResource(Res.string.arrange_waiting, slotName(slot.id), waiting),
                    modifier = Modifier.padding(top = 4.dp),
                    color = colors.onSurfaceVariant,
                    style = small,
                )
            }
        }
    }
}

/**
 * A place of «Обставить» (spec 3.36.7): its name — cut with an ellipsis, never pushing the link out — and «в лавке 4 →» where the shop
 * has more for it; under them its tiles, «пусто» first where the place may be bare, the chosen one framed and ticked. A line under it.
 */
@Composable
private fun Place(slot: HomeSlot, ui: HomeUi, standing: String?, more: Int, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val line = colors.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(line, Offset(0f, size.height - PlaceLine.toPx() / 2), Offset(size.width, size.height - PlaceLine.toPx() / 2), PlaceLine.toPx()) }
            .padding(vertical = PlacePadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slotName(slot.id),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = PlaceNameLine, fontWeight = FontWeight.Bold),
            )
            if (more > 0) ShopLink(more) { onIntent(HomeIntent.ShopAtClicked(slot.id)) }
        }
        Row(
            Modifier
                .padding(top = TilesTop, bottom = ChosenRing)
                .fillMaxWidth()
                .bleed(TickOut)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = TickOut)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TilesGap),
        ) {
            // walls, a floor, the window and what things stand on cannot be bare; a desk without a lamp is a choice too
            if (!slot.palette && slot.id !in HomeCatalog.NEVER_BARE) {
                Choice(chosen = standing == null, description = stringResource(Res.string.arrange_empty), onClick = { onIntent(HomeIntent.Placed(slot.id, "")) }, empty = true) {
                    Text(
                        stringResource(Res.string.arrange_empty),
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 4.dp),
                        color = ViolinTheme.textTertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            HomeRules.wardrobe(slot.id, ui.home).forEach { item ->
                val city = item.from?.let { cityOf(JourneyRoute.indexOf(it)) }
                val chosen = standing == item.id
                Choice(
                    chosen = chosen,
                    description = listOfNotNull(itemName(item.id), city).joinToString(", "),
                    onClick = { onIntent(HomeIntent.Placed(slot.id, item.id)) },
                ) {
                    ItemThumb(item)
                    // the tick of a chosen tile stands in the other top corner: the pill ends before it, with an ellipsis
                    if (city != null) CityPill(city, Modifier.align(Alignment.TopStart).then(if (chosen) Modifier.widthIn(max = PillBesideTick) else Modifier))
                }
            }
        }
    }
}

/**
 * «в лавке 4 →» (spec 3.36.7): a button to the shop by this place, 13 sp / 700 in the accent with an arrow of 16. Laid out as high as the
 * line of the name, pressed over 48 — its own height, not a widening of the system: the strips over and under that line stand in the
 * fields of the place, and the tiles start where the lower one ends ([TilesTop]).
 */
@Composable
private fun ShopLink(more: Int, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                val line = minOf(placeable.height, PlaceNameLine.roundToPx())
                layout(placeable.width, line) { placeable.place(0, (line - placeable.height) / 2) }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(start = LinkSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.arrange_in_shop, more),
            color = accent,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(LinkGap))
        AppIcon(AppIcons.ArrowRight, contentDescription = null, size = LinkArrow, tint = accent)
    }
}

/**
 * A row as wide as its column and [by] more at each side — the row of tiles scrolls to the very edge of its column while its ring and
 * its tick, which stand out of a tile, are not cut by the edge of the scroll.
 */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wide)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    layout(width, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/**
 * The header of «Обставить»: «Съёмная комната · 23 места, 18 занято» — the home, then the places in the words of the language.
 */
internal fun placesWords(places: Int): StringResource =
    Formats.plural(places, Res.string.arrange_places_one, Res.string.arrange_places_few, Res.string.arrange_places_many)

/**
 * A tile of a place (spec 3.36.7, 5.29 R7): 72 × 72 at a corner of 14 on the card colour, no caption — a reader hears the [description]
 * («Люстра, Вена»), a radio button of its place, chosen or not. The chosen one is framed in the accent, ringed outside, and ticked at
 * its top end corner: not by colour alone. [empty] — «пусто», a dashed frame of 1.5 in the colour of the borders while not chosen.
 */
@Composable
private fun Choice(chosen: Boolean, description: String, onClick: () -> Unit, empty: Boolean = false, picture: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    Box(
        Modifier
            .size(TileSize)
            .then(
                if (chosen) {
                    Modifier.drawBehind {
                        val ring = ChosenRing.toPx()
                        drawRoundRect(
                            accent.copy(alpha = CHOSEN_RING_ALPHA),
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + 2 * ring, size.height + 2 * ring),
                            cornerRadius = CornerRadius(TileCorner.toPx() + ring),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(TileShape)
                .background(colors.surfaceContainer)
                .then(
                    when {
                        chosen -> Modifier.border(ChosenFrame, accent, TileShape)
                        empty -> Modifier.dashedFrame(colors.outlineVariant, corner = TileCorner)
                        else -> Modifier
                    },
                )
                .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            // the word «пусто» and the pill of the city are said by the description, once
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}, content = picture)
        }
        if (chosen) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = TickOut, y = -TickOut)
                    .size(TickCircle)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Check, contentDescription = null, size = Tick, tint = colors.onPrimary) }
        }
    }
}
