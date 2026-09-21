package com.example.violintuner.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.violintuner.feature.home.art.rememberHouseArt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import com.example.violintuner.feature.journey.cityOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.home.HomeCatalog
import com.example.violintuner.core.domain.home.HomeGroup
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.JourneyRules
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.feature.home.art.HomePicture
import com.example.violintuner.feature.home.art.ItemThumb
import com.example.violintuner.feature.home.art.homeModeNow
import com.example.violintuner.feature.journey.JourneyTopBar
import com.example.violintuner.feature.journey.TaktAmount
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.rememberSceneCamera
import com.example.violintuner.feature.journey.art.rememberSceneSeconds
import com.example.violintuner.feature.journey.art.sceneCamera
import com.example.violintuner.feature.journey.cityToOf
import java.time.LocalDate

// the shelves are places, not tables: dark wood of the maker's, striped cloth of the market (handoff 27b)
private val ShelfWood = Color(0xFF3A2A22)
private val ShelfWoodLit = Color(0xFF5A4030)
private val ShelfCloth = Color(0xFF3E3652)
private val ShelfClothLit = Color(0xFF4E4468)
private val TileShape = RoundedCornerShape(14.dp)

/** A hole in the wall is not a choice: a room always has its window and something behind it. */
private val NEVER_BARE = setOf("window", "view")

/** Takts a usual half-hour of playing brings: for the quiet hint «ещё примерно два занятия». */
private const val TAKTS_A_SESSION = 300

private fun groupLabel(group: HomeGroup): Int = when (group) {
    HomeGroup.INSTRUMENT -> R.string.shop_group_instrument
    HomeGroup.MUSIC -> R.string.shop_group_music
    HomeGroup.ROOM -> R.string.shop_group_room
    HomeGroup.LIGHT -> R.string.shop_group_light
    HomeGroup.FURNITURE -> R.string.shop_group_furniture
    HomeGroup.PLANT -> R.string.shop_group_plant
    HomeGroup.LIFE -> R.string.shop_group_life
    HomeGroup.PET -> R.string.shop_group_pet
    HomeGroup.OUTSIDE -> R.string.shop_group_outside
}

private fun shelfLabel(group: HomeGroup): Int = when (group) {
    HomeGroup.INSTRUMENT -> R.string.shop_shelf_instrument
    HomeGroup.MUSIC -> R.string.shop_shelf_music
    HomeGroup.ROOM -> R.string.shop_shelf_room
    HomeGroup.LIGHT -> R.string.shop_shelf_light
    HomeGroup.FURNITURE -> R.string.shop_shelf_furniture
    HomeGroup.PLANT -> R.string.shop_shelf_plant
    HomeGroup.LIFE -> R.string.shop_shelf_life
    HomeGroup.PET -> R.string.shop_shelf_pet
    HomeGroup.OUTSIDE -> R.string.shop_shelf_outside
}

/** What a thing on a shelf says under its name. */
private enum class Tag { PRICE, GIFT, STANDING, OWNED, LOCKED }

private fun tagOf(item: HomeItem, ui: HomeUi): Tag = when {
    HomeRules.owned(item, ui.home) -> if (HomeRules.placed(ui.home)[item.slot]?.id == item.id) Tag.STANDING else Tag.OWNED
    !HomeRules.unlocked(item, ui.progress) -> Tag.LOCKED
    item.price == 0 -> Tag.GIFT
    else -> Tag.PRICE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShopScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    ui.tryOn?.let { TryOn(it, ui, onIntent, modifier); return }
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.home_shop), onBack = { onIntent(HomeIntent.BackClicked) }) { Balance(ui) }
        if (ui.loading) return@Column
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.category == null, onClick = { onIntent(HomeIntent.CategorySelected(null)) }, label = { Text(stringResource(R.string.shop_all)) })
            HomeGroup.entries.forEach { group ->
                FilterChip(selected = ui.category == group, onClick = { onIntent(HomeIntent.CategorySelected(group)) }, label = { Text(stringResource(groupLabel(group))) })
            }
        }
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // where takts come from, said once — until the first purchase (handoff `dev`: «в лавке, первый вход»)
            if (ui.home.purchased.isEmpty()) Text(stringResource(R.string.shop_takts_note), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            HomeGroup.entries.filter { ui.category == null || ui.category == it }.forEach { group ->
                // what the room came with is not for sale
                val things = HomeCatalog.items.filter { it.group == group && it.id !in HomeCatalog.startItems }
                val wooden = group == HomeGroup.INSTRUMENT || group == HomeGroup.MUSIC
                Column(
                    Modifier.fillMaxWidth().clip(HomeCard).background(Brush.verticalGradient(if (wooden) listOf(ShelfWoodLit, ShelfWood) else listOf(ShelfClothLit, ShelfCloth))).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(shelfLabel(group)), color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        things.forEach { item -> Tile(item, ui, onIntent) }
                    }
                    if (things.all { HomeRules.owned(it, ui.home) }) {
                        Text(stringResource(R.string.shop_shelf_yours), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    ui.card?.let { item ->
        ModalBottomSheet(onDismissRequest = { onIntent(HomeIntent.CardClosed) }) { ItemCard(item, ui, onIntent) }
    }
}

/**
 * A thing on a shelf (handoff 28e): a tile of 100 dp — three in a row at 360 dp of width, four at 412 —
 * that holds the worst of the texts. Where the thing comes from is a pill on the picture, without a
 * preposition, read before the name like a label on the thing; under the board — the name in up to
 * two lines and one line: a price or a state. A thing of a city not reached yet stands faint, «привезут».
 */
@Composable
private fun Tile(item: HomeItem, ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val tag = tagOf(item, ui)
    val name = itemName(item.id)
    val city = item.from?.let { cityOf(JourneyRoute.indexOf(it)) }
    val under = when (tag) {
        Tag.PRICE -> Formats.takts(item.price.toLong())
        Tag.GIFT -> stringResource(R.string.shop_gift)
        Tag.STANDING -> stringResource(if (item.outside) R.string.shop_standing_outside else R.string.shop_standing)
        Tag.OWNED -> stringResource(R.string.shop_owned)
        Tag.LOCKED -> stringResource(R.string.shop_soon)
    }
    val description = listOfNotNull(name, city, if (tag == Tag.PRICE) stringResource(R.string.shop_price_takts, under) else under).joinToString(", ")
    Column(
        Modifier.width(100.dp).clip(TileShape)
            // a thing of the next city calls to the road: it has no card yet
            .then(if (tag == Tag.LOCKED) Modifier else Modifier.clickable(role = Role.Button) { onIntent(HomeIntent.ItemClicked(item.id)) })
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(100.dp, 76.dp).clip(TileShape).background(Color.Black.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxSize().alpha(if (tag == Tag.LOCKED) LOCKED_ALPHA else 1f)) { ItemThumb(item) }
            if (city != null) {
                Text(
                    city,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = Color.White.copy(alpha = 0.9f), fontSize = 9.5.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false,
                )
            }
        }
        Text(
            name, color = Color.White.copy(alpha = if (tag == Tag.LOCKED) 0.55f else 0.92f), fontSize = 11.sp, lineHeight = 14.sp,
            textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        if (tag == Tag.PRICE) {
            TaktAmount(under, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, icon = 12.dp)
        } else {
            Text(under, color = Color.White.copy(alpha = if (tag == Tag.STANDING) 0.92f else 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        }
    }
}

private const val LOCKED_ALPHA = 0.4f

/** Trying on starts a step back from «covering»: the floor of the room, where the violin and the rug are, must not hide under the buttons. */
private const val TRY_ON_ZOOM = 0.62f

/** The card of a thing (handoff 27b2): the thing large, a line about it, where it will stand, what will be left — and «Примерить · Купить». */
@Composable
private fun ItemCard(item: HomeItem, ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tag = tagOf(item, ui)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp).clip(HomeCard).background(colors.surfaceContainerHighest)) { ItemThumb(item) }
        item.from?.let { Text(stringResource(R.string.shop_from, cityToOf(JourneyRoute.indexOf(it))), color = colors.primary, style = MaterialTheme.typography.labelLarge) }
        Text(itemName(item.id), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        itemNote(item.id).takeIf { it.isNotEmpty() }?.let { Text(it, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.shop_place, slotName(item.at ?: item.slot)), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        // a thing whose place this home lacks stays on sale: it will move with its owner (handoff 27b4)
        val here = HomeRules.slotIn(item.slot, ui.house) && (item.at == null || HomeRules.slotIn(item.at, ui.house))
        when {
            !here -> Text(stringResource(if (item.slot == "fire") R.string.shop_needs_chimney else R.string.shop_no_place), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            !HomeRules.inSeason(item, LocalDate.now()) -> Text(stringResource(R.string.shop_waits_season), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        when (tag) {
            Tag.STANDING, Tag.LOCKED -> Unit
            Tag.OWNED -> OutlinedButton(onClick = { onIntent(HomeIntent.Placed(item.slot, item.id)); onIntent(HomeIntent.CardClosed) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.shop_put)) }
            Tag.GIFT, Tag.PRICE -> {
                if (item.price > 0) {
                    val left = ui.balance - item.price
                    val next = JourneyRules.next(ui.progress)
                    if (left >= 0) {
                        // not forbidding to spend, but showing what it costs the road (handoff `dev`)
                        Text(
                            if (next == null || left >= next.price) stringResource(R.string.shop_left_after, Formats.takts(left))
                            else stringResource(R.string.shop_left_after_road, Formats.takts(left), cityToOf(JourneyRoute.indexOf(next.id)), Formats.takts(next.price - left)),
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        val sessions = ((-left + TAKTS_A_SESSION - 1) / TAKTS_A_SESSION).toInt().coerceAtLeast(1)
                        Text(
                            if (sessions == 1) stringResource(R.string.shop_sessions_one) else stringResource(Formats.pluralRu(sessions, R.string.shop_sessions_few, R.string.shop_sessions_few, R.string.shop_sessions_many), sessions),
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (here) OutlinedButton(onClick = { onIntent(HomeIntent.TryClicked) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.shop_try)) }
                    BuyButton(item.price, ui.balance, if (item.price == 0) stringResource(R.string.shop_take) else stringResource(R.string.shop_buy, Formats.takts(item.price.toLong())), { onIntent(HomeIntent.BuyClicked) }, Modifier.weight(1.4f))
                }
            }
        }
    }
}

/**
 * Trying on (handoff 27c): one's own room on the whole screen, the thing in its place at half its
 * density inside a dashed frame that breathes, what stood there taken away for the while.
 * «вечер · день» matters here most: a chandelier and a fire are bought for the evening.
 */
@Composable
private fun TryOn(item: HomeItem, ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val camera = rememberSceneCamera()
    val mode = ui.tryMode ?: homeModeNow()
    val box = rememberHouseArt(ui.house, mode)?.items?.get(item.id)
    var size by remember { mutableStateOf(IntSize.Zero) }
    // upright the room is wider than the screen: the eye starts on the thing, not on the middle of the room
    LaunchedEffect(item.id, box != null, size) { if (box != null && size != IntSize.Zero) camera.lookAt((box.left + box.right) / 2, size.width.toFloat(), size.height.toFloat(), atZoom = TRY_ON_ZOOM) }
    Box(modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }) {
        HomePicture(
            ui.home, outside = item.outside, mode = mode, description = itemName(item.id), ghost = item,
            modifier = Modifier.fillMaxSize().sceneCamera(camera), seconds = rememberSceneSeconds(), camera = camera::read,
        )
        TwoWay(stringResource(R.string.home_evening), stringResource(R.string.home_day), mode == SceneMode.DAY, { onIntent(HomeIntent.TryModeSelected(if (it) SceneMode.DAY else SceneMode.EVENING)) }, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))).padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(itemName(item.id), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(slotName(item.at ?: item.slot), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // «Убрать» keeps its own width, buying takes the rest: the row does not break at 360 dp (handoff 28f)
                OutlinedButton(onClick = { onIntent(HomeIntent.TryClosed) }, modifier = Modifier.height(52.dp)) { Text(stringResource(R.string.shop_try_remove), color = Color.White, maxLines = 1, softWrap = false) }
                BuyButton(item.price, ui.balance, if (item.price == 0) stringResource(R.string.shop_take) else stringResource(R.string.shop_buy, Formats.takts(item.price.toLong())), { onIntent(HomeIntent.BuyClicked) }, Modifier.weight(1f))
            }
        }
    }
}

/**
 * «Обставить» (handoff 27d): the room on top, alive and answering; below — the places, each with
 * what was bought for it in a row. A tap replaces at once; «пусто» leaves the place bare. No
 * dragging: a thing knows its place, the room is always put together.
 */
@Composable
fun ArrangeScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.arrange_title), onBack = { onIntent(HomeIntent.BackClicked) }) {
            TwoWay(stringResource(R.string.home_room), stringResource(R.string.home_outside), ui.outside, { onIntent(HomeIntent.SideSelected(it)) })
        }
        if (ui.loading) return@Column
        HomePicture(
            ui.home, ui.outside, homeModeNow(), description = houseName(ui.house),
            modifier = Modifier.widthIn(max = HomeMaxWidth).fillMaxWidth().padding(horizontal = 16.dp).height(210.dp).clip(RoundedCornerShape(20.dp)), seconds = rememberSceneSeconds(),
        )
        val placed = HomeRules.placed(ui.home)
        val slots = HomeCatalog.slots.filter { it.outside == ui.outside }
        val here = slots.filter { HomeRules.slotIn(it.id, ui.house) }
        val filled = here.filter { HomeRules.wardrobe(it.id, ui.home).isNotEmpty() }
        Column(
            Modifier.widthIn(max = HomeMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${houseName(ui.house)} · " + stringResource(R.string.arrange_places_few, here.size, here.count { placed[it.id] != null }), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            filled.forEach { slot ->
                val things = HomeRules.wardrobe(slot.id, ui.home)
                val standing = placed[slot.id]?.id
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slotName(slot.id), modifier = Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
                        val more = HomeCatalog.items.count { it.slot == slot.id && !HomeRules.owned(it, ui.home) }
                        if (more > 0) Text(stringResource(R.string.arrange_in_shop, more), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        things.forEach { item ->
                            Choice(chosen = standing == item.id, label = itemName(item.id), onClick = { onIntent(HomeIntent.Placed(slot.id, item.id)) }) { ItemThumb(item) }
                        }
                        // walls and a floor cannot be bare; a desk without a lamp is a choice too
                        if (!slot.palette && slot.id !in NEVER_BARE) Choice(chosen = standing == null, label = stringResource(R.string.arrange_empty), onClick = { onIntent(HomeIntent.Placed(slot.id, "")) }) {}
                    }
                }
            }
            val missing = slots.filter { !HomeRules.slotIn(it.id, ui.house) && HomeRules.wardrobe(it.id, ui.home).isNotEmpty() }
            if (missing.isNotEmpty()) {
                Text(stringResource(R.string.arrange_not_here), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                missing.forEach { slot ->
                    val waiting = HomeRules.wardrobe(slot.id, ui.home).map { itemName(it.id) }.joinToString(", ")
                    Text("${slotName(slot.id)} — " + stringResource(R.string.arrange_waiting, waiting), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Choice(chosen: Boolean, label: String, onClick: () -> Unit, picture: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp, 64.dp).clip(TileShape).background(colors.surfaceContainer)
                .then(if (chosen) Modifier.border(2.dp, colors.primary, TileShape) else Modifier)
                .clickable(onClickLabel = label, role = Role.RadioButton, onClick = onClick),
        ) { picture() }
        Text(label, color = if (chosen) colors.primary else colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
