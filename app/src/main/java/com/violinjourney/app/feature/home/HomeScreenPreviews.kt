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
import kotlinx.datetime.LocalDate

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

@Preview(name = "Дом · половина разделённого экрана 412 × 450: окно выше, чем шире, — стоя (рядом со словами комнате осталось бы 44)", locale = "ru", device = "spec:width=412dp,height=450dp")
@Composable
private fun HomeSplitPreview() = Screen { HomeScreen(Usual, onIntent = {}) }

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

// The shop, the card of a thing, the try-on and «Обставить» of R7 (stage 119; journey-home.html 7–9). The date of the cards is fixed —
// the tree's season is told by it; the rooms of the try-on and of «Обставить» follow the clock of the machine, as on a phone.

private val October: LocalDate = LocalDate(2026, 10, 1)

private fun thing(id: String) = HomeCatalog.byId.getValue(id)

/** The journey stood in London with [balance] takts in the purse: the next city is Saint Petersburg, 6 000 away. */
private fun progressAtLondon(balance: Long): JourneyProgress {
    val london = JourneyRoute.indexOf("london")
    val spent = JourneyRoute.stops.take(london + 1).sumOf { it.price.toLong() }
    return JourneyProgress(earned = spent + balance, spent = spent, arrivals = (0..london).map { Arrival(JourneyRoute.stops[it].id, 0L) }, extras = emptySet())
}

/** Every leg of the road paid and reached, Sydney last: no next city. */
private fun progressAtTheEnd(balance: Long): JourneyProgress {
    val spent = JourneyRoute.stops.sumOf { it.price.toLong() }
    return JourneyProgress(earned = spent + balance, spent = spent, arrivals = JourneyRoute.stops.map { Arrival(it.id, 0L) }, extras = emptySet())
}

@Preview(name = "Лавка · полки: чипы рядов лентой, «Всё» выбран, таблетка города на плотном стекле", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ShopPreview() = Screen { ShopScreen(Usual, onIntent = {}) }

@Preview(name = "Лавка · первый подарок: строка о тактах над первой полкой, подарок на полке мастера", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ShopGiftPreview() = Screen { ShopScreen(NothingBought, onIntent = {}) }

@Preview(name = "Лавка по месту «На столе, справа»: чип места с крестиком первым, только его вещи, ряды без них ушли", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ShopByPlacePreview() = Screen { ShopScreen(Usual.copy(slot = "deskR"), onIntent = {}) }

@Preview(name = "Лавка по месту · de 360 при 1,3: лента чипов листается вбок", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun ShopByPlaceGermanPreview() = Screen { ShopScreen(Usual.copy(slot = "deskR"), onIntent = {}) }

/** The card of [id] in a sheet of R1, its buttons pinned at the bottom (none for a thing that has nothing to press). */
@Composable
private fun Card(id: String, ui: HomeUi) {
    val item = thing(id)
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            AppSheetCard(bottom = { ItemCardButtons(item, ui, onIntent = {}, today = October) }) {
                ItemCardContent(item, ui, Modifier.fillMaxWidth(), today = October)
            }
        }
    }
}

@Preview(name = "Карточка · хватает: «из Вены», «Место», «После покупки — останется 46 984», «До Праги — всё ещё хватает», «Примерить» · «Купить · 900»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardEnoughPreview() = Card("chandelier", Usual)

@Preview(name = "Карточка · не хватает (люстра 900 при 472): «Баланс — 472», плашка «не хватает 428», «примерно 2 занятия»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardShortPreview() = Card("chandelier", homeUi(balance = 472))

@Preview(name = "Карточка · маршрут пройден: строки «До …» нет", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardEndPreview() = Card("chandelier", Usual.copy(progress = progressAtTheEnd(47_884)))

@Preview(name = "Карточка · нет места: пианино в домике — «нет места в этом доме», «Купить · 5 000» во всю ширину", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardNoPlacePreview() = Card("piano", Usual)

@Preview(name = "Карточка · нужен дом с трубой: камин в съёмной комнате", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardChimneyPreview() = Card("fireplace", homeUi(HomeState(loaded = true, purchased = Bought, houses = emptySet(), choices = emptyMap())))

@Preview(name = "Карточка · не сезон (1 октября): ёлка — «ждёт декабря», «Примерить» · «Купить · 500»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardSeasonPreview() = Card("xmas", Usual)

@Preview(name = "Карточка · купленная, место есть: одна главная «Поставить»", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardOwnedPreview() = Card("case_velvet", Usual)

@Preview(name = "Карточка · куплена и места нет: чип «куплено», у «Места» — «нет места в этом доме», кнопок нет", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardOwnedNoPlacePreview() =
    Card("piano", homeUi(InTheWoodenHouse.copy(purchased = Bought + "piano", choices = InTheWoodenHouse.choices + ("floorL" to "piano"))))

@Preview(name = "Карточка · стоит: чип «в комнате», кнопок нет", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardStandingPreview() = Card("metronome", homeUi(InTheWoodenHouse.copy(choices = InTheWoodenHouse.choices + ("deskM" to "metronome"))))

@Preview(name = "Карточка · подарок: «Примерить» · «Забрать», строк о тактах нет", locale = "ru", widthDp = 412, heightDp = 892)
@Composable
private fun CardGiftPreview() = Card(HomeCatalog.GIFT, GiftWaits)

@Preview(name = "Карточка · 360 при 1,3: «Примерить» не встаёт в свою долю — кнопки одна под другой", locale = "ru", fontScale = 1.3f, widthDp = 360, heightDp = 760)
@Composable
private fun CardSmallLargePreview() = Card("chandelier", Usual)

@Preview(name = "Карточка · de 360: «Anprobieren» мельче или под «Kaufen · 900»", locale = "de", widthDp = 360, heightDp = 760)
@Composable
private fun CardGermanPreview() = Card("chandelier", Usual)

@Preview(name = "Карточка · it 360 при 1,3, до Санкт-Петербурга: ключ «Per San / Pietroburgo» по пробелу, «mancano 1 900» целиком", locale = "it", fontScale = 1.3f, widthDp = 360, heightDp = 760)
@Composable
private fun CardToSaintPetersburgPreview() = Card("chandelier", Usual.copy(progress = progressAtLondon(5_000)))

@Preview(name = "Примерка: шапка «‹ Примерка · Люстра» и «вечер | день» на стекле; внизу «Убрать» по слову и «Купить · 900»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TryOnPreview() = Screen { ShopScreen(Usual.copy(tryOn = thing("chandelier")), onIntent = {}) }

@Preview(name = "Примерка · не хватает: «Убрать» и плашка «не хватает 428» без подсказки о занятиях", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TryOnShortPreview() = Screen { ShopScreen(homeUi(balance = 472).copy(tryOn = thing("chandelier")), onIntent = {}) }

/** The master's violin from Cremona put in its place: its tile is chosen, and the pill of its city ends before the tick. */
private val MastersViolin = homeUi(InTheWoodenHouse.copy(choices = InTheWoodenHouse.choices + ("violin" to "vln_master")))

/** The desk touched last in «Обставить»: the plain desk the home came with stands there. */
private val DeskTouched = ArrangeFocus("desk", "desk_simple")

@Preview(name = "Обставить · обводка стола: «пусто» первой, плитки 72 с галочкой, у выбранной скрипки «Кремона» кончается до галочки, «в лавке N →»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ArrangeOutlinePreview() = Screen { ArrangeScreen(MastersViolin.copy(arrangeFocus = DeskTouched), onIntent = {}) }

@Preview(name = "Обставить · без обводки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ArrangePreview() = Screen { ArrangeScreen(Usual, onIntent = {}) }

@Preview(name = "Обставить · снаружи", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ArrangeOutsidePreview() = Screen { ArrangeScreen(Usual.copy(outside = true), onIntent = {}) }

@Preview(name = "Обставить · landscape 603 × 308 (эмулятор 640 × 360): комната слева, места справа — до них дотянуться", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun ArrangeLowPreview() = Screen { ArrangeScreen(Usual.copy(arrangeFocus = DeskTouched), onIntent = {}) }

@Preview(name = "Обставить · landscape 892 × 412", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun ArrangeLandscapePreview() = Screen { ArrangeScreen(Usual, onIntent = {}) }

@Preview(name = "Обставить · половина разделённого экрана 412 × 450: окно выше, чем шире, — стоя; комната уступает, первое место целиком", locale = "ru", device = "spec:width=412dp,height=450dp")
@Composable
private fun ArrangeSplitPreview() = Screen { ArrangeScreen(Usual, onIntent = {}) }

@Preview(name = "Обставить · половина экрана лёжа 456 × 411: рядом с местами комнате меньше 200 — стоя", locale = "ru", device = "spec:width=456dp,height=411dp")
@Composable
private fun ArrangeSplitLyingPreview() = Screen { ArrangeScreen(Usual, onIntent = {}) }
