package com.violinjourney.app.core.analytics

/**
 * Every event the app can send, with its parameters — one list, the way `IntonationConfig` holds
 * the numbers of the domain (spec 3.34). A new event is a new line in the spec first and a class
 * here second: the list is part of the promise made on the fourth page of the onboarding.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())

/** Which screen was opened; the key is a route stripped of its arguments — see [screenKeyOf]. */
class ScreenOpen(screen: String) : AnalyticsEvent(NAME, mapOf(PARAM to screen)) {
    companion object {
        const val NAME = "screen_open"
        const val PARAM = "screen"
    }
}

/**
 * A navigation route reduced to the screen it names: `piece/{pieceId}` becomes `piece`. Ids and
 * everything else an argument could carry are cut off here, so that no value can leave the phone by
 * riding along inside a route (spec 3.34, rule 1).
 */
fun screenKeyOf(route: String?): String? = route
    ?.substringBefore('/')
    ?.substringBefore('?')
    ?.takeIf { it.isNotBlank() }
