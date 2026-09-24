package com.violinjourney.app.core.analytics


/**
 * Sends nothing. This is what a debug build, a build without a key and the tests get, and the app
 * works exactly as it did before the statistics existed (spec 5.27).
 */
class NoOpAnalytics : Analytics {
    override fun track(event: AnalyticsEvent) = Unit

    override fun error(group: ErrorGroup, message: String, cause: Throwable?) = Unit
}
