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
    /** The month alone, for the calendar of this year («Сентябрь», spec 3.36.2): its name standing by itself, or «9月». */
    val month: String = "LLLL",
    // The dates of the events (spec 3.36.9, «Новые шаблоны дат»): ja / zh / ko keep their own order.
    /** The short weekday, the day and the short month: «пн 5 окт.» — the chip of the sheet «Дата», «вт 20 окт., 18:00» in a question. */
    val weekdayDate: String,
    /** The same with a comma, the first letter capital: «Пн, 28 сент.» — the row of the date in the form. */
    val weekdayCommaDate: String,
    /** The short weekday and the day: «пн 19» — the plate «что меняется» of a repeat. */
    val weekdayDay: String,
    /** The short weekday, the day and the whole month: «сб 24 октября», «пн 28 декабря». */
    val weekdayDayMonth: String,
    /** The short month alone, without a dot: «окт» — the tile of the date of «Выступления». */
    val shortMonth: String,
    /** The whole weekday, then the day and the whole month: «понедельник, 28 сентября» — TalkBack of the form and the sheet «Дата». */
    val weekdayFullDate: String,
    // A list of dates of one month (the answers of the sheet of a repeat, plan D31): «19, 26 окт.», «Oct 19, 26», «10月19日、26日».
    /** A day of the list standing without its month: «19», «19.», «19日». */
    val listedDay: String,
    /** The month goes with the first day of the list («Oct 19, 26»), not with the last («19, 26 окт.»). */
    val monthFirstList: Boolean,
    /** Between the dates of a list: «, », or «、» in Chinese and Japanese. */
    val listSeparator: String,
) {
    /** Where the whole part ends: 7,3 in most languages of the app, 7.3 in English, Korean, Chinese and Japanese. */
    val decimalSeparator: Char = if (tag in POINT_LANGUAGES) '.' else ','

    /**
     * A weekday is written small inside a sentence — «пн 5 окт.», «lun. 28 sept.» — in Russian, French, Spanish, Italian and Portuguese;
     * German and English write it capital. iOS left to itself capitalises a Russian short weekday that begins the text, Android does not.
     */
    val smallWeekday: Boolean = tag in SMALL_WEEKDAY_LANGUAGES

    data class SoundUnits(val ms: String, val s: String, val hz: String, val khz: String, val db: String, val kilo: String)

    companion object {
        private val POINT_LANGUAGES = setOf("en", "ko", "zh", "ja")
        private val SMALL_WEEKDAY_LANGUAGES = setOf("ru", "fr", "es", "it", "pt")
        private const val COMMA = ", "
        private const val IDEOGRAPHIC_COMMA = "、"
        private val LATIN = SoundUnits("ms", "s", "Hz", "kHz", "dB", "k")

        val RUSSIAN = FormatLanguage(
            "ru", "ч", "мин", "МБ", "ГБ", "меньше 1 МБ", SoundUnits("мс", "с", "Гц", "кГц", "дБ", "к"),
            "d MMMM", "d MMM", "d MMMM, EEEE", "LLLL yyyy", "d MMMM yyyy", "d MMMM yyyy, EEEE", PluralRule.SLAVIC,
            weekdayDate = "EEE d MMM", weekdayCommaDate = "EEE, d MMM", weekdayDay = "EEE d", weekdayDayMonth = "EEE d MMMM", shortMonth = "LLL",
            weekdayFullDate = "EEEE, d MMMM", listedDay = "d", monthFirstList = false, listSeparator = COMMA,
        )
        val ENGLISH = FormatLanguage(
            "en", "h", "min", "MB", "GB", "under 1 MB", LATIN,
            "MMMM d", "MMM d", "EEEE, MMMM d", "LLLL yyyy", "MMMM d, yyyy", "EEEE, MMMM d, yyyy", PluralRule.ONE_OTHER,
            weekdayDate = "EEE MMM d", weekdayCommaDate = "EEE, MMM d", weekdayDay = "EEE d", weekdayDayMonth = "EEE, MMMM d", shortMonth = "LLL",
            weekdayFullDate = "EEEE, MMMM d", listedDay = "d", monthFirstList = true, listSeparator = COMMA,
        )

        /** Every language the interface is translated into; the first is the one for a device that speaks none of them. */
        val ALL: List<FormatLanguage> = listOf(
            ENGLISH,
            RUSSIAN,
            FormatLanguage(
                "de", "Std.", "Min.", "MB", "GB", "unter 1 MB", LATIN, "d. MMMM", "d. MMM", "EEEE, d. MMMM", "LLLL yyyy", "d. MMMM yyyy", "EEEE, d. MMMM yyyy", PluralRule.ONE_OTHER,
                weekdayDate = "EEE d. MMM", weekdayCommaDate = "EEE, d. MMM", weekdayDay = "EEE d.", weekdayDayMonth = "EEE, d. MMMM", shortMonth = "LLL",
                weekdayFullDate = "EEEE, d. MMMM", listedDay = "d.", monthFirstList = false, listSeparator = COMMA,
            ),
            FormatLanguage(
                "fr", "h", "min", "Mo", "Go", "moins de 1 Mo", LATIN, "d MMMM", "d MMM", "EEEE d MMMM", "LLLL yyyy", "d MMMM yyyy", "EEEE d MMMM yyyy", PluralRule.ONE_UP_TO_TWO,
                weekdayDate = "EEE d MMM", weekdayCommaDate = "EEE d MMM", weekdayDay = "EEE d", weekdayDayMonth = "EEE d MMMM", shortMonth = "LLL",
                weekdayFullDate = "EEEE d MMMM", listedDay = "d", monthFirstList = false, listSeparator = COMMA,
            ),
            FormatLanguage(
                "es", "h", "min", "MB", "GB", "menos de 1 MB", LATIN, "d 'de' MMMM", "d MMM", "EEEE, d 'de' MMMM", "LLLL 'de' yyyy", "d 'de' MMMM 'de' yyyy",
                "EEEE, d 'de' MMMM 'de' yyyy", PluralRule.ONE_OTHER,
                weekdayDate = "EEE d MMM", weekdayCommaDate = "EEE, d MMM", weekdayDay = "EEE d", weekdayDayMonth = "EEE d 'de' MMMM", shortMonth = "LLL",
                weekdayFullDate = "EEEE, d 'de' MMMM", listedDay = "d", monthFirstList = false, listSeparator = COMMA,
            ),
            FormatLanguage(
                "it", "h", "min", "MB", "GB", "meno di 1 MB", LATIN, "d MMMM", "d MMM", "EEEE d MMMM", "LLLL yyyy", "d MMMM yyyy", "EEEE d MMMM yyyy", PluralRule.ONE_OTHER,
                weekdayDate = "EEE d MMM", weekdayCommaDate = "EEE d MMM", weekdayDay = "EEE d", weekdayDayMonth = "EEE d MMMM", shortMonth = "LLL",
                weekdayFullDate = "EEEE d MMMM", listedDay = "d", monthFirstList = false, listSeparator = COMMA,
            ),
            FormatLanguage(
                "pt", "h", "min", "MB", "GB", "menos de 1 MB", LATIN, "d 'de' MMMM", "d MMM", "EEEE, d 'de' MMMM", "LLLL 'de' yyyy", "d 'de' MMMM 'de' yyyy",
                "EEEE, d 'de' MMMM 'de' yyyy", PluralRule.ONE_OTHER,
                weekdayDate = "EEE, d 'de' MMM", weekdayCommaDate = "EEE, d 'de' MMM", weekdayDay = "EEE, d", weekdayDayMonth = "EEE, d 'de' MMMM", shortMonth = "LLL",
                weekdayFullDate = "EEEE, d 'de' MMMM", listedDay = "d", monthFirstList = false, listSeparator = COMMA,
            ),
            FormatLanguage(
                "ko", "시간", "분", "MB", "GB", "1MB 미만", LATIN, "M월 d일", "M월 d일", "M월 d일 EEEE", "yyyy년 M월", "yyyy년 M월 d일", "yyyy년 M월 d일 EEEE", PluralRule.NONE,
                unitSpace = "", month = "M월",
                weekdayDate = "M월 d일 (EEE)", weekdayCommaDate = "M월 d일 (EEE)", weekdayDay = "d일 (EEE)", weekdayDayMonth = "M월 d일 (EEE)", shortMonth = "M월",
                weekdayFullDate = "M월 d일 EEEE", listedDay = "d일", monthFirstList = true, listSeparator = COMMA,
            ),
            FormatLanguage(
                "zh", "小时", "分钟", "MB", "GB", "不到 1 MB", LATIN, "M月d日", "M月d日", "M月d日 EEEE", "yyyy年M月", "yyyy年M月d日", "yyyy年M月d日 EEEE", PluralRule.NONE,
                unitSpace = " ", month = "M月",
                weekdayDate = "M月d日 EEE", weekdayCommaDate = "M月d日 EEE", weekdayDay = "d日 EEE", weekdayDayMonth = "M月d日 EEE", shortMonth = "M月",
                weekdayFullDate = "M月d日 EEEE", listedDay = "d日", monthFirstList = true, listSeparator = IDEOGRAPHIC_COMMA,
            ),
            FormatLanguage(
                "ja", "時間", "分", "MB", "GB", "1 MB未満", LATIN, "M月d日", "M月d日", "M月d日 EEEE", "yyyy年M月", "yyyy年M月d日", "yyyy年M月d日 EEEE", PluralRule.NONE,
                unitSpace = "", month = "M月",
                weekdayDate = "M月d日(EEE)", weekdayCommaDate = "M月d日(EEE)", weekdayDay = "d日(EEE)", weekdayDayMonth = "M月d日(EEE)", shortMonth = "M月",
                weekdayFullDate = "M月d日 EEEE", listedDay = "d日", monthFirstList = true, listSeparator = IDEOGRAPHIC_COMMA,
            ),
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
