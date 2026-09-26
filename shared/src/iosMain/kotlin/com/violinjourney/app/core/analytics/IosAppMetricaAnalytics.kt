package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * AppMetrica as the iOS app reaches it: the library is a Swift package of the Xcode project, and the Swift side hands
 * this bridge to `MainViewController`. Kotlin knows nothing of the library itself — only these five calls.
 */
interface AnalyticsService {
    /** Activates the library with sending off; [logs] makes it say what it took and sent (a build meant to be watched). */
    fun activate(apiKey: String, logs: Boolean)

    fun setDataSendingEnabled(enabled: Boolean)

    /** [params] hold numbers, booleans and short keys only (spec 3.34). */
    fun reportEvent(name: String, params: Map<String, Any>)

    fun reportError(group: String, message: String, cause: String?)

    /**
     * A Kotlin exception that ended the last run ([KotlinCrashes]), told at this start as a crash of its own — its class,
     * message and frames — beside the system's report of the same fall, which has none of them (spec 3.34).
     */
    fun reportUnhandledException(type: String, message: String?, frames: List<KotlinCrashFrame>, environment: Map<String, String>)
}

/**
 * [Analytics] of iOS, as `AppMetricaAnalytics` is on Android (spec 5.27): the library is activated once, muted, and the
 * consent stored in the settings unmutes it — no first session lost to waiting for the disk, nothing sent before consent.
 */
class IosAppMetricaAnalytics(private val service: AnalyticsService) : Analytics {
    /** Follows the consent of [settings] for as long as [scope] lives — the scope of the app's data. */
    fun followConsent(settings: SettingsRepository, scope: CoroutineScope) {
        scope.launch(Dispatchers.Main) {
            settings.settings.map { it.analyticsEnabled }.distinctUntilChanged().collect(service::setDataSendingEnabled)
        }
    }

    override fun track(event: AnalyticsEvent) = service.reportEvent(event.name, event.params)

    override fun error(group: ErrorGroup, message: String, cause: Throwable?) =
        service.reportError(group.key, message, cause?.let { "${it::class.simpleName}: ${it.message}" })

    /** A Kotlin exception that ended the last run, told at this start, under the same switch as everything else (spec 3.34). */
    internal fun crashed(crash: KotlinCrash) = service.reportUnhandledException(
        crash.type,
        crash.message,
        crash.frames,
        buildMap {
            put(KOTLIN, KotlinVersion.CURRENT.toString())
            crash.causes?.let { put(CAUSED_BY, it) }
        },
    )

    companion object {
        /** Keys of the environment of a crash: the version of Kotlin, and the chain of causes. */
        private const val KOTLIN = "kotlin"
        private const val CAUSED_BY = "caused by"

        /** Once per process: the library refuses a second activation. */
        fun activate(service: AnalyticsService, apiKey: String, logs: Boolean): IosAppMetricaAnalytics {
            service.activate(apiKey, logs)
            return IosAppMetricaAnalytics(service)
        }
    }
}
