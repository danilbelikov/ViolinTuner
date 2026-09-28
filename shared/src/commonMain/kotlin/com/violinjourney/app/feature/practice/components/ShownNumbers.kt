package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.feature.practice.PracticeSummary

/** A streak that grew by a day is simply shown; only a jump of days rolls. */
private const val STREAK_ROLL_FROM = 2L

/**
 * The numbers of «Сегодня», of the card «Неделя» and of the chip of the streak as the screen shows them (spec 3.36.2, «Движение»):
 * what is saved, held while a sheet lies over it, and the figures rolled to it.
 */
@Immutable
internal data class ShownNumbers(
    /** False — the first run: «Здесь будет ваша неделя». */
    val hasHistory: Boolean,
    /** Today's saved time: whether «Сегодня» says a time or «Ещё не играли». */
    val todayMs: Long,
    /** The same, as it rolls to it: the words of the number. */
    val rolledTodayMs: Long,
    /** The week's saved time: whether there is a line «Неделя» at all, and its words for TalkBack. */
    val weekMs: Long,
    val rolledWeekMs: Long,
    /** The days of the week, Monday first: the bars. */
    val weekDaysMs: List<Long>,
    /** The streak itself — whether the chip is there, its flame — and as it rolls to it, its words. */
    val streakDays: Int,
    val rolledStreakDays: Int,
    /** What makes numbers other numbers — another day, the first data: they change without rolling, and the flame does not flare. */
    val scope: Any,
)

/**
 * The numbers as they are shown, worked out here — at the level of the layout, not in the cards: «Сегодня» and the card of the
 * running practice take each other's place, and a card made anew would roll nothing (spec 3.16, 3.36.2).
 *
 * Numbers grow before the eyes: while a sheet lies over them — the recap, the gift after it, the time of a day being edited,
 * «Мой путь», or a practice being saved by the prompt whose recap is on its way — they keep what was seen, and roll and flare once
 * it has gone. «Сегодня», «Неделя» and the streak roll (600 and 400 ms, from 5 min and 2 days); the day changing at midnight is not
 * growth — a new day is a new scope, and its numbers are shown as they are. The first data after loading is not growth either, nor
 * a streak that comes from nothing: its chip appears ([streakRollScope]).
 */
@Composable
internal fun rememberShownNumbers(state: PracticeState): ShownNumbers {
    val sheetOpen = state.sheet != null || state.gift != null || state.recapPending
    val live = Held(state.hasHistory, state.summary, state.todayMs)
    // another model — the first data after loading — is other numbers: shown at once, a sheet open or not
    var seen by remember(state.loading) { mutableStateOf(live) }
    LaunchedEffect(sheetOpen, live) {
        if (!sheetOpen) seen = live
    }
    val held = if (sheetOpen) seen else live
    val scope: Any = state.today to state.loading
    val rollFrom = PracticeMotion.ROLL_FROM_MINUTES * MS_PER_MINUTE
    val today = rolledValue(held.todayMs, scope, PracticeMotion.ROLL_MS, rollFrom)
    val week = rolledValue(held.summary.weekMs, scope, PracticeMotion.ROLL_MS, rollFrom)
    val streak = rolledValue(held.summary.streakDays.toLong(), streakRollScope(scope, held.summary.streakDays), PracticeMotion.ROLL_STREAK_MS, rollFrom = STREAK_ROLL_FROM)
    return ShownNumbers(
        hasHistory = held.hasHistory,
        todayMs = held.todayMs,
        rolledTodayMs = today,
        weekMs = held.summary.weekMs,
        rolledWeekMs = week,
        weekDaysMs = held.summary.weekDaysMs,
        streakDays = if (held.hasHistory) held.summary.streakDays else 0,
        rolledStreakDays = streak.toInt(),
        scope = scope,
    )
}

/**
 * What makes the streak of the chip another number, not the same one grown: [scope], and whether there is a streak at all. A streak
 * that comes from nothing — a day saved after midnight, a forgotten yesterday added — appears at its size: the chip is not there
 * at zero (spec 3.36.2: «ноль не показывается»), and one rolled from zero would say «0 дней» beside its flame. One that ends goes
 * with its chip.
 */
internal fun streakRollScope(scope: Any, streakDays: Int): Any = scope to (streakDays > 0)

/** What is held under a sheet. */
private data class Held(val hasHistory: Boolean, val summary: PracticeSummary, val todayMs: Long)
