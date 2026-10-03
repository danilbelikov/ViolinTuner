package com.violinjourney.app.core.ui.format

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.Test

/** Numbers and dates speak the language of the interface (spec 3.26). Every other test of the formats is in Russian — the default on the JVM. */
class FormatLanguageTest {
    private val date = LocalDate(2026, 9, 20)

    @AfterTest
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private fun use(tag: String) = Formats.use(FormatLanguage.ALL.first { it.tag == tag })

    @Test
    fun `a device that speaks none of the app's languages gets English — as the resources do`() {
        assertEquals("en", FormatLanguage.of("sv-SE").tag)
        assertEquals("de", FormatLanguage.of("de-AT").tag)
        assertEquals("zh", FormatLanguage.of("zh-Hans-CN").tag)
        assertEquals("pt", FormatLanguage.of("pt-BR").tag)
    }

    @Test
    fun `time in words takes the units of the language`() {
        val ms = (85 * 60_000L)
        assertEquals("1 ч 25 мин", Formats.minutesInWords(ms))
        use("en"); assertEquals("1 h 25 min", Formats.minutesInWords(ms))
        use("de"); assertEquals("1 Std. 25 Min.", Formats.minutesInWords(ms))
        use("ko"); assertEquals("1시간 25분", Formats.minutesInWords(ms))
        use("zh"); assertEquals("1 小时 25 分钟", Formats.minutesInWords(ms))
        use("ja"); assertEquals("2時間", Formats.minutesInWords(120 * 60_000L))
        use("en"); assertEquals("1250 h", Formats.totalTime(1250 * 3_600_000L))
    }

    @Test
    fun `dates are written the way the language writes them`() {
        use("en")
        assertEquals("September 20", Formats.dayAndMonth(date))
        assertEquals("Sunday, September 20", Formats.dayWithWeekday(date))
        assertEquals("September 20, 2025", Formats.recordDate(date.minus(1, DateTimeUnit.YEAR), withYear = true))
        assertEquals("September 2026", Formats.monthAndYear(YearMonth(2026, 9)))
        use("de"); assertEquals("20. September", Formats.dayAndMonth(date))
        use("fr"); assertEquals("20 septembre", Formats.dayAndMonth(date))
        use("es"); assertEquals("20 de septiembre", Formats.dayAndMonth(date))
        use("ko"); assertEquals("9월 20일", Formats.dayAndMonth(date))
        use("zh"); assertEquals("2026年9月", Formats.monthAndYear(YearMonth(2026, 9)))
        use("ja"); assertEquals("9月20日", Formats.dayAndMonth(date))
    }

    @Test
    fun `the month of the calendar stands alone in this year and with its year in another`() {
        val september = YearMonth(2026, 9)
        val august = YearMonth(2025, 8)
        fun title(tag: String, month: YearMonth): String {
            use(tag)
            return Formats.monthTitle(month, currentYear = 2026)
        }
        assertEquals("September", title("en", september))
        assertEquals("August 2025", title("en", august))
        assertEquals("September", title("de", september))
        assertEquals("August 2025", title("de", august))
        assertEquals("Septembre", title("fr", september))
        assertEquals("Août 2025", title("fr", august))
        assertEquals("Septiembre", title("es", september))
        assertEquals("Agosto de 2025", title("es", august))
        assertEquals("Settembre", title("it", september))
        assertEquals("Setembro", title("pt", september))
        assertEquals("9월", title("ko", september))
        assertEquals("2025년 8월", title("ko", august))
        assertEquals("9月", title("zh", september))
        assertEquals("2025年8月", title("zh", august))
        assertEquals("9月", title("ja", september))
        assertEquals("2025年8月", title("ja", august))
    }

    @Test
    fun `a count picks its word by the rule of the language`() {
        fun words(vararg counts: Int) = counts.map { Formats.plural(it, "one", "few", "many") }
        assertEquals(listOf("many", "one", "few", "many", "many", "one"), words(0, 1, 3, 5, 12, 21))
        use("en"); assertEquals(listOf("many", "one", "many", "many", "many"), words(0, 1, 2, 5, 21))
        use("fr"); assertEquals(listOf("one", "one", "many"), words(0, 1, 2))
        use("ko"); assertEquals(listOf("many", "many", "many"), words(1, 2, 5))
    }

    /** The dates of the events (spec 3.36.9, «Новые шаблоны дат») of one day in one language, as Android writes them — and iOS. */
    private data class EventDates(
        val tag: String,
        val weekdayDate: String,
        val weekdayCommaDate: String,
        val weekdayDay: String,
        val weekdayDayMonth: String,
        val shortMonth: String,
        val weekdayFullDate: String,
    )

    private fun assertEventDates(date: LocalDate, expected: List<EventDates>) {
        assertEquals(FormatLanguage.ALL.map { it.tag }.toSet(), expected.map { it.tag }.toSet(), "every language of the app")
        for (want in expected) {
            use(want.tag)
            val have = EventDates(
                want.tag, Formats.weekdayDate(date), Formats.weekdayCommaDate(date), Formats.weekdayDay(date), Formats.weekdayDayMonth(date),
                Formats.shortMonth(date), Formats.weekdayFullDate(date),
            )
            assertEquals(want, have, "${want.tag}, $date")
        }
    }

    /**
     * «пн 5 окт.» in a chip, «Пн, 28 сент.» in the row of the form, «пн 19» on the plate of a repeat, «сб 24 октября», «окт» on the tile
     * of «Выступления», «понедельник, 28 сентября» for TalkBack (plan 6.6, D46): ja, zh and ko in their own order. A Russian weekday is
     * small inside a sentence on both platforms — iOS by itself would make it «Пн» at the start.
     */
    @Test
    fun `the dates of the events are written the way each language writes them`() {
        assertEventDates(
            LocalDate(2026, 9, 28),
            listOf(
                EventDates("ru", "пн 28 сент.", "Пн, 28 сент.", "пн 28", "пн 28 сентября", "сент", "понедельник, 28 сентября"),
                EventDates("en", "Mon Sep 28", "Mon, Sep 28", "Mon 28", "Mon, September 28", "Sep", "Monday, September 28"),
                EventDates("de", "Mo. 28. Sept.", "Mo., 28. Sept.", "Mo. 28.", "Mo., 28. September", "Sep", "Montag, 28. September"),
                EventDates("fr", "lun. 28 sept.", "Lun. 28 sept.", "lun. 28", "lun. 28 septembre", "sept", "lundi 28 septembre"),
                EventDates("es", "lun 28 sept", "Lun, 28 sept", "lun 28", "lun 28 de septiembre", "sept", "lunes, 28 de septiembre"),
                EventDates("it", "lun 28 set", "Lun 28 set", "lun 28", "lun 28 settembre", "set", "lunedì 28 settembre"),
                EventDates("pt", "seg., 28 de set.", "Seg., 28 de set.", "seg., 28", "seg., 28 de setembro", "set", "segunda-feira, 28 de setembro"),
                EventDates("ko", "9월 28일 (월)", "9월 28일 (월)", "28일 (월)", "9월 28일 (월)", "9월", "9월 28일 월요일"),
                EventDates("zh", "9月28日 周一", "9月28日 周一", "28日 周一", "9月28日 周一", "9月", "9月28日 星期一"),
                EventDates("ja", "9月28日(月)", "9月28日(月)", "28日(月)", "9月28日(月)", "9月", "9月28日 月曜日"),
            ),
        )
        assertEventDates(
            LocalDate(2026, 10, 24),
            listOf(
                EventDates("ru", "сб 24 окт.", "Сб, 24 окт.", "сб 24", "сб 24 октября", "окт", "суббота, 24 октября"),
                EventDates("en", "Sat Oct 24", "Sat, Oct 24", "Sat 24", "Sat, October 24", "Oct", "Saturday, October 24"),
                EventDates("de", "Sa. 24. Okt.", "Sa., 24. Okt.", "Sa. 24.", "Sa., 24. Oktober", "Okt", "Samstag, 24. Oktober"),
                EventDates("fr", "sam. 24 oct.", "Sam. 24 oct.", "sam. 24", "sam. 24 octobre", "oct", "samedi 24 octobre"),
                EventDates("es", "sáb 24 oct", "Sáb, 24 oct", "sáb 24", "sáb 24 de octubre", "oct", "sábado, 24 de octubre"),
                EventDates("it", "sab 24 ott", "Sab 24 ott", "sab 24", "sab 24 ottobre", "ott", "sabato 24 ottobre"),
                EventDates("pt", "sáb., 24 de out.", "Sáb., 24 de out.", "sáb., 24", "sáb., 24 de outubro", "out", "sábado, 24 de outubro"),
                EventDates("ko", "10월 24일 (토)", "10월 24일 (토)", "24일 (토)", "10월 24일 (토)", "10월", "10월 24일 토요일"),
                EventDates("zh", "10月24日 周六", "10月24日 周六", "24日 周六", "10月24日 周六", "10月", "10月24日 星期六"),
                EventDates("ja", "10月24日(土)", "10月24日(土)", "24日(土)", "10月24日(土)", "10月", "10月24日 土曜日"),
            ),
        )
    }

    /**
     * The tile of a date of «Выступления» (review of stage 99): under its day the Russian month is the form of a date — «18 / мая», as the
     * mockups write the exam, not «18 / май» — which differs from the standing form in March, May, June and July; the dot of a short form
     * is dropped. The other languages keep theirs (de «Mär», not «März»).
     */
    @Test
    fun `the month on the tile of a date is the form of a date in Russian`() {
        val days = listOf(3, 5, 6, 7, 9).map { LocalDate(2026, it, 18) }
        assertEquals(listOf("мар", "мая", "июн", "июл", "сент"), days.map(Formats::shortMonth))
    }

    /**
     * A date in words that wrap (5.29 R9, review of stage 99: «число и слово при этом не разрываются»): its spaces are no-break ones — the
     * words break before it, never between its day, its month and its year; a language without spaces in its dates keeps them as they are.
     */
    @Test
    fun `a date in words that wrap never breaks inside`() {
        val october = LocalDate(2026, 10, 24)
        val december = LocalDate(2025, 12, 27)
        assertEquals("24 октября", Formats.recordDateWhole(october, withYear = false))
        assertEquals("27 декабря 2025", Formats.recordDateWhole(december, withYear = true))
        use("de"); assertEquals("24. Oktober", Formats.recordDateWhole(october, withYear = false))
        use("en"); assertEquals("December 27, 2025", Formats.recordDateWhole(december, withYear = true))
        use("ja"); assertEquals("10月24日", Formats.recordDateWhole(october, withYear = false))
    }

    /**
     * The dates of the answers of the sheet of a repeat (plan D31): of one month its name once — after the last day, or before the first
     * in English, Korean, Chinese and Japanese — and of two months each date whole; «、» between them in Chinese and Japanese.
     */
    @Test
    fun `a list of dates names a month once and two months each`() {
        val oneMonth = listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26))
        val twoMonths = listOf(LocalDate(2026, 10, 26), LocalDate(2026, 11, 2))
        val expected = mapOf(
            "ru" to listOf("19, 26 окт.", "26 окт., 2 нояб.", "19 окт."),
            "en" to listOf("Oct 19, 26", "Oct 26, Nov 2", "Oct 19"),
            "de" to listOf("19., 26. Okt.", "26. Okt., 2. Nov.", "19. Okt."),
            "fr" to listOf("19, 26 oct.", "26 oct., 2 nov.", "19 oct."),
            "es" to listOf("19, 26 oct", "26 oct, 2 nov", "19 oct"),
            "it" to listOf("19, 26 ott", "26 ott, 2 nov", "19 ott"),
            "pt" to listOf("19, 26 out.", "26 out., 2 nov.", "19 out."),
            "ko" to listOf("10월 19일, 26일", "10월 26일, 11월 2일", "10월 19일"),
            "zh" to listOf("10月19日、26日", "10月26日、11月2日", "10月19日"),
            "ja" to listOf("10月19日、26日", "10月26日、11月2日", "10月19日"),
        )
        assertEquals(FormatLanguage.ALL.map { it.tag }.toSet(), expected.keys, "every language of the app")
        for ((tag, lists) in expected) {
            use(tag)
            assertEquals(lists, listOf(Formats.dateList(oneMonth), Formats.dateList(twoMonths), Formats.dateList(oneMonth.take(1))), tag)
        }
        assertEquals("", Formats.dateList(emptyList()))
    }

    @Test
    fun `decimals and file sizes follow the language`() {
        assertEquals("7,3", Formats.oneDecimal(7.3))
        assertEquals("1,5 ГБ", Formats.fileSize((1.5 * 1024 * 1024 * 1024).toLong()))
        use("en")
        assertEquals("7.3", Formats.oneDecimal(7.3))
        assertEquals("1.5 GB", Formats.fileSize((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("under 1 MB", Formats.fileSize(10))
        use("fr"); assertEquals("214 Mo", Formats.fileSize(214L * 1024 * 1024))
    }
}
