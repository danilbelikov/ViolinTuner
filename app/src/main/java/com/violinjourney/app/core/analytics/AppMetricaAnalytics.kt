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
                .collect { enabled -> AppMetrica.setDataSendingEnabled(enabled) }
        }
    }

    override fun track(event: AnalyticsEvent) {
        if (event.params.isEmpty()) {
            AppMetrica.reportEvent(event.name)
        } else {
            AppMetrica.reportEvent(event.name, event.params)
        }
    }

    override fun error(group: ErrorGroup, message: String, cause: Throwable?) {
        if (cause == null) {
            AppMetrica.reportError(group.key, message)
        } else {
            AppMetrica.reportError(group.key, message, cause)
        }
    }
}
