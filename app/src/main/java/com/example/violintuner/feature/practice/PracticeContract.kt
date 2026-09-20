package com.example.violintuner.feature.practice

import com.example.violintuner.feature.history.HistoryCard
import java.time.LocalDate
import java.time.YearMonth

/** The three figures above the calendar; null when there has never been a practice (dashes). */
data class PracticeSummary(
    val weekMs: Long,
    val monthMs: Long,
    val streakDays: Int,
)

/** One day of the calendar grid; null cells pad the month to whole weeks. */
data class CalendarCell(
    val date: LocalDate,
    val totalMs: Long,
    /** 0 = no fill, 1–4 = tone of the fill (spec 5.6). */
    val fillLevel: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isFuture: Boolean,
)

data class SelectedDay(
    val date: LocalDate,
    val isToday: Boolean,
    val totalMs: Long,
    /** Sessions recorded on that day, newest first. */
    val sessions: List<HistoryCard>,
)

/** One icon of the trophy row in the header. */
data class TrophyBadge(val hours: Int, val given: Boolean)

/** The profile header that stands where the title used to (spec 3.13, handoff 11a). */
data class ProfileHeader(
    /** Empty when the user gave none: the header then says «За скрипкой». */
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
     * here once its gift sheet has been answered: until then its place is still the outline,
     * and it turns into the trophy in front of the user's eyes.
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

sealed interface PracticeSheet {
    /**
     * "Закончить занятие": the timed length with a chance to trim it. [minutes] is what the
     * stepper shows; until it is touched the exact [actualMs] is what gets saved.
     */
    data class Summary(
        val startedAtEpochMs: Long,
        val actualMs: Long,
        val minutes: Int,
        val minMinutes: Int,
        val maxMinutes: Int,
        val edited: Boolean,
    ) : PracticeSheet

    /**
     * «Профиль»: [nameDraft] is what the field shows, stored when the sheet closes. Photo
     * actions take effect at once; [importingPhoto] is true while a picked photo is copied.
     */
    data class Profile(val nameDraft: String, val importingPhoto: Boolean) : PracticeSheet

    /** «Трофеи»: the list itself is [PracticeState.trophies]. */
    data object Trophies : PracticeSheet

    /** "Изменить время" of a day: the whole day's time, zero removes the day. */
    data class EditTime(
        val date: LocalDate,
        val minutes: Int,
        val maxMinutes: Int,
    ) : PracticeSheet
}

data class PracticeState(
    /** True until both the entries and the running practice have been read once. */
    val loading: Boolean,
    /** False = the empty state: no practice was ever saved (spec 3.12). */
    val hasHistory: Boolean,
    /** Null when no practice runs; otherwise how long it has been running, refreshed every second. */
    val runningMs: Long?,
    val todayMs: Long,
    val summary: PracticeSummary,
    val month: YearMonth,
    /** The calendar never goes past the current month. */
    val canGoForward: Boolean,
    /** Monday-first grid of whole weeks. */
    val cells: List<CalendarCell?>,
    val selected: SelectedDay,
    val header: ProfileHeader,
    val trophies: List<TrophyLine>,
    /** The lowest trophy not seen yet; null while another sheet is open — it waits its turn. */
    val gift: Gift?,
    val sheet: PracticeSheet?,
    /** Whole minutes of one stepper step, from the config: the sheets word their hint with it. */
    val stepMinutes: Int,
)

sealed interface PracticeIntent {
    /** The window into the journey (spec 3.23). */
    data object JourneyClicked : PracticeIntent

    data object StartClicked : PracticeIntent

    data object StopClicked : PracticeIntent

    /** Stepper of the summary sheet: +1 or −1 step. */
    data class SummaryStepped(val steps: Int) : PracticeIntent

    data object SummarySaved : PracticeIntent

    data object SummaryDiscarded : PracticeIntent

    data class DaySelected(val date: LocalDate) : PracticeIntent

    data object MonthBack : PracticeIntent

    data object MonthForward : PracticeIntent

    data object EditTimeClicked : PracticeIntent

    /** Stepper of the edit sheet: +1 or −1 step. */
    data class EditTimeStepped(val steps: Int) : PracticeIntent

    /** Chips of the edit sheet: add minutes, or zero to clear. */
    data class EditTimeAdded(val minutes: Int) : PracticeIntent

    data object EditTimeCleared : PracticeIntent

    data object EditTimeSaved : PracticeIntent

    data object EditTimeCancelled : PracticeIntent

    data class SessionClicked(val id: Long) : PracticeIntent

    /** Avatar or name tapped. */
    data object ProfileClicked : PracticeIntent

    data class ProfileNameChanged(val text: String) : PracticeIntent

    /** The system picker returned a photo; [uri] is its content uri as a string. */
    data class ProfilePhotoPicked(val uri: String) : PracticeIntent

    data object ProfilePhotoRemoved : PracticeIntent

    /** «Готово» and a swipe down alike: the name is stored either way (spec 3.13). */
    data object ProfileClosed : PracticeIntent

    /** The trophy row tapped. */
    data object TrophiesClicked : PracticeIntent

    data object TrophiesClosed : PracticeIntent

    /** «Спасибо» and a swipe down alike: the trophy of [hours] has been seen. */
    data class GiftAccepted(val hours: Int) : PracticeIntent
}

sealed interface PracticeEffect {
    /** "Начать занятие" leads to Live: practising means playing. */
    data object OpenLive : PracticeEffect

    data class OpenSession(val id: Long) : PracticeEffect

    data object OpenJourney : PracticeEffect

    data object ShowTooShort : PracticeEffect

    /** The picked file could not be read as a picture. */
    data object ShowPhotoFailed : PracticeEffect
}
