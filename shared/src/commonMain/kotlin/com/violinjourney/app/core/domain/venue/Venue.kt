package com.violinjourney.app.core.domain.venue

/**
 * Where the player is (spec 3.27): at home, or in a city the road has reached. Live takes place
 * there — in the room, or on the stage of the city's hall — and the window on «Занятия» shows it.
 * The number of the stop is `stopIndex`, beside the route in the app.
 */
sealed interface Venue {
    data object Home : Venue

    data class Hall(val stopId: String) : Venue
}
