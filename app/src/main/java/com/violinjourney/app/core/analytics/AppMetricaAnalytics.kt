package com.violinjourney.app.core.analytics

import android.app.Application
import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.di.DefaultDispatcher
import com.violinjourney.app.core.settings.SettingsRepository
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The one class that knows AppMetrica (spec 5.27). Everything above it speaks [Analytics], so
 * another service — or none at all — would cost this file and the module that binds it.
 */
@Singleton
class AppMetricaAnalytics @Inject constructor(
    private val settings: SettingsRepository,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : Analytics {
    /** The consent as read from the settings: nothing reaches the library before it is read, nor while it is off. */
    private val consent = ConsentGate()

    /**
     * Called once from Application.onCreate. Nothing is sent until the stored consent allows it,
     * so this is safe to call before the consent has been read from the disk.
     */
    fun start(application: Application) {
        // Activation has to be synchronous here, while the consent is read from a flow. So the
        // library starts muted and the flow unmutes it: no runBlocking, and no first session lost
        // to waiting for the disk.
        AppMetrica.activate(
            application,
            AppMetricaConfig.newConfigBuilder(BuildConfig.APPMETRICA_KEY)
                .withDataSendingEnabled(false)
                // No advertising id (spec 3.34), whatever an old Android or old Play Services make of
                // the AD_ID permission the manifest removes: the statistics stay unlinkable across apps.
                .withAdvIdentifiersTracking(false)
                // Only in the build that exists to be watched (`-PanalyticsDebug=true`): the
                // library then says what it took and what it sent, in `adb logcat -s AppMetrica`.
                .apply { if (BuildConfig.ANALYTICS_IN_DEBUG) withLogs() }
                .build(),
        )
        // Lives as long as the process does, on purpose: the consent has to be followed until the
        // app is gone. A scope of its own rather than GlobalScope, so the job has an owner.
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            settings.settings
                .map { it.analyticsEnabled }
                .distinctUntilChanged()
                .collect { enabled -> consent.follow(enabled, AppMetrica::setDataSendingEnabled) }
        }
    }

    override fun track(event: AnalyticsEvent) = consent.pass {
        if (event.params.isEmpty()) {
            AppMetrica.reportEvent(event.name)
        } else {
            AppMetrica.reportEvent(event.name, event.params)
        }
    }

    // the class and the frames of the exception go, its messages without the names of files (spec 3.34, rule 1)
    override fun error(group: ErrorGroup, message: String, cause: Throwable?) {
        val reported = cause?.reported()
        consent.pass {
            if (reported == null) {
                AppMetrica.reportError(group.key, message)
            } else {
                AppMetrica.reportError(group.key, message, reported)
            }
        }
    }
}

/**
 * An exception as the statistics may see it: the class and the frames of the original, with the messages — its own and
 * its causes' — passed through [ErrorText.scrub]. Crashes the library catches itself are not passed through here.
 */
internal class ReportedFailure(message: String, cause: Throwable?) : Exception(message, cause)

internal fun Throwable.reported(depth: Int = 0): Throwable =
    ReportedFailure(
        listOfNotNull(this::class.java.name, ErrorText.scrub(message)).joinToString(": "),
        cause?.takeIf { it !== this && depth < MAX_CAUSES }?.reported(depth + 1),
    ).also { it.stackTrace = stackTrace }

/** A chain of causes longer than this is cut: it is a loop or a pile, and the first ones tell the story. */
private const val MAX_CAUSES = 8
