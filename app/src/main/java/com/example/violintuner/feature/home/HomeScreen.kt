package com.example.violintuner.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.home.HomeCatalog
import com.example.violintuner.core.domain.home.HomeHouse
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.motion.LocalReduceMotion
import com.example.violintuner.feature.home.art.HomePicture
import com.example.violintuner.feature.home.art.HomeSilhouettes
import com.example.violintuner.feature.home.art.ItemThumb
import com.example.violintuner.feature.home.art.homeModeNow
import com.example.violintuner.feature.journey.JourneyTopBar
import com.example.violintuner.feature.journey.PriceBar
import com.example.violintuner.feature.journey.TaktAmount
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.rememberSceneSeconds

internal val HomeMaxWidth = 560.dp
internal val HomeCard = RoundedCornerShape(16.dp)
private val PictureShape = RoundedCornerShape(20.dp)
private val PictureHeight = 260.dp

@Composable
internal fun itemName(id: String): String = HomeTexts.itemNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun itemNote(id: String): String = HomeTexts.itemNotes[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun slotName(id: String): String = HomeTexts.slotNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun houseName(id: String): String = HomeTexts.houseNames[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun houseNote(id: String): String = HomeTexts.houseNotes[id]?.let { stringResource(it) }.orEmpty()

@Composable
internal fun thingsInWords(count: Int): String = stringResource(Formats.pluralRu(count, R.string.home_things_one, R.string.home_things_few, R.string.home_things_many), count)

@Composable
internal fun Balance(ui: HomeUi) {
    TaktAmount(Formats.takts(ui.balance), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), icon = 18.dp)
}

/** Two words on one track: «Комната · Снаружи», «вечер · день». */
@Composable
internal fun TwoWay(first: String, second: String, secondChosen: Boolean, onChoose: (second: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(colors.surface.copy(alpha = 0.78f)).padding(3.dp)) {
        listOf(first to false, second to true).forEach { (word, isSecond) ->
            val chosen = isSecond == secondChosen
            Text(
                word,
                modifier = Modifier.clip(CircleShape).background(if (chosen) colors.primary else Color.Transparent)
                    .clickable(role = Role.Tab) { onChoose(isSecond) }.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (chosen) colors.onPrimary else colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** The home as its own section (spec 3.24, handoff 27a): the room alive, what it is, the two doors — the shop and the wardrobe — and the home to save for. */
@Composable
fun HomeScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.home_title), onBack = { onIntent(HomeIntent.BackClicked) }) { Balance(ui) }
        if (ui.loading) return@Column
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Picture(ui, onIntent, Modifier.weight(1f).fillMaxHeight().padding(bottom = 16.dp))
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { About(ui, onIntent) }
                }
            } else {
                Column(
                    Modifier.widthIn(max = HomeMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Picture(ui, onIntent, Modifier.fillMaxWidth().height(PictureHeight))
                    About(ui, onIntent)
                }
            }
        }
    }
    ui.moving?.let { Moving(it, ui) }
}

@Composable
private fun Picture(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier) {
    val name = houseName(ui.house)
    Box(modifier.clip(PictureShape)) {
        HomePicture(
            ui.home, ui.outside, homeModeNow(),
            description = stringResource(if (ui.outside) R.string.home_picture_outside else R.string.home_picture_room, name),
            modifier = Modifier.fillMaxSize(), seconds = rememberSceneSeconds(),
        )
        TwoWay(stringResource(R.string.home_room), stringResource(R.string.home_outside), ui.outside, { onIntent(HomeIntent.SideSelected(it)) }, Modifier.align(Alignment.TopStart).padding(10.dp))
    }
}

@Composable
private fun About(ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (things, brought) = HomeRules.counts(ui.home)
    Column {
        Text(houseName(ui.house), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(
            when {
                things == 0 -> stringResource(R.string.home_things_none)
                brought == 0 -> thingsInWords(things)
                else -> stringResource(R.string.home_things_brought, thingsInWords(things), brought)
            },
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onIntent(HomeIntent.ShopClicked) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.home_shop)) }
        OutlinedButton(onClick = { onIntent(HomeIntent.ArrangeClicked) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.home_arrange)) }
    }
    if (HomeRules.giftWaiting(ui.home)) {
        // the shop begins with a joy, not with a price tag (handoff 27a3)
        Row(Modifier.fillMaxWidth().clip(HomeCard).background(colors.primaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface.copy(alpha = 0.5f))) { ItemThumb(HomeCatalog.byId.getValue(HomeCatalog.GIFT)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_gift_title), color = colors.onPrimaryContainer, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_gift_text), color = colors.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onIntent(HomeIntent.GiftTaken) }) { Text(stringResource(R.string.home_gift_take)) }
        }
    }
    val broughtThings = HomeCatalog.items.filter { it.from != null && it.id in ui.home.purchased }
    if (broughtThings.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_brought), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(broughtThings, key = { it.id }) { item ->
                    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(84.dp, 64.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer)) { ItemThumb(item) }
                        Text(itemName(item.id), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    HomeRules.nextHouse(ui.home)?.let { next ->
        // the same count and bar as the way ahead: saving for a home and for the road look alike
        Column(
            Modifier.fillMaxWidth().clip(HomeCard).background(colors.surfaceContainer).clickable(role = Role.Button) { onIntent(HomeIntent.HousesClicked) }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HouseSilhouette(next.id, colors.onSurfaceVariant, Modifier.size(72.dp, 44.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_next_house), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Text(houseName(next.id), color = colors.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TaktAmount(stringResource(R.string.home_have, Formats.takts(ui.balance), Formats.takts(next.price.toLong())), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, icon = 14.dp)
            }
            PriceBar((ui.balance.toFloat() / next.price).coerceIn(0f, 1f))
        }
    }
    TextButton(onClick = { onIntent(HomeIntent.HousesClicked) }) { Text(stringResource(R.string.home_all_houses)) }
}

@Composable
internal fun HouseSilhouette(id: String, color: Color, modifier: Modifier = Modifier, outline: Boolean = false) {
    val paths = remember(id) { HomeSilhouettes.paths[id].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier) {
        val k = minOf(size.width / 200f, size.height / 120f)
        translate((size.width - 200f * k) / 2, (size.height - 120f * k) / 2) {
            scale(k, k, pivot = Offset.Zero) { paths.forEach { if (outline) drawPath(it, color, style = Stroke(3f)) else drawPath(it, color) } }
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
        Text(stringResource(R.string.houses_moving), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        HomePicture(
            ui.home, outside = true, mode = SceneMode.EVENING, description = houseName(house.id), house = house.id,
            modifier = Modifier.widthIn(max = HomeMaxWidth).fillMaxWidth().height(PictureHeight).graphicsLayer { alpha = shown.value }.clip(PictureShape),
            seconds = rememberSceneSeconds(),
        )
        Text(houseName(house.id), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        val moved = stringResource(R.string.houses_moved_things, thingsInWords(things))
        Text(
            if (HomeRules.catOnPorch(ui.home, house.id) != null) stringResource(R.string.houses_moved_cat, moved) else moved,
            color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
    }
}

/** The row of homes (handoff 27e1): where one lives, the next with what is saved, the far ones as silhouettes with a price. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HousesScreen(ui: HomeUi, onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        JourneyTopBar(stringResource(R.string.houses_title), onBack = { onIntent(HomeIntent.BackClicked) }) { Balance(ui) }
        if (ui.loading) return@Column
        val owned = HomeRules.ownedHouses(ui.home)
        Column(
            Modifier.widthIn(max = HomeMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeCatalog.houses.forEach { house ->
                val mine = house.id in owned
                val here = house.id == ui.house
                val buyable = house.drawn && !mine
                Row(
                    Modifier.fillMaxWidth().clip(HomeCard).background(colors.surfaceContainer)
                        .then(if (here) Modifier.border(1.5.dp, colors.primary, HomeCard) else Modifier)
                        .then(if (buyable) Modifier.clickable(role = Role.Button) { onIntent(HomeIntent.HouseClicked(house.id)) } else Modifier)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HouseSilhouette(house.id, if (mine) colors.primary else colors.outline, Modifier.size(84.dp, 52.dp), outline = !mine && !house.drawn)
                    Column(Modifier.weight(1f)) {
                        Text(houseName(house.id), color = if (mine || house.drawn) colors.onSurface else colors.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                        Text(houseNote(house.id), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        if (buyable) {
                            Spacer(Modifier.height(8.dp))
                            PriceBar((ui.balance.toFloat() / house.price).coerceIn(0f, 1f))
                        }
                    }
                    when {
                        here -> Text(stringResource(R.string.houses_live_here), color = colors.primary, style = MaterialTheme.typography.labelLarge)
                        mine -> TextButton(onClick = { onIntent(HomeIntent.LiveHere(house.id)) }) { Text(stringResource(R.string.houses_live)) }
                        else -> Column(horizontalAlignment = Alignment.End) {
                            TaktAmount(Formats.takts(house.price.toLong()), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, icon = 14.dp)
                            if (!house.drawn) Text(stringResource(R.string.home_house_soon), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    ui.houseCard?.let { house ->
        ModalBottomSheet(onDismissRequest = { onIntent(HomeIntent.HouseCardClosed) }) { HouseCard(house, ui, onIntent) }
    }
    ui.moving?.let { Moving(it, ui) }
}

@Composable
private fun HouseCard(house: HomeHouse, ui: HomeUi, onIntent: (HomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePicture(ui.home, outside = true, mode = SceneMode.DAY, description = houseName(house.id), house = house.id, modifier = Modifier.fillMaxWidth().height(200.dp).clip(PictureShape), seconds = rememberSceneSeconds())
        Text(stringResource(R.string.houses_of, HomeCatalog.houses.indexOf(house), HomeCatalog.houses.size - 1), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(houseName(house.id), color = colors.onSurface, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(houseNote(house.id), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        val news = HomeTexts.houseNews[house.id]?.let { stringArrayResource(it) }.orEmpty()
        if (news.isNotEmpty()) {
            Text(stringResource(R.string.houses_news), color = colors.onSurface, style = MaterialTheme.typography.titleSmall)
            news.forEach { Text("· $it", color = colors.onSurface, style = MaterialTheme.typography.bodyMedium) }
        }
        Text(stringResource(R.string.houses_moving_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        BuyButton(price = house.price, balance = ui.balance, label = stringResource(R.string.houses_move, Formats.takts(house.price.toLong())), onBuy = { onIntent(HomeIntent.MoveClicked) })
    }
}

/** Enough — a filled button; short — a tonal one that says how much is missing: it tells, it does not forbid (handoff 26a3). */
@Composable
internal fun BuyButton(price: Int, balance: Long, label: String, onBuy: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    if (balance >= price) {
        Button(onClick = onBuy, modifier = modifier.fillMaxWidth().height(height)) { TaktAmount(label, style = MaterialTheme.typography.titleMedium, icon = 18.dp) }
    } else {
        FilledTonalButton(onClick = {}, enabled = false, modifier = modifier.fillMaxWidth().height(height)) {
            TaktAmount(stringResource(R.string.shop_missing, Formats.takts(price - balance)), style = MaterialTheme.typography.titleMedium, icon = 18.dp)
        }
    }
}
