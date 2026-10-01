package com.violinjourney.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The home and «Дома» of R7 (spec 3.36.7; journey-home.html 6 and 10, landscape.html 4) on the data of the mockups: the small wooden
// house lived in, Vienna reached with 47 884 takts in the purse; the next home — the flat — not drawn yet. Pictures are read
// asynchronously: an empty frame in a preview is fine.

private val VIENNA = JourneyRoute.indexOf("vienna")

/** The journey stood in Vienna with [balance] takts in the purse: every leg to it paid. */
private fun progressAt(balance: Long): JourneyProgress {
    val spent = JourneyRoute.stops.take(VIENNA + 1).sumOf { it.price.toLong() }
    return JourneyProgress(earned = spent + balance, spent = spent, arrivals = (0..VIENNA).map { Arrival(JourneyRoute.stops[it].id, 0L) }, extras = emptySet())
}

/** Seven things, three of them brought from the road; the gift taken. */
private val Bought = setOf(HomeCatalog.GIFT, "vln_master", "case_velvet", "stand_wood", "metronome", "portrait", "notes")

private val InTheWoodenHouse = HomeState(loaded = true, purchased = Bought, houses = setOf("wood"), choices = mapOf(HomeState.HOUSE_KEY to "wood"))

private fun homeUi(home: HomeState = InTheWoodenHouse, balance: Long = 47_884, outside: Boolean = false) = HomeUi(
    loading = false,
    home = home,
    progress = progressAt(balance),
    house = home.choices[HomeState.HOUSE_KEY] ?: HomeCatalog.START_HOUSE,
    outside = outside,
)

private val Usual = homeUi()
private val GiftWaits = homeUi(InTheWoodenHouse.copy(purchased = Bought - HomeCatalog.GIFT))

/** Nothing bought, the rented room: the gift waits, and the wooden house is 1 200 / 3 000 away. */
private val NothingBought = homeUi(HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap()), balance = 1_200)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Screen(content: @Composable () -> Unit) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) { content() }
        }
    }
}

@Preview(name = "Дом · обычное: комната 290, «Комната | Снаружи» справа сверху, строка следующего дома «скоро · 6 000», внизу «В дорогу» · «Лавка» · «Обставить»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HomePreview() = Screen { HomeScreen(Usual, onIntent = {}) }

@Preview(name = "Дом · подарок ждёт: карточка с «Забрать»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HomeGiftPreview() = Screen { HomeScreen(GiftWaits, onIntent = {}) }

@Preview(name = "Дом · снаружи", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HomeOutsidePreview() = Screen { HomeScreen(Usual.copy(outside = true), onIntent = {}) }

@Preview(name = "Дом · ничего не куплено: «всё как было, когда сняли», подарок, «1 200 / 3 000» с полосой", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HomeNothingBoughtPreview() = Screen { HomeScreen(NothingBought, onIntent = {}) }

@Preview(name = "Дом · следующего дома нет: строка «Все дома ›»", locale = "ru", widthDp = 412)
@Composable
private fun NoNextHousePreview() {
    ViolinTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) { NextHouseRow(next = null, balance = 47_884, onClick = {}) }
    }
}

@Preview(name = "Дом · 360 × 640: комната уступает (≈ 260–285), зона видна целиком", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun HomeSmallPreview() = Screen { HomeScreen(Usual, onIntent = {}) }

@Preview(name = "Дом · 360 × 640, шрифт 1,3: «Обставить» не рвётся", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun HomeSmallLargePreview() = Screen { HomeScreen(GiftWaits, onIntent = {}) }

@Preview(name = "Дом · landscape 892 × 412: комната слева на всю высоту, под ней строка из трёх, без затухания; справа 320", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun HomeLandscapePreview() = Screen { HomeScreen(GiftWaits, onIntent = {}) }

@Preview(name = "Дом · landscape 640 × 360: колонка уже 440 — «В дорогу» сверху, «Лавка» и «Обставить» половинками", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun HomeLandscape640Preview() = Screen { HomeScreen(Usual, onIntent = {}) }

@Preview(name = "Дом · landscape 603 × 308 (эмулятор за вырезом): половинки в колонке 235", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun HomeLandscape603Preview() = Screen { HomeScreen(Usual, onIntent = {}) }

@Preview(name = "Дом · landscape 603 × 308, шрифт 1,3: половинки одна под другой, комната низкая — квадрата «на весь экран» нет", locale = "ru", fontScale = 1.3f, device = "spec:width=603dp,height=308dp")
@Composable
private fun HomeLandscape603LargePreview() = Screen { HomeScreen(Usual, onIntent = {}) }

@Preview(name = "Дом · landscape 892 × 412, шрифт 1,3: «Забрать» под словами подарка — рядом рвалось «Бесплатно.»", locale = "ru", fontScale = 1.3f, device = "spec:width=892dp,height=412dp")
@Composable
private fun HomeLandscapeGiftLargePreview() = Screen { HomeScreen(GiftWaits, onIntent = {}) }

@Preview(name = "Дома · живу в домике: «здесь живу» с рамкой, у съёмной комнаты «Жить здесь», дальние — «скоро · …»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HousesPreview() = Screen { HousesScreen(Usual, onIntent = {}) }

@Preview(name = "Дома · домик не куплен: «1 200 / 3 000», шеврон, полоса под заметкой", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun HousesShortPreview() = Screen { HousesScreen(NothingBought, onIntent = {}) }

@Preview(name = "Дома · 360, домик не куплен, хватает: «хватает · 3 000 ›» под заметкой — имя целиком", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun HousesSmallEnoughPreview() = Screen { HousesScreen(homeUi(HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap())), onIntent = {}) }

@Preview(name = "Дома · de 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun HousesGermanLargePreview() = Screen { HousesScreen(NothingBought, onIntent = {}) }

private val Wood = HomeCatalog.houseById.getValue("wood")

@Preview(name = "Лист дома · хватает: «Переехать · 3 000»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun HouseSheetEnoughPreview() {
    val ui = homeUi(HomeState(loaded = true, purchased = Bought, houses = emptySet(), choices = emptyMap()))
    ViolinTheme {
        AppSheetCard(bottom = { HouseSheetButtons(Wood, ui, onIntent = {}) }) { HouseSheetContent(Wood, ui, Modifier.fillMaxWidth()) }
    }
}

@Preview(name = "Лист дома · не хватает: плашка «не хватает 1 800» и под ней «примерно 6 занятий»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun HouseSheetShortPreview() {
    val ui = homeUi(HomeState(loaded = true, purchased = Bought, houses = emptySet(), choices = emptyMap()), balance = 1_200)
    ViolinTheme {
        AppSheetCard(bottom = { HouseSheetButtons(Wood, ui, onIntent = {}) }) { HouseSheetContent(Wood, ui, Modifier.fillMaxWidth()) }
    }
}
