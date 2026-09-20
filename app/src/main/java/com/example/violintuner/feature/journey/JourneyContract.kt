package com.example.violintuner.feature.journey

import com.example.violintuner.core.domain.journey.JourneyExtra
import com.example.violintuner.core.domain.journey.JourneyStop
import com.example.violintuner.core.domain.journey.Transport

/** A stop the player has been to, as the strip of postcards and the passport show it. */
data class VisitedStop(val stop: JourneyStop, val index: Int, val arrivedAtEpochMs: Long, val souvenir: Boolean)

/** What the screen is busy with: the road is the one piece of cinema in the app (handoff 26f). */
sealed interface JourneyPhase {
    data object Idle : JourneyPhase

    /** The intro: shown until the case is packed. */
    data object Intro : JourneyPhase

    data class Road(val from: JourneyStop, val to: JourneyStop, val fromIndex: Int, val durationMs: Int) : JourneyPhase

    data class Arrival(val stop: JourneyStop, val index: Int) : JourneyPhase

    data class Stamp(val stop: JourneyStop, val index: Int) : JourneyPhase
}

data class JourneyState(
    /** True until the stored journey has been read once. */
    val loading: Boolean,
    val phase: JourneyPhase,
    val current: JourneyStop,
    val currentIndex: Int,
    val arrivedAtEpochMs: Long?,
    /** Null beyond the last drawn stop: the takts wait for the next batch of cities. */
    val next: JourneyStop?,
    val balance: Long,
    val missing: Long,
    val canDepart: Boolean,
    /** Oldest first, the current stop last. */
    val visited: List<VisitedStop>,
    /** Stops after home: «остановка 4 из 16». */
    val totalStops: Int,
)

sealed interface JourneyIntent {
    data object BackClicked : JourneyIntent

    data object IntroConfirmed : JourneyIntent

    data object DepartClicked : JourneyIntent

    data object StampClicked : JourneyIntent

    data object StampDone : JourneyIntent

    data object MapClicked : JourneyIntent

    data object PassportClicked : JourneyIntent

    data class StopClicked(val stopId: String) : JourneyIntent

    /** «Убрать анимации» is known to the route, not to the view model: the road is then a card for a moment. */
    data class ReduceMotionChanged(val reduce: Boolean) : JourneyIntent
}

sealed interface JourneyEffect {
    data object Close : JourneyEffect

    data object OpenMap : JourneyEffect

    data object OpenPassport : JourneyEffect

    data class OpenStop(val stopId: String) : JourneyEffect
}

/** The window into the journey on «Занятия» (handoff 26h): where the player is and how far the next city. */
data class JourneyWindow(
    val current: JourneyStop,
    val next: JourneyStop?,
    val balance: Long,
    val missing: Long,
    val canDepart: Boolean,
    /** Takts of the practice saved a moment ago: a pill on the card for a few seconds. Null otherwise. */
    val justEarned: Int? = null,
)

/** One extra of a stop as its screen offers it. */
data class ExtraOffer(val extra: JourneyExtra, val price: Int, val bought: Boolean, val affordable: Boolean)

data class StopState(
    val loading: Boolean,
    val stop: JourneyStop,
    val index: Int,
    val totalStops: Int,
    val arrivedAtEpochMs: Long?,
    val balance: Long,
    val offers: List<ExtraOffer>,
    /** What the postcard shows now; day and the second view are there once bought. */
    val day: Boolean,
    val inside: Boolean,
    val dayUnlocked: Boolean,
    val secondViewUnlocked: Boolean,
)

sealed interface StopIntent {
    data object BackClicked : StopIntent

    data class DaySelected(val day: Boolean) : StopIntent

    data class InsideSelected(val inside: Boolean) : StopIntent

    data class BuyClicked(val extra: JourneyExtra) : StopIntent
}

/** How long the road takes on screen (handoff `anims`). */
object JourneyMotion {
    const val ROAD_TRAIN_MS = 2_000
    const val ROAD_FAR_MS = 2_500
    const val ROAD_REDUCED_MS = 800
    const val ARRIVAL_FADE_MS = 600
    const val ARRIVAL_TEXT_DELAY_MS = 300
    const val STAMP_MS = 350
    const val EARNED_PILL_MS = 3_000L
    const val PHASE_FADE_MS = 300

    fun roadMs(transport: Transport, reduce: Boolean): Int = when {
        reduce -> ROAD_REDUCED_MS
        transport == Transport.TRAIN -> ROAD_TRAIN_MS
        else -> ROAD_FAR_MS
    }
}
