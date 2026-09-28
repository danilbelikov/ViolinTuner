package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.feature.history.HistoryCard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** The figures of «Сегодня» and of the calendar (spec 3.36.2): the week, its days, the month shown and the streak. */
data class PracticeSummary(
    val weekMs: Long,
    /** The time of the month shown: under its name over the calendar, «17 ч 27 мин · 24 дня». */
    val monthMs: Long,
    val streakDays: Int,
    /** The seven days of this week, Monday first, saved time only (5.6): the bars of «Сегодня». */
    val weekDaysMs: List<Long>,
    /** The days of the month shown that have practice: «24 дня»; none — the calendar says only the month. */
    val monthDays: Int,
)

/** One day of the calendar grid; null cells pad the month to whole weeks. */
data class CalendarCell(
    val date: LocalDate,
    val totalMs: Long,
    /** 0 = no fill, 1–4 = tone of the fill (spec 5.6). */
    val fillLevel: Int,
    val isToday: Boolean,
    /** The day whose sheet is open — the sheet of the day or «Время за день» over it (spec 3.36.2); none the rest of the time. */
    val isSelected: Boolean,
    /** Not selectable until the events of R9: nothing to open yet. */
    val isFuture: Boolean,
)

/** The day of the sheet of the day (spec 3.36.2): its date, its time and its records. */
data class SelectedDay(
    val date: LocalDate,
    val isToday: Boolean,
    val totalMs: Long,
    /** Sessions recorded on that day, newest first. */
    val sessions: List<HistoryCard>,
)

/** One tile of the trophies of «Мой путь»; [index] — the place of the mark among the marks of the config, for its name. */
data class TrophyBadge(val hours: Int, val given: Boolean, val index: Int)

/** Who practises and how far they have come: the path row of «Занятия» and the sheet «Мой путь» (spec 3.36.2). */
data class ProfileHeader(
    /** Empty when the user gave none: «Мой путь» then says «За скрипкой». */
    val name: String,
    /** Absolute path of the photo; null without one or when the file is gone. */
    val avatarPath: String?,
    /** All the time ever practised: the main figure of the header. */
    val totalMs: Long,
    val level: Int,
    /** Null on the last level. */
    val nextLevel: Int?,
    /** 0..1, how far the bar is filled. */
    val levelFraction: Float,
    val toNextLevelMs: Long?,
    /**
     * The latest trophies (two at most) and then the next mark as an outline. A trophy counts
     * here once its gift sheet has been answered: until then its place is still the outline.
     */
    val trophyRow: List<TrophyBadge>,
    val givenTrophies: Int,
    /** Null when every trophy is taken. */
    val nextTrophyHours: Int?,
)

/**
 * A trophy given but not yet seen: the gift sheet (spec 3.13, handoff 11f). Not a
 * [PracticeSheet]: nobody opens it, it follows from the stored trophies.
 */
data class Gift(
    val hours: Int,
    /** Position of the mark among the marks of the config, for the name. */
    val index: Int,
    val awardedDate: LocalDate,
)

/** One line of the trophies sheet (handoff 11e). */
data class TrophyLine(
    val hours: Int,
    /** Position of the mark among the marks of the config: the trophy names follow the same order. */
    val index: Int,
    /** Null = not given yet. */
    val awardedDate: LocalDate?,
    /** Null for a given trophy and for one far ahead. */
    val remainingMs: Long?,
    val isNext: Boolean,
    /** Below the «Далеко впереди» divider: a horizon, not a debt. */
    val isFar: Boolean,
)

/**
 * One element played in the practice, as «Что играли» lists it (spec 3.28, 3.36.3): «7 из 10 мин», or the goal with the brass tick.
 * [dropped] — the stepper cut it off whole (begun no earlier than the new end, or left shorter than a minute): it stays in the list
 * as «— не вошёл», so the cut is seen at once rather than in the recap, and it is not saved (5.21).
 */
data class PlayedLine(val title: String, val minutes: Int, val goalMinutes: Int, val done: Boolean, val dropped: Boolean = false)

/** The block that runs, under «Занятие идёт» (handoff 30g4): «ещё 7 мин», or «готово» when [minutesLeft] is null. */
data class RunningBlockLine(val title: String, val minutesLeft: Int?)

/**
 * The clock of the running practice (spec 3.12, 3.28): apart from [PracticeState], for it changes every second
 * and the rest of the screen has nothing to do with it — the calendar, the header and the day's records are not
 * worked out again for a tick.
 */
data class PracticeTimer(
    /** How long the practice has been running, refreshed every second. */
    val elapsedMs: Long,
    /** The block that runs within the practice; null without one. */
    val block: RunningBlockLine?,
)

sealed interface PracticeSheet {
    /**
     * «Закончить занятие» (spec 3.12, 3.36.3): the timed length with a chance to trim it. [minutes] is what the stepper shows;
     * until it is touched the exact [actualMs] is what gets saved.
     */
    data class Summary(
        val startedAtEpochMs: Long,
        val actualMs: Long,
        val minutes: Int,
        val minMinutes: Int,
        val maxMinutes: Int,
        val edited: Boolean,
        /**
         * The fewest minutes the stepper trims to (5.6, [com.violinjourney.app.core.domain.practice.PracticeConfig.minEditableMinutes]):
         * a practice no longer than it cannot be trimmed, and its hint names the number — «Короче 5 минут не укоротить».
         */
        val floorMinutes: Int,
        /** The blocks of this practice and the names of their elements: «Что играли» follows the length being saved. */
        val blocks: PracticeBlocks? = null,
        val titles: Map<Long, String> = emptyMap(),
        /**
         * What «Что играли» lists for the length the sheet would save now — the blocks of the whole length, those the stepper cut off
         * whole as «не вошёл»; empty without blocks — the sheet as it was.
         */
        val played: List<PlayedLine> = emptyList(),
        /** The chosen length changed «Что играли» — a line shortened, its tick lost, or one cut off: the hint says so (spec 3.36.3). */
        val playedCut: Boolean = false,
    ) : PracticeSheet

    /**
     * «Мой путь» (spec 3.36.2): the ring, the bar of the level, the trophies and the ways to «Все трофеи», «Имя и фото»
     * and «Настройки»; it shows [PracticeState.header].
     */
    data object Path : PracticeSheet

    /**
     * «Профиль»: [nameDraft] is what the field shows, stored when the sheet closes. Photo
     * actions take effect at once; [importingPhoto] is true while a picked photo is copied.
     * Opens only over «Мой путь», in its place, and goes back to it when closed.
     */
    data class Profile(val nameDraft: String, val importingPhoto: Boolean) : PracticeSheet

    /** «Трофеи»: the list itself is [PracticeState.trophies]. Over «Мой путь», as [Profile]. */
    data object Trophies : PracticeSheet

    /** «Занятие сохранено» (spec 3.31): what the practice just saved earned and changed. */
    data class Recap(val recap: PracticeRecap) : PracticeSheet

    /**
     * The sheet of a day tapped in the calendar (spec 3.36.2): its time with «Изменить» / «Добавить» and its records; the day is
     * [PracticeState.selected]. Only a day up to today opens one.
     */
    data class Day(val date: LocalDate) : PracticeSheet

    /**
     * «Время за день» of a day: the whole day's time, zero removes the day. Opens only over [Day], in its place, and gives it back —
     * «Сохранить» with the new time, «Отмена» and a swipe with the old one.
     */
    data class EditTime(
        val date: LocalDate,
        val minutes: Int,
        val maxMinutes: Int,
        /** The minutes the sheet opened with: «Сохранить» with the same number rewrites nothing (spec 5.6). */
        val initialMinutes: Int = minutes,
        /**
         * The real sum of the day before the edit — over twelve hours too, where the sheet opens at twelve (spec 3.36.3): «было 13 ч 10
         * мин» says it once the number moves. Zero — an empty day: «занятие без телефона», «Добавить N мин».
         */
        val dayTotalMs: Long = initialMinutes * MS_PER_MINUTE,
    ) : PracticeSheet
}

/**
 * «17:55 — 18:42» under the number of «Закончить занятие» (spec 3.36.3): the start and the moment the length counts to — now, or for a
 * practice past the limit its last sound — and [wasMs], the length before the stepper moved, once it has moved («· было 47 мин»).
 */
data class SummarySpan(val startEpochMs: Long, val endEpochMs: Long, val wasMs: Long?)

/** The hint under the stepper of «Закончить занятие» (spec 3.36.3). */
enum class SummaryHint {
    /** «Забыли остановить? Уберите лишнее — больше, чем шло, не добавить.» */
    Forgot,

    /** «Подходы, что не влезли, укоротились вместе с занятием.»: the stepper cut something in «Что играли». */
    Cut,

    /** «Короче 5 минут не укоротить — сохранится как было.»: both buttons of the stepper are dimmed. */
    TooShort,
}

/** What stands under the number of «Время за день» (spec 3.36.3). */
sealed interface DayCaption {
    /** The number has not moved from the time of the day. */
    data object None : DayCaption

    /** An empty day: «занятие без телефона». */
    data object NoPhone : DayCaption

    /** The number moved: «было 1 ч 15 мин» — the real sum of the day, not the number it opened at. */
    data class Was(val ms: Long) : DayCaption
}

data class PracticeState(
    /** True until both the entries and the running practice have been read once. */
    val loading: Boolean,
    /** False = the first run: no practice was ever saved (spec 3.36.2, «Здесь будет ваша неделя»). */
    val hasHistory: Boolean,
    /**
     * The day the running practice began on (`practiceDateOf` its start); null while none runs. How long it has been running
     * is [PracticeTimer], a flow of its own. Only a practice begun today hatches today's bar and adds to «Сегодня вместе с ним».
     */
    val runningSince: LocalDate?,
    /** The day it is: anew at midnight. */
    val today: LocalDate,
    val todayMs: Long,
    val summary: PracticeSummary,
    val month: YearMonth,
    /** The calendar never goes past the current month. */
    val canGoForward: Boolean,
    /** Monday-first grid of whole weeks. */
    val cells: List<CalendarCell?>,
    /**
     * The day whose sheet is open — the sheet of the day or «Время за день» over it (spec 3.36.2); null the rest of the time: by
     * default no day is selected.
     */
    val selected: SelectedDay?,
    val header: ProfileHeader,
    val trophies: List<TrophyLine>,
    /**
     * The lowest trophy not seen yet; null while another sheet is open, or while a practice is being saved and its
     * recap has not opened yet — it waits its turn (spec 3.31).
     */
    val gift: Gift?,
    val sheet: PracticeSheet?,
    /**
     * A record opened from the sheet of the day is on the screen: the sheets of «Занятия» step aside — the model keeps [sheet] — and
     * the sheet of the day rises again when the screen is back (spec 3.36.2: «назад» from the record — to the same sheet).
     */
    val sheetsAway: Boolean = false,
    /** Whole minutes of one stepper step, from the config: the sheets word their hint with it. */
    val stepMinutes: Int,
    /**
     * The bottom of the scale of the week bars, from the config — the highest step of the calendar (5.6, 5.29): an even
     * week of short days does not look like a record one.
     */
    val weekFloorMinutes: Int,
    /**
     * A practice is being saved — here or by the prompt over the screen — and its recap is not open yet: what grows
     * with it grows only once the recap and the gift after it have gone (spec 3.16, 3.18, 3.31).
     */
    val recapPending: Boolean = false,
) {
    /** A practice runs. */
    val running: Boolean get() = runningSince != null

    /**
     * A practice runs and began today (spec 3.36.2): it hatches today's bar with its minutes. One left running past midnight
     * belongs to yesterday — it hatches nothing of the new day.
     */
    val runningToday: Boolean get() = runningSince != null && runningSince == today

    /** «Сегодня вместе с ним» under the timer (spec 3.36.2): the practice began today and today already has saved time. */
    val withRunningToday: Boolean get() = runningToday && todayMs > 0
}

sealed interface PracticeIntent {
    /** The window into the journey (spec 3.23). */
    data object JourneyClicked : PracticeIntent

    /** The narrow card «дом» under the window into the journey (spec 3.24). */
    data object HomeClicked : PracticeIntent

    data object StartClicked : PracticeIntent

    data object StopClicked : PracticeIntent

    /** «Вернуться к Live» of the card «Занятие идёт» (spec 3.36.2): the tab Live, the practice runs on. */
    data object BackToLiveClicked : PracticeIntent

    /** The path row: «Мой путь» opens (spec 3.36.2). */
    data object PathClicked : PracticeIntent

    /** «Мой путь» swiped down, tapped beside or closed with «назад»: only hidden. */
    data object PathHidden : PracticeIntent

    /** «Настройки» of «Мой путь»: the sheet closes, the settings open; back from them — «Занятия» without a sheet. */
    data object PathSettingsClicked : PracticeIntent

    /** Stepper of «Закончить занятие»: +1 or −1 step. */
    data class SummaryStepped(val steps: Int) : PracticeIntent

    data object SummarySaved : PracticeIntent

    data object SummaryDiscarded : PracticeIntent

    /** A swipe down, a tap beside it or «назад»: the sheet goes, the practice runs on — never a «Не сохранять». */
    data object SummaryHidden : PracticeIntent

    /** A day of the calendar: its sheet opens (spec 3.36.2) — a day up to today, over no other sheet. */
    data class DaySelected(val date: LocalDate) : PracticeIntent

    /** The sheet of the day swiped down, tapped beside or closed with «назад»: only hidden. */
    data object DayHidden : PracticeIntent

    /** The screen is on top again — back from a record, from the settings: the sheet of the day that stepped aside for a record rises. */
    data object Resumed : PracticeIntent

    data object MonthBack : PracticeIntent

    data object MonthForward : PracticeIntent

    /** «Изменить» / «Добавить» of the sheet of the day: «Время за день» takes its place. Heard only over the sheet of the day. */
    data object EditTimeClicked : PracticeIntent

    /** Stepper of «Время за день»: +1 or −1 step. */
    data class EditTimeStepped(val steps: Int) : PracticeIntent

    /** Chips of «Время за день»: add minutes, or zero to clear. */
    data class EditTimeAdded(val minutes: Int) : PracticeIntent

    data object EditTimeCleared : PracticeIntent

    data object EditTimeSaved : PracticeIntent

    /** «Отмена», a swipe down and «назад» alike: nothing is written, the sheet of the day comes back — in place, for «назад». */
    data object EditTimeCancelled : PracticeIntent

    /** A record of the sheet of the day: its screen opens, and «назад» from it comes back to the sheet. */
    data class SessionClicked(val id: Long) : PracticeIntent

    /** «Имя и фото» of «Мой путь»: «Профиль» takes its place. Heard only over «Мой путь». */
    data object ProfileClicked : PracticeIntent

    data class ProfileNameChanged(val text: String) : PracticeIntent

    /** The system picker returned a photo; [uri] is its content uri as a string. */
    data class ProfilePhotoPicked(val uri: String) : PracticeIntent

    data object ProfilePhotoRemoved : PracticeIntent

    /** «Готово», a swipe down and «назад» alike: the name is stored either way (spec 3.13), and «Мой путь» comes back. */
    data object ProfileClosed : PracticeIntent

    /**
     * «Настройки» under «Готово» of the profile (until R3): the name is stored, the profile and «Мой путь» under it close, the
     * settings open — as from «Мой путь».
     */
    data object ProfileSettingsClicked : PracticeIntent

    /** «Все трофеи» or the row of trophies of «Мой путь»: «Трофеи» take its place. Heard only over «Мой путь». */
    data object TrophiesClicked : PracticeIntent

    /** «Трофеи» swiped down or closed with «назад»: «Мой путь» comes back. */
    data object TrophiesClosed : PracticeIntent

    /** «Спасибо» and a swipe down alike: the trophy of [hours] has been seen. */
    data class GiftAccepted(val hours: Int) : PracticeIntent

    /** «Готово», a swipe down and «назад» alike close «Занятие сохранено». */
    data object RecapClosed : PracticeIntent

    /** «В дорогу» of «Занятие сохранено»: home, where the road is taken (spec 3.25). */
    data object RecapTravelClicked : PracticeIntent
}

sealed interface PracticeEffect {
    /** "Начать занятие" and «Вернуться к Live» lead to Live: practising means playing. */
    data object OpenLive : PracticeEffect

    data class OpenSession(val id: Long) : PracticeEffect

    data object OpenJourney : PracticeEffect

    data object OpenHome : PracticeEffect

    data object OpenSettings : PracticeEffect

    data object ShowTooShort : PracticeEffect

    /** The picked file could not be read as a picture. */
    data object ShowPhotoFailed : PracticeEffect
}
