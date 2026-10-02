package com.violinjourney.app.feature.events

/**
 * The one movement of the events of their own (spec 3.36.9, 5.29 R9): the reminder on «Занятия» comes and goes while the screen is
 * open. The rest is not theirs and is not repeated here: the month slides as it did (220 ms, R2), the reminder changes with «Сегодня»
 * ↔ the card of the running practice in its fade (200 ms, R2), sheets rise as every sheet does; «убрать анимации» — all at once.
 */
object EventsMotion {
    /** The reminder fades in when it comes and out when it goes while «Занятия» are open. */
    const val REMINDER_FADE_MS = 300
}
