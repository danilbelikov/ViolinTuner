package com.violinjourney.app.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.standInPhoto
import com.violinjourney.app.feature.practice.components.GiftButtons
import com.violinjourney.app.feature.practice.components.GiftSheetContent
import com.violinjourney.app.feature.practice.components.NamePhotoButtons
import com.violinjourney.app.feature.practice.components.NamePhotoSheetContent
import com.violinjourney.app.feature.practice.components.RecapButtons
import com.violinjourney.app.feature.practice.components.RecapSheetContent
import com.violinjourney.app.feature.practice.components.TrophiesSheetContent
import kotlinx.datetime.LocalDate

/**
 * The sheets of progress of R3 (spec 3.36.3; practice-sheets.html 2, 5, 6, 7) on the data of the mockups: Sunday 27 September 2026;
 * 47 h 17 min at the violin before the practice — level 5, «Гаммы»; the trophies of 1 h (14 June) and 10 h (2 July); the road from
 * Vienna to Prague. Each sheet lies under the scrim at the bottom of a screen of the preview's size ([OnScreen]), its buttons at the
 * bottom of the sheet.
 */
private object ProgressSample {
    const val HOUR = 60 * MS_PER_MINUTE
    val config = ProgressConfig()
    val today = LocalDate(2026, 9, 27)

    /** 47 h 17 min: the whole time of the mockups. */
    const val TOTAL = 47 * HOUR + 17 * MS_PER_MINUTE
    private val rosin = Trophy(1, LocalDate(2026, 6, 14), shown = true)
    private val peg = Trophy(10, LocalDate(2026, 7, 2), shown = true)
    val twoTrophies = listOf(rosin, peg)

    fun lines(total: Long = TOTAL, trophies: List<Trophy> = twoTrophies): List<TrophyLine> = ProgressReducer.trophyLines(total, trophies, config)

    /** Every trophy up to [hours], the last of them not seen yet: its gift is due. */
    fun gift(hours: Int, total: Long = hours * HOUR): Gift {
        val given = config.trophyHours.filter { it <= hours }.map { Trophy(it, today, shown = it != hours) }
        return requireNotNull(ProgressReducer.giftOf(given, config, total))
    }

    /** An empty day at zero hours set to 12 h: the trophies of 1 h and 10 h at once — the first of the chain. */
    val chain: Gift = requireNotNull(ProgressReducer.giftOf(listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)), config, 12 * HOUR))
}

/** The recaps of the mockup: Vienna → Prague, 47 min, 212 notes in tune, two elements, a streak of 8 grown today. */
private object RecapSample {
    private val progress = ProgressConfig()
    private const val HOUR = ProgressSample.HOUR

    /** Prague, the sixth city of the road: «До Праги — ещё 1 128». */
    val toPrague = RecapRoad.Leg(nextIndex = 5, price = 3_000, balanceBefore = 1_647, balanceAfter = 1_872)

    fun recap(
        inTune: Int = 212,
        minutes: Long = 47,
        pieces: Int = 2,
        road: RecapRoad = toPrague,
        totalBefore: Long = ProgressSample.TOTAL,
        streak: Int = 8,
        extended: Boolean = true,
        dayTotal: Long? = 92 * MS_PER_MINUTE,
    ): PracticeRecap {
        val duration = minutes * MS_PER_MINUTE
        val sources = JourneyRules.taktsBySource(inTune, duration, JourneyConfig(), pieces)
        return PracticeRecap(
            durationMs = duration, dayTotalMs = dayTotal, takts = sources.total, sources = sources, road = road,
            streakDays = streak, streakExtended = extended,
            levelBefore = Progress.levelOf(totalBefore, progress), levelAfter = Progress.levelOf(totalBefore + duration, progress),
        )
    }

    /** 1 h 5 min, 380 notes, one element: +287, enough for Prague. */
    val enough = recap(inTune = 380, minutes = 65, pieces = 1, road = RecapRoad.Leg(5, 3_000, 2_800, 3_087), extended = false, dayTotal = null)

    /** 2 h 50 min over 47 h 17 min: level 6, «Этюды», «до 7 уровня — 49 ч 53 мин». */
    val newLevel = recap(inTune = 810, minutes = 170, pieces = 0, dayTotal = null)
}

@Composable
private fun RecapPreview(recap: PracticeRecap, low: Boolean = false) = OnScreen { modifier ->
    if (low) {
        // «Готово» at the bottom of the right column, the columns scrolling over it
        AppSheetCard(modifier) { RecapSheetContent(recap, onTravel = {}, onDone = {}, low = true, animated = false) }
    } else {
        AppSheetCard(modifier, bottom = { RecapButtons(onDone = {}) }) {
            RecapSheetContent(recap, onTravel = {}, onDone = {}, low = false, animated = false)
        }
    }
}

@Preview(name = "Итог · обычный: 47 мин, +225, до Праги ещё 1 128, 8 дней и уровень 5", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapUsualPreview() = RecapPreview(RecapSample.recap())

@Preview(name = "Итог · хватает до Праги: карточка акцентом, «В дорогу →»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapEnoughPreview() = RecapPreview(RecapSample.enough)

@Preview(name = "Итог · новый уровень: «6 · Этюды» в рамке, серия строкой", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapNewLevelPreview() = RecapPreview(RecapSample.newLevel)

@Preview(name = "Итог · серия 0: уровень во всю ширину", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapNoStreakPreview() = RecapPreview(RecapSample.recap(streak = 0, extended = false))

@Preview(name = "Итог · новый уровень при серии 0: без строки серии", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapNewLevelNoStreakPreview() = RecapPreview(RecapSample.recap(inTune = 810, minutes = 170, pieces = 0, streak = 0, extended = false, dayTotal = null))

@Preview(name = "Итог · маршрут пройден", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapRouteDonePreview() = RecapPreview(RecapSample.recap(road = RecapRoad.RouteDone(2_340), totalBefore = 530 * ProgressSample.HOUR, streak = 31))

@Preview(name = "Итог · путешествие не начато, первое занятие", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapNotStartedPreview() = RecapPreview(
    RecapSample.recap(inTune = 0, minutes = 12, pieces = 0, road = RecapRoad.NotStarted, totalBefore = 0, streak = 1, dayTotal = null),
)

@Preview(name = "Итог · без нот (Live не открывали), без подходов", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapNoNotesPreview() = RecapPreview(RecapSample.recap(inTune = 0, pieces = 0))

@Preview(name = "Итог · единственное занятие дня: без «всего за день»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecapOnlyPracticePreview() = RecapPreview(RecapSample.recap(dayTotal = null))

@Preview(name = "Итог · низкое окно 892 × 412: два столбца, «Готово» у низа правого", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun RecapLowPreview() = RecapPreview(RecapSample.recap(), low = true)

@Preview(name = "Итог · низкое окно 892 × 412, новый уровень", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun RecapLowNewLevelPreview() = RecapPreview(RecapSample.newLevel, low = true)

@Preview(name = "Итог · низкое окно 640 × 360: кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun RecapTinyPreview() = RecapPreview(RecapSample.enough, low = true)

@Preview(name = "Итог · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun RecapGermanPreview() = RecapPreview(RecapSample.recap())

@Composable
private fun GiftPreview(gift: Gift, low: Boolean = false) = OnScreen { modifier ->
    AppSheetCard(modifier, bottom = { GiftButtons(onThanks = {}) }) { GiftSheetContent(gift, low = low, animated = false) }
}

@Preview(name = "Подарок · «Струна», «Дальше — Смычок · 100 ч · ещё 50 ч»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun GiftStringPreview() = GiftPreview(ProgressSample.gift(50))

@Preview(name = "Подарок · цепочка: «Канифоль», дальше «Струна» — ещё 38 ч", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun GiftChainPreview() = GiftPreview(ProgressSample.chain)

@Preview(name = "Подарок · 10 000 ч: без карточки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun GiftLastPreview() = GiftPreview(ProgressSample.gift(10_000))

@Preview(name = "Подарок · низкое окно 892 × 412: трофей 80, без карточки", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun GiftLowPreview() = GiftPreview(ProgressSample.gift(50), low = true)

@Preview(name = "Подарок · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GiftGermanPreview() = GiftPreview(ProgressSample.gift(50))

@Composable
private fun TrophiesPreview(lines: List<TrophyLine>, total: Long) = OnScreen { modifier ->
    AppSheetCard(modifier) { TrophiesSheetContent(lines, total) }
}

@Preview(name = "Трофеи · 2 из 10 при 47 ч 17 мин: Струна в рамке, ещё 2 ч 43 мин", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TrophiesTwoPreview() = TrophiesPreview(ProgressSample.lines(), ProgressSample.TOTAL)

@Preview(name = "Трофеи · 0 из 10: «Канифоль» в рамке", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TrophiesNonePreview() = TrophiesPreview(ProgressSample.lines(total = 20 * MS_PER_MINUTE, trophies = emptyList()), 20 * MS_PER_MINUTE)

@Preview(name = "Трофеи · 10 из 10: ни рамки, ни «Далеко впереди»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TrophiesAllPreview() {
    val all = ProgressSample.config.trophyHours.map { Trophy(it, ProgressSample.today, shown = true) }
    TrophiesPreview(ProgressSample.lines(total = 10_000 * ProgressSample.HOUR, trophies = all), 10_000 * ProgressSample.HOUR)
}

@Preview(name = "Трофеи · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun TrophiesGermanPreview() = TrophiesPreview(ProgressSample.lines(), ProgressSample.TOTAL)

@Preview(name = "Трофеи · landscape 892 × 412: не шире 640, прокрутка", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun TrophiesLandscapePreview() = TrophiesPreview(ProgressSample.lines(), ProgressSample.TOTAL)

@Composable
private fun NamePhotoPreview(name: String = "", withPhoto: Boolean = false, importing: Boolean = false) = OnScreen { modifier ->
    val photo = if (withPhoto) remember { standInPhoto() } else null
    AppSheetCard(modifier, bottom = { NamePhotoButtons(onIntent = {}) }) {
        NamePhotoSheetContent(
            sheet = PracticeSheet.Profile(nameDraft = name, importingPhoto = importing),
            hasPhoto = withPhoto,
            photo = photo,
            onIntent = {},
            onPickPhoto = {},
        )
    }
}

@Preview(name = "Имя и фото · ни фото, ни имени: «?», «Выбрать фото»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NamePhotoEmptyPreview() = NamePhotoPreview()

@Preview(name = "Имя и фото · имя без фото: «А»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NamePhotoLetterPreview() = NamePhotoPreview(name = "Аня")

@Preview(name = "Имя и фото · с фото: «Другое фото» и «Убрать фото»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NamePhotoPhotoPreview() = NamePhotoPreview(name = "Аня", withPhoto = true)

@Preview(name = "Имя и фото · фото копируется: обе кнопки приглушены", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NamePhotoImportingPreview() = NamePhotoPreview(name = "Аня", withPhoto = true, importing = true)

@Preview(name = "Имя и фото · fr, 360, шрифт 1,3", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun NamePhotoFrenchPreview() = NamePhotoPreview(name = "Anne", withPhoto = true)

@Preview(name = "Имя и фото · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun NamePhotoGermanPreview() = NamePhotoPreview(name = "Anna", withPhoto = true)

@Preview(name = "Имя и фото · landscape 892 × 412: без автофокуса, прокрутка над «Готово»", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun NamePhotoLandscapePreview() = NamePhotoPreview(name = "Аня")
