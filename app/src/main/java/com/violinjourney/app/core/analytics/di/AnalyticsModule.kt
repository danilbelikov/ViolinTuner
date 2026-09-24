package com.violinjourney.app.core.analytics.di

import com.violinjourney.app.BuildConfig
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.AppMetricaAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    /**
     * Statistics are sent by a build that was given a key (spec 5.27). A debug build stays silent
     * unless it is built with `-PanalyticsDebug=true` — otherwise every check on the emulator would
     * mix into the numbers the real phones send. A build without a key has nowhere to send to.
     */
    @Provides
    @Singleton
    fun provideAnalytics(appMetrica: Provider<AppMetricaAnalytics>): Analytics {
        val sends = BuildConfig.APPMETRICA_KEY.isNotBlank() &&
            (!BuildConfig.DEBUG || BuildConfig.ANALYTICS_IN_DEBUG)
        return if (sends) appMetrica.get() else NoOpAnalytics()
    }
}
