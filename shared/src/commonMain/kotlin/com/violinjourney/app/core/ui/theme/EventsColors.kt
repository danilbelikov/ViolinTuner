package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** One colour of a kind of events (spec 5.29 R9): of its mark on the ground, and of its sign and caption on its own plate. */
@Immutable
data class KindColors(val color: Color, val onPlate: Color)

/**
 * The colours of the kinds of events (spec 3.36.9, 5.29 R9; events-kinds.html, 1): the set of eight a kind keeps by its number
 * (`event_kinds.color`, 0–7) — Синий, Морская волна, Роза, Орхидея, Лёд, Бирюза, Пудра, Лайм. A plate is the colour at 18 % over
 * what it lies on — the ground of the screen or a card; never a raw colour on a screen.
 */
@Immutable
data class EventsColors(val kinds: List<KindColors>) {
    /** The colours of the number [index]; one out of the set (a row of a newer build) is the first. */
    fun of(index: Int): KindColors = kinds.getOrElse(index) { kinds.first() }

    /** The plate of the sign of [index] laid on [ground]: the mark of the kind at 18 %, opaque — so the sign over it keeps its contrast. */
    fun plate(index: Int, ground: Color): Color = of(index).color.copy(alpha = PLATE_ALPHA).compositeOver(ground)

    /** The tone of the chosen tile of a kind in the form (5.29 R9): the colour at 12 % over [ground]. */
    fun tile(index: Int, ground: Color): Color = of(index).color.copy(alpha = TILE_ALPHA).compositeOver(ground)

    private companion object {
        const val PLATE_ALPHA = 0.18f
        const val TILE_ALPHA = 0.12f
    }
}

internal val DarkEventsColors = EventsColors(
    listOf(
        KindColors(KindBlue, KindBlueOnPlate),
        KindColors(KindSea, KindSeaOnPlate),
        KindColors(KindRose, KindRoseOnPlate),
        KindColors(KindOrchid, KindOrchidOnPlate),
        KindColors(KindIce, KindIce),
        KindColors(KindTurquoise, KindTurquoise),
        KindColors(KindPowder, KindPowder),
        KindColors(KindLime, KindLime),
    ),
)

internal val LocalEventsColors = staticCompositionLocalOf<EventsColors> {
    error("EventsColors not provided: wrap content in ViolinTheme")
}
