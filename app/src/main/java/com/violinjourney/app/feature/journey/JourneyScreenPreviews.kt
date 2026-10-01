package com.violinjourney.app.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.BoughtExtra
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyExtra
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

// The journey of R7 (spec 3.36.7; journey-home.html 1–2, landscape.html «Путешествие») on the data of the mockups: Vienna reached on
// 20 September with 47 884 takts in the purse, Prague next for 1 600; short of it — 472, 1 128 missing, about 4 practices. Over the
// tabs: the status bar over it, no bar of the tabs. Pictures are read asynchronously: an empty frame in a preview is fine.

/** The day each stop was reached: home on 5 September, Cremona on the 7th, Milan on the 12th, Salzburg on the 18th, Vienna on the 20th… */
private val Days = listOf(5, 7, 12, 18, 20, 22, 23, 24, 25, 26, 27, 28, 28, 29, 29, 30, 30)

private fun dayMs(index: Int): Long = LocalDate(2026, 9, Days[index]).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** The journey stood at the stop [index] with [balance] takts in the purse: every leg to it paid. */
private fun progressAt(index: Int, balance: Long): JourneyProgress {
    val spent = JourneyRoute.stops.take(index + 1).sumOf { it.price.toLong() }
    return JourneyProgress(
        earned = spent + balance,
        spent = spent,
        arrivals = (0..index).map { Arrival(JourneyRoute.stops[it].id, dayMs(it)) },
        extras = emptySet(),
    )
}

private val VIENNA = JourneyRoute.indexOf("vienna")
private val PRAGUE = JourneyRoute.indexOf("prague")
private val SYDNEY = JourneyRoute.stops.lastIndex

private fun stateAt(index: Int, balance: Long, phase: JourneyPhase? = null): JourneyState =
    JourneyReducer.stateOf(progressAt(index, balance), phase, JourneyConfig())

private val Enough = stateAt(VIENNA, 47_884)
private val Short = stateAt(VIENNA, 472)
private val TourDone = stateAt(SYDNEY, 2_516)
private val AtHome = stateAt(0, 340)
private val AtHomeShort = stateAt(0, 128)
private val Intro = JourneyReducer.stateOf(JourneyProgress(earned = 180, spent = 0, arrivals = emptyList(), extras = emptySet()), null, JourneyConfig())

/** The leg to Prague is paid when the road starts: the arrival and the page of the stamp stand in Prague already. */
private val Arrived = stateAt(PRAGUE, 46_284, JourneyPhase.Arrival(JourneyRoute.stops[PRAGUE], PRAGUE))
private val Stamped = stateAt(PRAGUE, 46_284, JourneyPhase.Stamp(JourneyRoute.stops[PRAGUE], PRAGUE))
private val StampedSydney = stateAt(SYDNEY, 2_516, JourneyPhase.Stamp(JourneyRoute.stops[SYDNEY], SYDNEY))

/** The home as the door and the stop «Дом» show it: the small wooden house as it was when it was taken. */
private val Home = HomeState(loaded = true, purchased = emptySet(), houses = emptySet(), choices = emptyMap())

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Journey(state: JourneyState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true, LocalHomeLook provides Home) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                JourneyScreen(state, onIntent = {})
            }
        }
    }
}

@Preview(name = "Путешествие · хватает: карточка пути с ценой, «В путь · Прага», «спишется 1 600 из 47 884»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EnoughPreview() = Journey(Enough)

@Preview(name = "Путешествие · не хватает: «472 / 1 600» с полосой, плашка «не хватает 1 128 · примерно 4 занятия»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ShortPreview() = Journey(Short)

@Preview(name = "Путешествие · маршрут пройден: «Мировое турне пройдено» с балансом, без кнопки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TourDonePreview() = Journey(TourDone)

@Preview(name = "Путешествие · на остановке «Дом»: дверь «вы дома» не залита, «В путь · Кремона», «Пройдено 0 из 16»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AtHomePreview() = Journey(AtHome)

@Preview(name = "Путешествие · дома, не хватает: «не хватает 172 · примерно 1 занятие»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AtHomeShortPreview() = Journey(AtHomeShort)

@Preview(name = "Путешествие · вступление: «Собрать футляр» в нижней зоне", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun IntroPreview() = Journey(Intro)

@Preview(name = "Путешествие · прибытие: «Поставить штамп» со знаком штампа в нижней зоне", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ArrivalPreview() = Journey(Arrived)

@Preview(name = "Путешествие · штамп: «Штамп № 5», «До Лейпцига — 2 000 тактов», «Готово» и «Играть здесь»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StampPreview() = Journey(Stamped)

@Preview(name = "Путешествие · штамп Сиднея: «Дальше дорога ещё рисуется.»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StampSydneyPreview() = Journey(StampedSydney)

@Preview(name = "Путешествие · landscape 892 × 412: зона в левой колонке 360, справа только лента", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Journey(Enough)

@Preview(name = "Путешествие · landscape 892 × 412, не хватает: плашка в левой колонке", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeShortPreview() = Journey(Short)

@Preview(name = "Путешествие · landscape 640 × 360: шапка 48, кнопка 48, «поездом» не рвётся", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun Landscape640Preview() = Journey(Enough)

@Preview(name = "Путешествие · landscape 603 × 308 (эмулятор за вырезом): зона не прячет кнопку", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun Landscape603Preview() = Journey(Short)

@Preview(name = "Путешествие · штамп в landscape 640 × 360: рамка уступает, «Штамп № 5» и перегон справа от неё, над затуханием зоны", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun StampLandscapePreview() = Journey(Stamped)

@Preview(name = "Путешествие · штамп в landscape 892 × 412: рамка слева уступает месту над затуханием, слова справа, «Готово» и «Играть здесь» в зоне", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun StampLandscape892Preview() = Journey(Stamped)

@Preview(name = "Путешествие · вступление в landscape 640 × 360: открытка слева, заголовок справа, «Собрать футляр» виден сразу", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun IntroLandscapePreview() = Journey(Intro)

@Preview(name = "Путешествие · прибытие в landscape 603 × 308 (эмулятор за вырезом): открытка слева, «Прага» и «место · страна» справа, над затуханием зоны", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun ArrivalLandscapePreview() = Journey(Arrived)

@Preview(name = "Путешествие · 360 × 640: открытка 240, «Путешествие» целиком, «В путь» в зоне", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Journey(Enough)

@Preview(name = "Путешествие · de 360, шрифт 1,3: строки кнопки в одну строку, числа целы", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargePreview() = Journey(stateAt(JourneyRoute.indexOf("london"), 147_884))

@Preview(name = "Путешествие · de 360, шрифт 1,3, не хватает: плашка в две строки", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargeShortPreview() = Journey(Short)

@Preview(name = "Путешествие · fr 360: «En route · Saint-Pétersbourg», «… sur … seront dépensées»", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Journey(stateAt(JourneyRoute.indexOf("london"), 147_884))

@Preview(name = "Путешествие · fr 360, шрифт 1,3: число под строкой пути, если слово не встаёт рядом", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchLargeShortPreview() = Journey(stateAt(JourneyRoute.indexOf("london"), 1_472))

// The stop, the map and the passport of R7 (spec 3.36.7; journey-home.html 3–5, landscape.html «Остановка»): Vienna on 20 September.

private val CREMONA = JourneyRoute.indexOf("cremona")

/**
 * The stop [index] with [balance] in the purse and the extras [bought] — as [StopViewModel] reads them: the views bought are open, the
 * one shown is [inside] (the main view where not given) and by [day].
 */
private fun stopAt(index: Int, balance: Long, bought: Set<JourneyExtra> = emptySet(), day: Boolean = false, inside: Boolean? = null, fullscreen: Boolean = false): StopState {
    val stop = JourneyRoute.stops[index]
    val config = JourneyConfig()
    val progress = progressAt(index, balance).copy(extras = bought.map { BoughtExtra(stop.id, it) }.toSet())
    val mainInside = stop.views.firstOrNull()?.inside ?: false
    val secondView = JourneyExtra.SECOND_VIEW in bought
    return StopState(
        loading = false,
        stop = stop,
        index = index,
        totalStops = JourneyReducer.totalStops,
        arrivedAtEpochMs = dayMs(index),
        balance = balance,
        offers = JourneyRules.offers(stop, progress).map { extra ->
            val owned = extra in bought
            ExtraOffer(extra, JourneyRules.priceOf(extra, config), owned, affordable = !owned && JourneyRules.canBuy(stop, extra, progress, config))
        },
        day = day && JourneyExtra.SECOND_TIME in bought,
        inside = if (secondView) inside ?: mainInside else mainInside,
        dayUnlocked = JourneyExtra.SECOND_TIME in bought,
        secondViewUnlocked = secondView,
        fullscreen = fullscreen,
    )
}

private val BothViews = setOf(JourneyExtra.SECOND_TIME, JourneyExtra.SECOND_VIEW)

@Composable
private fun Stop(state: StopState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true, LocalHomeLook provides Home) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                StopScreen(state, onIntent = {})
            }
        }
    }
}

@Composable
private fun RouteMap(state: JourneyState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true, LocalHomeLook provides Home) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                MapScreen(state, onIntent = {})
            }
        }
    }
}

@Preview(name = "Остановка · ничего не куплено: цены в капсулах, плашек на открытке нет, «Играть здесь · Live · Золотой зал» и «Домой»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StopPreview() = Stop(stopAt(VIENNA, 47_884))

@Preview(name = "Остановка · куплены оба вида: плашки «вечер | день» и «снаружи | внутри» в ряд на открытке, «✓ открыто»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StopBoughtPreview() = Stop(stopAt(VIENNA, 47_884, BothViews, day = true))

@Preview(name = "Остановка · de 360: две плашки не встают в ряд рядом со значком — вторая над первой", locale = "de", device = "spec:width=360dp,height=640dp")
@Composable
private fun StopTwoPlatesGermanPreview() = Stop(stopAt(VIENNA, 47_884, BothViews))

@Preview(name = "Остановка · не хватает на второй вид: «400» словом серым, без капсулы", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StopShortPreview() = Stop(stopAt(VIENNA, 300))

@Preview(name = "Остановка · Кремона: «Второй вид · вид снаружи», «Live · мастерская»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StopCremonaPreview() = Stop(stopAt(CREMONA, 47_884))

@Preview(name = "Остановка · landscape 892 × 412: открытка 180 и строка «Играть здесь» · «Домой» слева, слова и дополнения справа", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun StopLandscapePreview() = Stop(stopAt(VIENNA, 47_884, BothViews))

@Preview(name = "Остановка · landscape 603 × 308 (эмулятор за вырезом): кнопки 48, открытка над затуханием зоны", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun StopLandscape603Preview() = Stop(stopAt(VIENNA, 47_884, BothViews))

@Preview(name = "Остановка · it, landscape 603 × 308: «✓ sbloccato» под словами строки — «momento» рядом с ним рвался", locale = "it", device = "spec:width=603dp,height=308dp")
@Composable
private fun StopLandscape603ItalianPreview() = Stop(stopAt(VIENNA, 47_884, setOf(JourneyExtra.SECOND_TIME)))

@Preview(name = "Остановка · ru 360: две плашки — вторая над первой (ряд 298 при месте 256 рядом со значком)", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun StopTwoPlatesRussianPreview() = Stop(stopAt(VIENNA, 47_884, BothViews))

@Preview(name = "Остановка · 360 × 640, шрифт 1,3: строка места переносится, «Live · Золотой зал» в одну строку", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun StopSmallLargePreview() = Stop(stopAt(VIENNA, 300, setOf(JourneyExtra.SECOND_TIME)))

@Preview(name = "Остановка · полный экран: плашки внизу на стекле", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StopFullscreenPreview() = Stop(stopAt(VIENNA, 47_884, BothViews, fullscreen = true))

@Preview(name = "Карта · Вена: карточка «вы здесь · до Праги хватает» под схемой", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun MapPreview() = RouteMap(Enough)

@Preview(name = "Карта · не хватает: «вы здесь · до Праги 1 128»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun MapShortPreview() = RouteMap(Short)

@Preview(name = "Карта · дома: «Дом», «вы здесь · до Кремоны хватает»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun MapHomePreview() = RouteMap(AtHome)

@Preview(name = "Карта · маршрут пройден: «Сидней», «вы здесь · маршрут пройден»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun MapTourDonePreview() = RouteMap(TourDone)

@Preview(name = "Карта · 360 × 640: Сидней и Буэнос-Айрес над карточкой", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun MapSmallPreview() = RouteMap(Enough)

@Preview(name = "Карта · landscape 892 × 412: карточка 360 слева снизу, схема правее", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun MapLandscapePreview() = RouteMap(Enough)

@Preview(name = "Паспорт · три в ряд по 96, у Праги — «следующая»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PassportPreview() {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PassportScreen(Enough, onIntent = {})
            }
        }
    }
}

@Preview(name = "Паспорт · 360 × 640, сувенир у Кремоны: наклейка выходит за ячейку и не срезана; «Санкт-Петербург» — 11 sp целиком", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun PassportSouvenirPreview() {
    val souvenir = JourneyReducer.stateOf(
        progressAt(VIENNA, 47_884).copy(extras = setOf(BoughtExtra(JourneyRoute.stops[CREMONA].id, JourneyExtra.SOUVENIR))),
        null,
        JourneyConfig(),
    )
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PassportScreen(souvenir, onIntent = {})
            }
        }
    }
}

@Preview(name = "Паспорт · 360 × 640, шрифт 1,3: «Санкт-Петербург» мельче до 10 sp, одной строкой, дальше — многоточие", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun PassportSmallLargePreview() {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PassportScreen(Enough, onIntent = {})
            }
        }
    }
}
