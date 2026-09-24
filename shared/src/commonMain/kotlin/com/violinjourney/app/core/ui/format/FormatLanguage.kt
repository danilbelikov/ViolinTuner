package com.violinjourney.app.core.ui.format

/** How a count picks its word: Russian has three forms, most languages of the app two, and some one. */
enum class PluralRule { SLAVIC, ONE_OTHER, ONE_UP_TO_TWO, NONE }

/**
 * What [Formats] needs to speak a language of the interface (words themselves live in
 * `strings.xml`): the language of numbers and month names ([tag]), the short units that are written next to
 * numbers, the patterns of dates (Unicode patterns: the platform writes the month and weekday names in them),
 * the rule of plurals. Pure data — formats are tested on both platforms.
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
    /** Where the whole part ends: 7,3 in most languages of the app, 7.3 in English, Korean, Chinese and Japanese. */
    val decimalSeparator: Char = if (tag in POINT_LANGUAGES) '.' else ','

    data class SoundUnits(val ms: String, val s: String, val hz: String, val khz: String, val db: String, val kilo: String)

    companion object {
        private val POINT_LANGUAGES = setOf("en", "ko", "zh", "ja")
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

        /**
         * The language of the interface for a device set to [languageTag] ("de-AT", "zh-Hans-CN"): its own if the app
         * speaks it, English otherwise — as the resources fall back.
         */
        fun of(languageTag: String): FormatLanguage {
            val language = languageTag.substringBefore('-').substringBefore('_').lowercase()
            return ALL.firstOrNull { it.tag == language } ?: ENGLISH
        }
    }
}
