package com.violinjourney.app.core.analytics

import android.app.Application

/** Keeps what would have been sent, so that a test can say what left and what did not. */
class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val errors = mutableListOf<Pair<ErrorGroup, String>>()

    /** Names and parameters as the service would see them: `screen_open {screen=live}`. */
    fun sent(): List<String> = events.map { event ->
        if (event.params.isEmpty()) event.name else "${event.name} ${event.params}"
    }

    override fun start(application: Application) = Unit

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override fun error(group: ErrorGroup, message: String, cause: Throwable?) {
        errors += group to message
    }
}
