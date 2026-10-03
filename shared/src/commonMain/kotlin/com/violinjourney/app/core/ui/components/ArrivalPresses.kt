package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * The presses of a screen, or of a face, that has just come in under the finger that opened it: for the time of a double tap of the
 * system from its first frame ([SettleAfterDoubleTap]) they are not heard, as the main button of a face of a sheet (R3) and of a moment of
 * the journey (R7) is not. A press is bound to what it was made on (the lesson of stage 120): the second tap of a double tap lands on what
 * the first one opened, where the button pressed stood.
 *
 * - The bottom zone of every face of a copy and of a restore (spec 5.29 R8, review of stage 122; until stage 99 — `zonePresses` of
 *   `BackupParts.kt`): a face that came in the place of another — the choice after «Ещё раз», «Остановить» after «Восстановить» into an
 *   empty app — has its button where the one pressed stood, and the second tap would ask for a second «Сохранить как…» or stop what has
 *   just begun; the face a screen opens on holds too (the lead's fix of stage 122) — «Сохранить в…» of the copy stands where «Сначала
 *   сохранить текущие данные» of the passport stood.
 * - The form of an event (spec 5.29 R9, review of stage 99): its «Сохранить» stands where «Добавить выступление» of «Выступления» and the
 *   main button of a sheet of a day stood — the second tap would store an event nobody filled in.
 * - «Выступления» (the same review): their first row stands where the row «Выступления» of «Записи» stood — the second tap would open the
 *   nearest performance over the list.
 *
 * Held for the life of the composition it is called in: a screen composed again — back from a screen above it — holds again, under the
 * finger that pressed «назад» there.
 */
@Composable
internal fun <T> arrivalPresses(onIntent: (T) -> Unit): (T) -> Unit {
    val arrival = remember { FaceArrival(inPlace = true) }
    SettleAfterDoubleTap(arrival)
    val latest = rememberUpdatedState(onIntent)
    return remember(arrival) { { intent -> if (!arrival.holds) latest.value(intent) } }
}
