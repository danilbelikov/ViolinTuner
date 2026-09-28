package com.violinjourney.app.feature.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.WindowFit

/**
 * The window of the home on «Занятия» (spec 3.36.2; practice.html, practice-extra.html): the data of the mockups — the road at
 * Vienna, Prague next for 1 600, 47 884 takts in the purse; short of it — 472, 1 128 missing.
 */
internal object WindowSample {
    private val vienna = JourneyRoute.stops.first { it.id == "vienna" }
    private val prague = JourneyRoute.stops.first { it.id == "prague" }

    /** «Хватает до Праги — в путь», at home. */
    val enough = JourneyWindow(current = vienna, next = prague, balance = 47_884, missing = 0, canDepart = true, enough = true)

    /** «до Праги 1 128», the bar to the price. */
    val short = JourneyWindow(current = vienna, next = prague, balance = 472, missing = 1_128, canDepart = false, enough = false)

    /** The same, in Vienna: its postcard and «в пути · Вена · остановка …». */
    val inCity = short.copy(here = Venue.Hall(vienna.id))

    /** The end of what is drawn: «Скоро новые города». */
    val routeDone = JourneyWindow(current = JourneyRoute.stops.last(), next = null, balance = 2_516, missing = 0, canDepart = false, enough = false)

    /** Takts of the practice just saved: «+340» for its seconds. */
    val earned = short.copy(balance = 812, missing = 788, justEarned = 340)

    /** Not a takt earned yet: «Ваша комната». */
    val firstRun = JourneyReducer.windowOf(JourneyProgress.EMPTY)

    /** At home before the first city with the purse of the mockups: «Хватает до Кремоны — в путь», a long call. */
    val enoughForCremona = JourneyReducer.windowOf(
        JourneyProgress(earned = 47_884, spent = 0, arrivals = listOf(Arrival(JourneyRoute.HOME, 1)), extras = emptySet()),
    )
}

@Composable
private fun Windows(content: @Composable () -> Unit) = ViolinTheme {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

private val Full = WindowLook.Picture(WindowFit.PictureMax)

@Preview(name = "Окно дома · не хватает: полоса до цены", widthDp = 412, locale = "ru")
@Composable
private fun ShortPreview() = Windows { JourneyWindowCard(WindowSample.short, Full, onClick = {}) }

@Preview(name = "Окно дома · хватает: акцент, без полосы", widthDp = 412, locale = "ru")
@Composable
private fun EnoughPreview() = Windows { JourneyWindowCard(WindowSample.enough, Full, onClick = {}) }

@Preview(name = "Окно дома · 360: «в путь» переносится после тире", widthDp = 360, locale = "ru")
@Composable
private fun EnoughNarrowPreview() = Windows { JourneyWindowCard(WindowSample.enough, WindowLook.Picture(80.dp), onClick = {}) }

@Preview(name = "Окно дома · маршрут пройден", widthDp = 412, locale = "ru")
@Composable
private fun RouteDonePreview() = Windows { JourneyWindowCard(WindowSample.routeDone, Full, onClick = {}) }

@Preview(name = "Окно дома · в городе: открытка и «в пути …»", widthDp = 412, locale = "ru")
@Composable
private fun InCityPreview() = Windows { JourneyWindowCard(WindowSample.inCity, Full, onClick = {}) }

@Preview(name = "Окно дома · 360: пометка города с многоточием", widthDp = 360, locale = "de")
@Composable
private fun InCityGermanPreview() = Windows { JourneyWindowCard(WindowSample.inCity, WindowLook.Picture(80.dp), onClick = {}) }

@Preview(name = "Окно дома · «+340» поверх конца пометки", widthDp = 412, locale = "ru")
@Composable
private fun EarnedPreview() = Windows {
    JourneyWindowCard(WindowSample.earned, Full, onClick = {})
    JourneyWindowCard(WindowSample.earned.copy(here = WindowSample.inCity.here), Full, onClick = {})
}

@Preview(name = "Окно дома · первый запуск: «Ваша комната» под стеклом", widthDp = 412, locale = "ru")
@Composable
private fun FirstRunPreview() = Windows { JourneyWindowCard(WindowSample.firstRun, Full, onClick = {}) }

@Preview(name = "Окно дома · строка 56 с миниатюрой: дом, город, «+340», первый запуск", widthDp = 360, locale = "ru")
@Composable
private fun LinePreview() = Windows {
    JourneyWindowCard(WindowSample.enough, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.inCity, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.earned, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.routeDone, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.firstRun, WindowLook.Line, onClick = {})
}

@Preview(name = "Окно дома · строка, fr, шрифт 1,3", widthDp = 360, locale = "fr", fontScale = 1.3f)
@Composable
private fun LineFrenchPreview() = Windows {
    JourneyWindowCard(WindowSample.enough, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.short, WindowLook.Line, onClick = {})
}

@Preview(name = "Окно дома · 360, шрифт 1,3: «Хватает до Кремоны — в путь» целиком, строка растёт", widthDp = 360, locale = "ru", fontScale = 1.3f)
@Composable
private fun LongCallLargePreview() = Windows {
    JourneyWindowCard(WindowSample.enoughForCremona, WindowLook.Line, onClick = {})
    JourneyWindowCard(WindowSample.enoughForCremona, WindowLook.Picture(80.dp), onClick = {})
}

@Preview(name = "Окно дома · landscape: картинка 176 слева, таблетка у правого края (колонка 596)", widthDp = 628, locale = "ru")
@Composable
private fun BesidePreview() = Windows {
    JourneyWindowCard(WindowSample.enough, WindowLook.Beside(WindowFit.BesideWide), onClick = {})
    JourneyWindowCard(WindowSample.short, WindowLook.Beside(WindowFit.BesideWide), onClick = {})
    JourneyWindowCard(WindowSample.firstRun, WindowLook.Beside(WindowFit.BesideWide), onClick = {})
}

@Preview(name = "Окно дома · 640 × 360: картинка 120, таблетка под призывом (колонка 344)", widthDp = 376, locale = "ru")
@Composable
private fun BesideNarrowPreview() = Windows {
    JourneyWindowCard(WindowSample.enough, WindowLook.Beside(WindowFit.BesideNarrow), onClick = {})
    JourneyWindowCard(WindowSample.inCity, WindowLook.Beside(WindowFit.BesideNarrow), onClick = {})
}
