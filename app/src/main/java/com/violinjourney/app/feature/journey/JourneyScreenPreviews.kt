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
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
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
