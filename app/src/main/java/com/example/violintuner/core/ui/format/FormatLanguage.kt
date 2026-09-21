package com.example.violintuner.core.ui.format

import java.util.Locale

/** How a count picks its word: Russian has three forms, most languages of the app two, and some one. */
enum class PluralRule { SLAVIC, ONE_OTHER, ONE_UP_TO_TWO, NONE }

/**
 * What [Formats] needs to speak a language of the interface (words themselves live in
 * `strings.xml`): the locale of numbers and month names, the short units that are written next to
 * numbers, the patterns of dates, the rule of plurals. Pure data — formats are tested on the JVM.
 */
data class FormatLanguage(
    val tag: String,
    val hour: String,
    val minute: String,
    val megabyte: String,
    val gigabyte: String,
    val underMegabyte: String,
    /** Units of the sound screen: milliseconds, seconds, hertz, kilohertz, decibels, «k» of the axis. */
    val sound: SoundUnits,
    val dayMonth: String,
    val dayShortMonth: String,
    val dayMonthWeekday: String,
    val monthYear: String,
    val dayMonthYear: String,
    val dayMonthYearWeekday: String,
    val plural: PluralRule,
    /** «1 ч 25 мин» has a space between the number and the unit; «1시간 25분» has none. */
    val unitSpace: String = " ",
) {
    val locale: Locale = Locale.forLanguageTag(tag)

    data class SoundUnits(val ms: String, val s: String, val hz: String, val khz: String, val db: String, val kilo: String)

    companion object {
        private val LATIN = SoundUnits("ms", "s", "Hz", "kHz", "dB", "k")

        val RUSSIAN = FormatLanguage(
            "ru", "ч", "мин", "МБ", "ГБ", "меньше 1 МБ", SoundUnits("мс", "с", "Гц", "кГц", "дБ", "к"),
            "d MMMM", "d MMM", "d MMMM, EEEE", "LLLL yyyy", "d MMMM yyyy", "d MMMM yyyy, EEEE", PluralRule.SLAVIC,
        )
        val ENGLISH = FormatLanguage(
            "en", "h", "min", "MB", "GB", "under 1 MB", LATIN,
            "MMMM d", "MMM d", "EEEE, MMMM d", "LLLL yyyy", "MMMM d, yyyy", "EEEE, MMMM d, yyyy", PluralRule.ONE_OTHER,
        )

        /** Every language the interface is translated into; the first is the one for a device that speaks none of them. */
        val ALL: List<FormatLanguage> = listOf(
            ENGLISH,
            RUSSIAN,
            FormatLanguage("de", "Std.", "Min.", "MB", "GB", "unter 1 MB", LATIN, "d. MMMM", "d. MMM", "EEEE, d. MMMM", "LLLL yyyy", "d. MMMM yyyy", "EEEE, d. MMMM yyyy", PluralRule.ONE_OTHER),
            FormatLanguage("fr", "h", "min", "Mo", "Go", "moins de 1 Mo", LATIN, "d MMMM", "d MMM", "EEEE d MMMM", "LLLL yyyy", "d MMMM yyyy", "EEEE d MMMM yyyy", PluralRule.ONE_UP_TO_TWO),
            FormatLanguage("es", "h", "min", "MB", "GB", "menos de 1 MB", LATIN, "d 'de' MMMM", "d MMM", "EEEE, d 'de' MMMM", "LLLL 'de' yyyy", "d 'de' MMMM 'de' yyyy", "EEEE, d 'de' MMMM 'de' yyyy", PluralRule.ONE_OTHER),
            FormatLanguage("it", "h", "min", "MB", "GB", "meno di 1 MB", LATIN, "d MMMM", "d MMM", "EEEE d MMMM", "LLLL yyyy", "d MMMM yyyy", "EEEE d MMMM yyyy", PluralRule.ONE_OTHER),
            FormatLanguage("pt", "h", "min", "MB", "GB", "menos de 1 MB", LATIN, "d 'de' MMMM", "d MMM", "EEEE, d 'de' MMMM", "LLLL 'de' yyyy", "d 'de' MMMM 'de' yyyy", "EEEE, d 'de' MMMM 'de' yyyy", PluralRule.ONE_OTHER),
            FormatLanguage("ko", "시간", "분", "MB", "GB", "1MB 미만", LATIN, "M월 d일", "M월 d일", "M월 d일 EEEE", "yyyy년 M월", "yyyy년 M월 d일", "yyyy년 M월 d일 EEEE", PluralRule.NONE, unitSpace = ""),
            FormatLanguage("zh", "小时", "分钟", "MB", "GB", "不到 1 MB", LATIN, "M月d日", "M月d日", "M月d日 EEEE", "yyyy年M月", "yyyy年M月d日", "yyyy年M月d日 EEEE", PluralRule.NONE, unitSpace = " "),
            FormatLanguage("ja", "時間", "分", "MB", "GB", "1 MB未満", LATIN, "M月d日", "M月d日", "M月d日 EEEE", "yyyy年M月", "yyyy年M月d日", "yyyy年M月d日 EEEE", PluralRule.NONE, unitSpace = ""),
        )

        /** The language of the interface for a device set to [locale]: its own if the app speaks it, English otherwise — as the resources fall back. */
        fun of(locale: Locale): FormatLanguage = ALL.firstOrNull { it.locale.language == locale.language } ?: ENGLISH
    }
}
