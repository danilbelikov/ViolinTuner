package com.example.violintuner.core.ui.format

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Numbers and dates speak the language of the interface (spec 3.26). Every other test of the formats is in Russian — the default on the JVM. */
class FormatLanguageTest {
    private val date = LocalDate.of(2026, 9, 20)

    @After
    fun backToRussian() = Formats.use(FormatLanguage.RUSSIAN)

    private fun use(tag: String) = Formats.use(FormatLanguage.ALL.first { it.tag == tag })

    @Test
    fun `a device that speaks none of the app's languages gets English, as the resources do`() {
        assertEquals("en", FormatLanguage.of(Locale.forLanguageTag("sv-SE")).tag)
        assertEquals("de", FormatLanguage.of(Locale.forLanguageTag("de-AT")).tag)
        assertEquals("zh", FormatLanguage.of(Locale.forLanguageTag("zh-Hans-CN")).tag)
        assertEquals("pt", FormatLanguage.of(Locale.forLanguageTag("pt-BR")).tag)
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
        assertEquals("September 20, 2025", Formats.recordDate(date.minusYears(1), withYear = true))
        assertEquals("September 2026", Formats.monthAndYear(YearMonth.of(2026, 9)))
        use("de"); assertEquals("20. September", Formats.dayAndMonth(date))
        use("fr"); assertEquals("20 septembre", Formats.dayAndMonth(date))
        use("es"); assertEquals("20 de septiembre", Formats.dayAndMonth(date))
        use("ko"); assertEquals("9월 20일", Formats.dayAndMonth(date))
        use("zh"); assertEquals("2026年9月", Formats.monthAndYear(YearMonth.of(2026, 9)))
        use("ja"); assertEquals("9月20日", Formats.dayAndMonth(date))
    }

    @Test
    fun `a count picks its word by the rule of the language`() {
        fun words(vararg counts: Int) = counts.map { Formats.plural(it, "one", "few", "many") }
        assertEquals(listOf("many", "one", "few", "many", "many", "one"), words(0, 1, 3, 5, 12, 21))
        use("en"); assertEquals(listOf("many", "one", "many", "many", "many"), words(0, 1, 2, 5, 21))
        use("fr"); assertEquals(listOf("one", "one", "many"), words(0, 1, 2))
        use("ko"); assertEquals(listOf("many", "many", "many"), words(1, 2, 5))
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
