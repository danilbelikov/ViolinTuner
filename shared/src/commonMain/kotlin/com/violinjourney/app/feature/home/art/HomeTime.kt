package com.violinjourney.app.feature.home.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.journey.art.SceneMode
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** What the home is drawn at (spec 3.24, 3.27): the time of day by the phone's clock — the lamp and the fire are for the evening (handoff 27a) — and the date, for the tree. */
@Immutable
data class HomeTime(val mode: SceneMode, val date: LocalDate)

/** When the home looks different: day from 07:00 to 19:00, and a new date at midnight. Pure. */
object HomeTimeRules {
    const val DAY_FROM = 7
    const val DAY_TO = 19

    fun at(local: LocalDateTime): HomeTime = HomeTime(if (local.hour in DAY_FROM until DAY_TO) SceneMode.DAY else SceneMode.EVENING, local.date)

    /** Reads the zone of [clock] once. */
    fun at(clock: WallClock): HomeTime = at(clock.instant().toLocalDateTime(clock.zone))

    /** The first moment after [now] at which the home looks different in [zone]: 07:00, 19:00 or the next midnight, a day of a clock change included. */
    fun nextChange(now: Instant, zone: TimeZone): Instant {
        val today = now.toLocalDateTime(zone).date
        return listOf(
            LocalDateTime(today, LocalTime(DAY_FROM, 0)),
            LocalDateTime(today, LocalTime(DAY_TO, 0)),
            LocalDateTime(today.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)),
        ).map { it.toInstant(zone) }.first { it > now }
    }
}

/**
 * The time the home is drawn at, as state: read when the picture comes on the screen, again each time the app comes
 * back to the front, and exactly at 07:00, 19:00 and midnight — so the room turns to evening by itself, in silence too,
 * and the tree comes on the 1st of December without a tap. The zone is read then and only then, never on a frame: on
 * iOS a read of it is a read of the zone file. Coming back to the front reads anew because a wait on Android does not
 * count the phone's deep sleep, and a zone changed in the settings meanwhile is followed.
 */
@Composable
fun rememberHomeTime(clock: WallClock = SystemWallClock): HomeTime {
    val time = remember(clock) { mutableStateOf(HomeTimeRules.at(clock)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = clock.instant()
                val zone = clock.zone
                time.value = HomeTimeRules.at(now.toLocalDateTime(zone))
                delay((HomeTimeRules.nextChange(now, zone) - now).inWholeMilliseconds.coerceAtLeast(1))
            }
        }
    }
    return time.value
}
